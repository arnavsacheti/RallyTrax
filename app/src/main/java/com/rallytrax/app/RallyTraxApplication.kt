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
import com.rallytrax.app.notifications.NotificationChannels
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
        // super.onCreate() is the generated Hilt entry point — it finishes
        // graph init and populates @Inject fields (workerFactory, syncManager).
        // Anything that needs an injected field MUST run after this line, and
        // anything WorkManager-related MUST run after workManagerConfiguration
        // can be queried (i.e. after super), since WorkManager.getInstance(...)
        // calls back into the Configuration.Provider on first use.
        super.onCreate()

        // Register every notification channel up-front so first-use of any
        // notification type hits an already-registered channel.
        NotificationChannels.registerAll(this)

        // Initialize Firebase App Check for attestation
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Configure osmdroid (user-agent is required for tile downloads)
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
        }

        // All work-enqueueing is grouped into one method so the order
        // (super → init steps → enqueue) is obvious at a glance.
        scheduleStartupWork()
    }

    private fun scheduleStartupWork() {
        val workManager = WorkManager.getInstance(this)

        // Backfill accel/curvature data for existing tracks (runs once)
        val backfillRequest = OneTimeWorkRequestBuilder<BackfillWorker>().build()
        workManager.enqueueUniqueWork(
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
        workManager.enqueueUniquePeriodicWork(
            com.rallytrax.app.data.maintenance.MaintenanceReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            maintenanceReminderRequest,
        )

        // Schedule periodic trip detection (every 6 hours)
        val tripDetectionRequest = PeriodicWorkRequestBuilder<com.rallytrax.app.data.trips.TripDetectionWorker>(
            6, TimeUnit.HOURS,
        ).build()
        workManager.enqueueUniquePeriodicWork(
            com.rallytrax.app.data.trips.TripDetectionWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            tripDetectionRequest,
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
        workManager.enqueueUniquePeriodicWork(
            com.rallytrax.app.data.fuel.GasStationCacheWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            gasStationRequest,
        )
    }
}
