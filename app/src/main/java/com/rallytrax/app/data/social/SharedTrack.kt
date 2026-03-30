package com.rallytrax.app.data.social

/**
 * Represents a track shared by a user on Firestore.
 * Stored at: users/{uid}/sharedTracks/{trackId}
 */
data class SharedTrack(
    val trackId: String = "",
    val ownerUid: String = "",
    val ownerDisplayName: String? = null,
    val ownerPhotoUrl: String? = null,
    val name: String = "",
    val description: String? = null,
    val distanceMeters: Double = 0.0,
    val durationMs: Long = 0L,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val difficultyRating: String? = null,
    val surfaceBreakdown: String? = null,
    val recordedAt: Long = 0L,
    val sharedAt: Long = 0L,
)
