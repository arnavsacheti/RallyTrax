package com.rallytrax.app.ui.recording

import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.recording.RecordingData
import com.rallytrax.app.recording.SensorHudData
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import java.util.Locale
import kotlin.math.abs

// Each field renders as a single [value, label] metric in the swipeable pager
// on the recording screen. The recording pager is driven by one of the preset
// layouts below; users pick a preset in Settings.
enum class RecordingField(val label: String) {
    Time("Time"),
    Distance("Distance"),
    AvgSpeed("Avg Speed"),
    MaxSpeed("Max Speed"),
    ElevationGain("Elev Gain"),
    Elevation("Elevation"),
    GpsAccuracy("GPS Acc"),
    PointCount("Points"),
    LateralG("Lat G"),
    BrakingG("Brk G"),
    ;

    fun format(
        data: RecordingData,
        sensor: SensorHudData,
        prefs: UserPreferencesData,
    ): String {
        val unit = prefs.unitSystem
        return when (this) {
            Time -> formatElapsedTime(data.elapsedTimeMs)
            Distance -> formatDistance(data.distanceMeters, unit)
            AvgSpeed -> "${formatSpeed(data.avgSpeedMps, unit)} ${speedUnit(unit)}"
            MaxSpeed -> "${formatSpeed(data.maxSpeedMps, unit)} ${speedUnit(unit)}"
            ElevationGain -> formatElevation(data.elevationGainM, unit)
            Elevation -> data.currentElevation?.let { formatElevation(it, unit) } ?: "—"
            GpsAccuracy -> data.gpsAccuracy?.let { "${it.toInt()}m" } ?: "—"
            PointCount -> "${data.pointCount}"
            LateralG -> sensor.lateralG?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
            BrakingG -> sensor.longitudinalG?.let {
                String.format(Locale.US, "%.2f", abs(it))
            } ?: "—"
        }
    }
}

enum class RecordingFieldPreset(val label: String, val pages: List<List<RecordingField>>) {
    Balanced(
        label = "Balanced",
        pages = listOf(
            listOf(RecordingField.Time, RecordingField.Distance),
            listOf(
                RecordingField.AvgSpeed,
                RecordingField.MaxSpeed,
                RecordingField.ElevationGain,
                RecordingField.Elevation,
            ),
            listOf(
                RecordingField.GpsAccuracy,
                RecordingField.PointCount,
                RecordingField.LateralG,
                RecordingField.BrakingG,
            ),
        ),
    ),
    Performance(
        label = "Performance",
        pages = listOf(
            listOf(RecordingField.Time, RecordingField.Distance, RecordingField.MaxSpeed),
            listOf(
                RecordingField.LateralG,
                RecordingField.BrakingG,
                RecordingField.AvgSpeed,
            ),
        ),
    ),
    Minimal(
        label = "Minimal",
        pages = listOf(
            listOf(RecordingField.Time, RecordingField.Distance),
        ),
    ),
    ;

    companion object {
        fun fromName(name: String?): RecordingFieldPreset =
            entries.firstOrNull { it.name == name } ?: Balanced
    }
}
