package com.rallytrax.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.rallytrax.app.data.local.BackfillWorker
import com.rallytrax.app.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RallyTraxApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase App Check for attestation
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Configure osmdroid (user-agent is required for tile downloads)
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
        }

        // Backfill accel/curvature data for existing tracks (runs once)
        val backfillRequest = OneTimeWorkRequestBuilder<BackfillWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "backfill_accel_curvature",
            ExistingWorkPolicy.KEEP,
            backfillRequest,
        )

        // Schedule periodic Drive sync if user is signed in
        if (FirebaseAuth.getInstance().currentUser != null) {
            syncManager.schedulePeriodicSync()
        }

        // Schedule daily maintenance reminder check
        val maintenanceReminderRequest = PeriodicWorkRequestBuilder<com.rallytrax.app.data.maintenance.MaintenanceReminderWorker>(
            1, TimeUnit.DAYS,
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.rallytrax.app.data.maintenance.MaintenanceReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            maintenanceReminderRequest,
        )

        // Schedule gas station cache refresh (monthly, requires network)
        val gasStationRequest = PeriodicWorkRequestBuilder<com.rallytrax.app.data.fuel.GasStationCacheWorker>(
            30, TimeUnit.DAYS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.rallytrax.app.data.fuel.GasStationCacheWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            gasStationRequest,
        )
    }
}
