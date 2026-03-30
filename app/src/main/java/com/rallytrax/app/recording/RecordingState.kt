package com.rallytrax.app.recording

data class LatLng(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
)

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
}

/**
 * Live sensor readings for the HUD overlay during recording.
 * Values are in m/s^2 for acceleration and converted to G-forces in the UI.
 */
data class SensorHudData(
    val lateralAccelMps2: Double? = null,
    val longitudinalAccelMps2: Double? = null,
    val verticalAccelMps2: Double? = null,
    val peakLateralG: Double = 0.0,
    val peakBrakingG: Double = 0.0,
    val alertCount: Int = 0,
) {
    companion object {
        val EMPTY = SensorHudData()
        /** Standard gravity in m/s^2 */
        const val GRAVITY = 9.80665
        /** Lateral G threshold for triggering an alert */
        const val LATERAL_G_ALERT_THRESHOLD = 0.5
        /** Braking G threshold for triggering an alert */
        const val BRAKING_G_ALERT_THRESHOLD = 0.4
    }

    /** Lateral acceleration in G-forces (absolute value) */
    val lateralG: Double? get() = lateralAccelMps2?.let { kotlin.math.abs(it) / GRAVITY }
    /** Longitudinal acceleration in G-forces (positive = accel, negative = brake) */
    val longitudinalG: Double? get() = longitudinalAccelMps2?.let { it / GRAVITY }
    /** Vertical acceleration in G-forces (absolute value) */
    val verticalG: Double? get() = verticalAccelMps2?.let { kotlin.math.abs(it) / GRAVITY }
}

data class RecordingData(
    val pathSegments: List<List<LatLng>> = emptyList(),
    val currentSpeed: Double = 0.0,
    val elapsedTimeMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val currentLatLng: LatLng? = null,
    val pointCount: Int = 0,
    val gpsAccuracy: Float? = null,
    val avgSpeedMps: Double = 0.0,
    val currentElevation: Double? = null,
    val isAutoPaused: Boolean = false,
) {
    companion object {
        val EMPTY = RecordingData()
    }
}
