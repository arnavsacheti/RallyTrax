package com.rallytrax.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.rallytrax.app.data.gpx.GpxExporter
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    fun setError(message: String) {
        _syncStatus.value = _syncStatus.value.copy(error = message, isSyncing = false)
    }

    fun clearError() {
        _syncStatus.value = _syncStatus.value.copy(error = null)
    }

    /**
     * Perform a full sync cycle using the provided Drive credential.
     */
    suspend fun performSync(credential: GoogleAccountCredential) {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)
        try {
            val driveHelper = DriveServiceHelper(credential)

            // 1. Get or create manifest
            var manifest = driveHelper.getOrCreateManifest()

            // 2. Get local settings
            val localSettings = preferencesRepository.toSyncableSettings()
            val localJson = localSettings.toJson()
            val localMd5 = DriveServiceHelper.md5Hash(localJson)

            // 3. Compare and sync
            if (manifest.settingsFileId != null && manifest.settingsMd5 != localMd5) {
                // Remote exists and differs from local — download and merge
                val remoteJson = driveHelper.downloadSettings(manifest)
                if (remoteJson != null) {
                    val remoteSettings = SyncableSettings.fromJson(remoteJson)
                    val merged = localSettings.mergeWith(remoteSettings)
                    preferencesRepository.applySyncableSettings(merged)

                    // Upload merged result
                    val mergedJson = merged.toJson()
                    manifest = driveHelper.uploadSettings(mergedJson, manifest)
                    driveHelper.uploadManifest(manifest)
                }
            } else if (manifest.settingsFileId == null || manifest.settingsMd5 != localMd5) {
                // No remote or local has changed — upload local
                manifest = driveHelper.uploadSettings(localJson, manifest)
                driveHelper.uploadManifest(manifest)
            }
            // else: hashes match, nothing to do

            // 4. Update sync time
            val now = System.currentTimeMillis()
            preferencesRepository.setLastSyncTime(now)
            _syncStatus.value = SyncStatus(
                lastSyncTime = now,
                isSyncing = false,
                pendingChanges = false,
                error = null,
            )

            // 5. GPX track backup — upload tracks not yet in manifest
            val prefs = preferencesRepository.preferences.first()
            if (prefs.backupTracksEnabled) {
                backupTracks(driveHelper, manifest)
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
     * Back up all local GPX tracks to Google Drive that are not yet in the manifest.
     * Each track is exported to GPX format and uploaded to the appDataFolder.
     */
    private suspend fun backupTracks(driveHelper: DriveServiceHelper, manifest: SyncManifest) {
        val allTracks = trackDao.getAllTracksOnce()
        val existingIds = manifest.gpxFileIds
        val tracksToBackup = allTracks.filter { it.id !in existingIds }

        if (tracksToBackup.isEmpty()) {
            Log.d(TAG, "All tracks already backed up (${allTracks.size} total)")
            return
        }

        Log.d(TAG, "Backing up ${tracksToBackup.size} tracks to Google Drive")
        val updatedGpxFileIds = existingIds.toMutableMap()

        for (track in tracksToBackup) {
            try {
                val points = trackPointDao.getPointsForTrackOnce(track.id)
                val paceNotes = paceNoteDao.getNotesForTrackOnce(track.id)
                val gpxBytes = ByteArrayOutputStream().also { stream ->
                    GpxExporter.export(track, points, stream, paceNotes)
                }.toByteArray()

                val driveFileId = driveHelper.uploadGpxFile(track.id, gpxBytes)
                updatedGpxFileIds[track.id] = driveFileId
                Log.d(TAG, "Backed up track ${track.id} (${gpxBytes.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to back up track ${track.id}", e)
            }
        }

        // Persist updated manifest with new gpxFileIds
        val updatedManifest = manifest.copy(gpxFileIds = updatedGpxFileIds)
        driveHelper.uploadManifest(updatedManifest)
        Log.d(TAG, "GPX backup complete: ${updatedGpxFileIds.size} tracks in manifest")
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
