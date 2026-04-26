package com.rallytrax.app.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rallytrax.app.data.gpx.GpxExporter
import com.rallytrax.app.data.gpx.TrackImporter
import com.rallytrax.app.data.local.RallyTraxDatabase
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val vehicleDao: VehicleDao,
    private val maintenanceDao: MaintenanceDao,
    private val fuelLogDao: FuelLogDao,
    private val firestoreSyncHelper: FirestoreSyncHelper,
    private val database: RallyTraxDatabase,
) {
    /**
     * App-lifetime scope. SyncManager is a Hilt @Singleton, so the scope's
     * job intentionally lives for the process lifetime — there's no shorter
     * lifecycle to bind to and we want background sync to outlive any single
     * Activity. Use IO dispatcher because the work is Firestore + Storage I/O,
     * not main-thread UI updates. SupervisorJob keeps a single failure from
     * cancelling other launched work (status init, debounce, etc.).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        // Initialize sync status from stored last sync time
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            _syncStatus.value = _syncStatus.value.copy(lastSyncTime = prefs.lastSyncTime)
        }
    }

    /**
     * Cancel the long-lived scope. Intended for tests (and a future
     * ProcessLifecycle teardown hook) — production callers should not invoke
     * this; the singleton is meant to live for the process.
     */
    @androidx.annotation.VisibleForTesting
    fun cancel() {
        debounceJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    fun setError(message: String) {
        _syncStatus.value = _syncStatus.value.copy(error = message, isSyncing = false)
    }

    fun clearError() {
        _syncStatus.value = _syncStatus.value.copy(error = null)
    }

    /**
     * Perform a full sync cycle using Firestore and Cloud Storage.
     */
    suspend fun performSync() {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)
        try {
            // 1. Sync settings (single Firestore read for both settings + md5)
            val localSettings = preferencesRepository.toSyncableSettings()
            val localJson = localSettings.toJson()
            val localMd5 = FirestoreSyncHelper.md5Hash(localJson)

            val (remoteSettings, remoteMd5) = firestoreSyncHelper.getSettingsDoc()
            if (remoteSettings != null) {
                if (remoteMd5 != localMd5) {
                    val merged = localSettings.mergeWith(remoteSettings)
                    preferencesRepository.applySyncableSettings(merged)
                    firestoreSyncHelper.setSettings(merged)
                }
            } else {
                firestoreSyncHelper.setSettings(localSettings)
            }

            // 2. Backup garage data
            backupGarageData()

            // 3. Update sync time
            val now = System.currentTimeMillis()
            preferencesRepository.setLastSyncTime(now)
            _syncStatus.value = SyncStatus(
                lastSyncTime = now,
                isSyncing = false,
                pendingChanges = false,
                error = null,
            )

            // 4. GPX track backup
            val prefs = preferencesRepository.preferences.first()
            if (prefs.backupTracksEnabled) {
                backupTracks()
            }

            Log.d(TAG, "Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                error = e.message ?: "Sync failed",
            )
        }
    }

    /**
     * Back up all local GPX tracks to Cloud Storage that are not yet backed up.
     */
    private suspend fun backupTracks() {
        val allTracks = trackDao.getAllTracksOnce()
        val backedUpIds = firestoreSyncHelper.listBackedUpTrackIds().toSet()
        val tracksToBackup = allTracks.filter { it.id !in backedUpIds }

        if (tracksToBackup.isEmpty()) {
            Log.d(TAG, "All tracks already backed up (${allTracks.size} total)")
            return
        }

        Log.d(TAG, "Backing up ${tracksToBackup.size} tracks to Cloud Storage")

        for (track in tracksToBackup) {
            try {
                val points = trackPointDao.getPointsForTrackOnce(track.id)
                val paceNotes = paceNoteDao.getNotesForTrackOnce(track.id)
                val gpxBytes = ByteArrayOutputStream().also { stream ->
                    GpxExporter.export(track, points, stream, paceNotes)
                }.toByteArray()

                firestoreSyncHelper.uploadGpxFile(track.id, gpxBytes)
                Log.d(TAG, "Backed up track ${track.id} (${gpxBytes.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to back up track ${track.id}", e)
            }
        }

        Log.d(TAG, "GPX backup complete")
    }

    /**
     * Back up all garage data (vehicles, maintenance schedules/records, fuel logs) to Firestore.
     */
    private suspend fun backupGarageData() {
        val garageData = coroutineScope {
            val vehiclesDeferred = async { vehicleDao.getAllVehiclesOnce() }
            val schedulesDeferred = async { maintenanceDao.getAllSchedulesOnce() }
            val recordsDeferred = async { maintenanceDao.getAllRecordsOnce() }
            val fuelLogsDeferred = async { fuelLogDao.getAllLogsOnce() }

            SyncableGarage(
                vehicles = vehiclesDeferred.await().map { it.toSyncable() },
                maintenanceSchedules = schedulesDeferred.await().map { it.toSyncable() },
                maintenanceRecords = recordsDeferred.await().map { it.toSyncable() },
                fuelLogs = fuelLogsDeferred.await().map { it.toSyncable() },
            )
        }

        val garageJson = garageData.toJson()
        val localMd5 = FirestoreSyncHelper.md5Hash(garageJson)
        val remoteMd5 = firestoreSyncHelper.getGarageMd5()
        val baselineMd5 = preferencesRepository.preferences.first().lastSyncedGarageMd5

        // Three-way conflict detection. The previous logic was a plain
        // last-writer-wins on a full JSON blob — if both sides moved away
        // from the baseline independently, the local push silently
        // overwrote the remote changes. Compare against the baseline so we
        // can tell which side(s) changed:
        val localChanged = localMd5 != baselineMd5
        val remoteChanged = remoteMd5 != null && remoteMd5 != baselineMd5

        when {
            !localChanged && !remoteChanged -> {
                // Both sides match the baseline — nothing to do.
                Log.d(TAG, "Garage data unchanged — skipping upload")
            }
            localChanged && !remoteChanged -> {
                // Only local diverged — push.
                firestoreSyncHelper.setGarageData(garageJson)
                preferencesRepository.setLastSyncedGarageMd5(localMd5)
                Log.d(TAG, "Garage data backed up successfully")
            }
            !localChanged && remoteChanged -> {
                // Only remote diverged — surface so user can pull (download
                // path lives outside this method; for now we record the
                // remote hash as the new baseline so we don't loop on this).
                _syncStatus.value = _syncStatus.value.copy(
                    error = "Remote garage changed — pull or resolve in settings.",
                )
                Log.w(TAG, "Garage diverged remotely (baseline=$baselineMd5 remote=$remoteMd5); not auto-pushing")
            }
            else -> {
                // Both diverged from the baseline AND the result still
                // differs — this is a real conflict. Refuse to clobber.
                if (localMd5 == remoteMd5) {
                    // Identical drift on both sides; record the common hash
                    // as the new baseline so we converge on the next sync.
                    preferencesRepository.setLastSyncedGarageMd5(localMd5)
                    Log.d(TAG, "Garage drifted identically on both sides; baseline updated")
                } else {
                    _syncStatus.value = _syncStatus.value.copy(
                        error = "Garage data conflict — local and remote both changed.",
                    )
                    Log.w(TAG, "Garage conflict (baseline=$baselineMd5 local=$localMd5 remote=$remoteMd5); user must resolve")
                }
            }
        }
    }

    /**
     * Schedule a debounced sync — cancels any pending debounce and waits 30 seconds
     * before enqueuing a one-time SyncWorker.
     */
    fun scheduleDebouncedSync() {
        _syncStatus.value = _syncStatus.value.copy(pendingChanges = true)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            enqueueOneTimeSync()
        }
    }

    /**
     * Schedule periodic sync via WorkManager (every 6 hours).
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_SYNC_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        Log.d(TAG, "Periodic sync scheduled (every $PERIODIC_SYNC_HOURS hours)")
    }

    /**
     * Cancel periodic sync (on sign-out).
     */
    fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        debounceJob?.cancel()
        Log.d(TAG, "Periodic sync cancelled")
    }

    /**
     * Schedule GPX backup on Wi-Fi + charging (Tier 2).
     */
    fun scheduleGpxBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
            .setRequiresCharging(true)
            .build()

        val backupRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            GPX_BACKUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            backupRequest,
        )
        Log.d(TAG, "GPX backup scheduled (Wi-Fi + charging)")
    }

    /**
     * Immediately enqueue a one-time sync (no debounce delay).
     */
    fun scheduleImmediateSync() {
        debounceJob?.cancel()
        _syncStatus.value = _syncStatus.value.copy(pendingChanges = true)
        enqueueOneTimeSync()
    }

    /**
     * Restore tracks from Cloud Storage that don't already exist locally.
     * Returns the number of tracks restored.
     */
    suspend fun restoreTracks(): Int = withContext(Dispatchers.IO) {
        val trackIds = firestoreSyncHelper.listBackedUpTrackIds()
        var restoredCount = 0

        for (trackId in trackIds) {
            try {
                // Skip if track already exists locally
                if (trackDao.getTrackById(trackId) != null) {
                    Log.d(TAG, "Track $trackId already exists locally, skipping")
                    continue
                }

                // Download and parse
                val bytes = firestoreSyncHelper.downloadGpxFile(trackId) ?: continue
                val result = TrackImporter.import(ByteArrayInputStream(bytes))

                // Insert track, points, and pace notes atomically — a crash
                // mid-loop must not leave a track without its points (or
                // points without a parent track).
                database.withTransaction {
                    trackDao.insertTrack(result.track)
                    result.points.chunked(1000).forEach { chunk ->
                        trackPointDao.insertPoints(chunk)
                    }
                    if (result.paceNotes.isNotEmpty()) {
                        paceNoteDao.insertNotes(result.paceNotes)
                    }
                }

                restoredCount++
                Log.d(TAG, "Restored track: $trackId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore track $trackId", e)
            }
        }

        Log.d(TAG, "Restore complete: $restoredCount tracks restored from ${trackIds.size} files")
        restoredCount
    }

    private fun enqueueOneTimeSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest,
        )
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val DEBOUNCE_DELAY_MS = 30_000L
        private const val PERIODIC_SYNC_HOURS = 6L
        private const val PERIODIC_SYNC_WORK_NAME = "rallytrax_periodic_sync"
        private const val ONE_TIME_SYNC_WORK_NAME = "rallytrax_debounced_sync"
        private const val GPX_BACKUP_WORK_NAME = "rallytrax_gpx_backup"
    }
}
