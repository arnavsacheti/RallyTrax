package com.rallytrax.app.recording

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rallytrax.app.MainActivity
import com.rallytrax.app.notifications.NotificationChannels

class TrackingNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = NotificationChannels.RECORDING_ACTIVE
        const val NOTIFICATION_ID = 1
    }

    fun createNotification(
        elapsedTime: String,
        distance: String,
        isPaused: Boolean = false,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = if (isPaused) "Paused" else "Recording"
        val contentText = "$statusText \u2022 $elapsedTime \u2022 $distance"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("RallyTrax")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }
}
