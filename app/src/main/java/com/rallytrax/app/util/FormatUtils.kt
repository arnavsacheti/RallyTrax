package com.rallytrax.app.util

import com.rallytrax.app.data.preferences.UnitSystem
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

fun formatDistance(meters: Double, unitSystem: UnitSystem = UnitSystem.METRIC): String {
    return when (unitSystem) {
        UnitSystem.METRIC -> {
            if (meters < 1000) {
                String.format(Locale.US, "%.0f m", meters)
            } else {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            }
        }
        UnitSystem.IMPERIAL -> {
            val feet = meters * 3.28084
            val miles = meters / 1609.344
            if (miles < 0.1) {
                String.format(Locale.US, "%.0f ft", feet)
            } else {
                String.format(Locale.US, "%.2f mi", miles)
            }
        }
    }
}

fun formatSpeed(mps: Double, unitSystem: UnitSystem = UnitSystem.METRIC): String {
    return when (unitSystem) {
        UnitSystem.METRIC -> {
            val kmh = mps * 3.6
            String.format(Locale.US, "%.0f", kmh)
        }
        UnitSystem.IMPERIAL -> {
            val mph = mps * 2.23694
            String.format(Locale.US, "%.0f", mph)
        }
    }
}

fun speedUnit(unitSystem: UnitSystem = UnitSystem.METRIC): String {
    return when (unitSystem) {
        UnitSystem.METRIC -> "km/h"
        UnitSystem.IMPERIAL -> "mph"
    }
}

fun formatElevation(meters: Double, unitSystem: UnitSystem = UnitSystem.METRIC): String {
    return when (unitSystem) {
        UnitSystem.METRIC -> String.format(Locale.US, "%.0f m", meters)
        UnitSystem.IMPERIAL -> String.format(Locale.US, "%.0f ft", meters * 3.28084)
    }
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

fun formatRelativeTime(epochMs: Long): String {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val diffMs = now.toEpochMilli() - epochMs
    val diffMinutes = diffMs / 60_000
    val diffHours = diffMs / 3_600_000

    val recordedDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        recordedDate == today -> "${diffHours}h ago"
        recordedDate == today.minusDays(1) -> "Yesterday"
        recordedDate.year == today.year -> {
            recordedDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        }
        else -> {
            recordedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        }
    }
}
