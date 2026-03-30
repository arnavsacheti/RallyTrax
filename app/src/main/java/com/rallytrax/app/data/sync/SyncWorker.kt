package com.rallytrax.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.rallytrax.app.MainActivity
import com.rallytrax.app.R
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val preferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.failure()
        val email = user.email ?: return Result.failure()

        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                listOf(DriveScopes.DRIVE_APPDATA),
            )
            credential.selectedAccountName = email
            syncManager.performSync(credential)
            Result.success()
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "Auth token expired, needs interactive re-auth", e)
            showReAuthNotification(applicationContext)
            syncManager.setError("Drive authorization expired. Please re-authorize.")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed", e)
            syncManager.setError("Sync failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun showReAuthNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            SYNC_ERROR_CHANNEL_ID,
            "Sync Errors",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications for sync authorization errors"
        }
        manager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SYNC_ERROR_CHANNEL_ID)
            .setContentTitle("RallyTrax Sync")
            .setContentText("Drive authorization expired. Tap to re-authorize.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(SYNC_ERROR_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val SYNC_ERROR_CHANNEL_ID = "sync_errors"
        private const val SYNC_ERROR_NOTIFICATION_ID = 2001
    }
}
