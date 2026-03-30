package com.rallytrax.app.data.social

data class SharedTrack(
    val trackId: String = "",
    val name: String = "",
    val recordedAt: Long = 0L,
    val distanceMeters: Double = 0.0,
    val durationMs: Long = 0L,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val difficultyRating: String? = null,
    val boundingBoxNorthLat: Double = 0.0,
    val boundingBoxSouthLat: Double = 0.0,
    val boundingBoxEastLon: Double = 0.0,
    val boundingBoxWestLon: Double = 0.0,
    val ownerUid: String = "",
    val ownerDisplayName: String? = null,
    val ownerPhotoUrl: String? = null,
)
