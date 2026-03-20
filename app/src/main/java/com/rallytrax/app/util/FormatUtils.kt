package com.rallytrax.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatElapsedTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        String.format(Locale.US, "%.0f m", meters)
    } else {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    }
}

fun formatSpeed(mps: Double): String {
    val kmh = mps * 3.6
    return String.format(Locale.US, "%.0f", kmh)
}

fun formatDate(epochMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    val dateTime = instant.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    return formatter.format(dateTime)
}

fun formatDateTime(epochMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    val dateTime = instant.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US)
    return formatter.format(dateTime)
}
