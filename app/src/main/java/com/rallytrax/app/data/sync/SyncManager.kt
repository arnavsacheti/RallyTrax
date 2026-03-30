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
import com.google.api.services.drive.DriveScopes
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val vehicleDao: VehicleDao,
    private val maintenanceDao: MaintenanceDao,
    private val fuelLogDao: FuelLogDao,
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

            // 3. Compare and sync settings
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

            // 3b. Backup garage data (vehicles, maintenance, fuel logs)
            manifest = backupGarageData(driveHelper, manifest)

            // 4. Update sync time
            val now = System.currentTimeMillis()
            preferencesRepository.setLastSyncTime(now)
            _syncStatus.value = SyncStatus(
                lastSyncTime = now,
                isSyncing = false,
                pendingChanges = false,
                error = null,
            )

            // 5. GPX backup (Tier 2) — if enabled, schedule via WorkManager
            val prefs = preferencesRepository.preferences.first()
            if (prefs.backupTracksEnabled) {
                Log.d(TAG, "GPX backup is enabled — will sync on Wi-Fi + charging")
                scheduleGpxBackup()
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
     * Back up all garage data (vehicles, maintenance schedules/records, fuel logs) to Drive.
     * Loads all entities in parallel, serializes to JSON, computes MD5 to skip unchanged data.
     * Returns the updated manifest (or the original if nothing changed).
     */
    private suspend fun backupGarageData(
        driveHelper: DriveServiceHelper,
        manifest: SyncManifest,
    ): SyncManifest {
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
        val garageMd5 = DriveServiceHelper.md5Hash(garageJson)

        if (garageMd5 == manifest.garageMd5) {
            Log.d(TAG, "Garage data unchanged — skipping upload")
            return manifest
        }

        val updatedManifest = driveHelper.uploadGarageData(garageJson, manifest)
        driveHelper.uploadManifest(updatedManifest)
        Log.d(TAG, "Garage data backed up successfully")
        return updatedManifest
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
