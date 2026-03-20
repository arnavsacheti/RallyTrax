package com.rallytrax.app.recording

data class LatLng(
    val latitude: Double,
    val longitude: Double,
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
) {
    companion object {
        val EMPTY = RecordingData()
    }
}
