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
