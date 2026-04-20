package com.rallytrax.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

// Centralized registry for every notification channel RallyTrax uses.
// Channel IDs, names, and importance levels match the spec in CLAUDE.md.
// Channels are idempotent — safe to call registerAll() on every Application onCreate.
object NotificationChannels {
    const val RECORDING_ACTIVE = "recording_active"
    const val PACE_NOTES = "pace_notes"
    const val MAINTENANCE = "maintenance"
    const val FUEL_PROMPTS = "fuel_prompts"
    const val ACTIVITY_COMPLETE = "activity_complete"

    fun registerAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                RECORDING_ACTIVE,
                "Recording",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing recording status (foreground service)"
                setShowBadge(false)
            },
            NotificationChannel(
                PACE_NOTES,
                "Pace notes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Upcoming pace note calls"
            },
            NotificationChannel(
                MAINTENANCE,
                "Maintenance reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders for upcoming and overdue vehicle maintenance"
            },
            NotificationChannel(
                FUEL_PROMPTS,
                "Fuel prompts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Low-fuel warnings and fill-up suggestions"
            },
            NotificationChannel(
                ACTIVITY_COMPLETE,
                "Activity complete",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Summary when a drive finishes"
            },
        )
        manager.createNotificationChannels(channels)
    }
}
