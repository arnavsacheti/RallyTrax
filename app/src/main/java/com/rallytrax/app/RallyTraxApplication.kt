package com.rallytrax.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
    }
}
