package com.rallytrax.app.data.social

import com.rallytrax.app.data.local.entity.TrackEntity

/**
 * Lightweight track summary for social feed display.
 * Stored in Firestore under users/{uid}/sharedTracks/{trackId}.
 */
data class SharedTrack(
    val trackId: String = "",
    val ownerUid: String = "",
    val ownerDisplayName: String? = null,
    val ownerPhotoUrl: String? = null,
    val name: String = "",
    val description: String? = null,
    val recordedAt: Long = 0L,
    val durationMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    // Bounding box for co-drive / nearby detection
    val boundingBoxNorthLat: Double = 0.0,
    val boundingBoxSouthLat: Double = 0.0,
    val boundingBoxEastLon: Double = 0.0,
    val boundingBoxWestLon: Double = 0.0,
    val routeType: String? = null,
    val difficultyRating: String? = null,
    val primarySurface: String? = null,
    val thumbnailPath: String? = null,
    val publishedAt: Long = System.currentTimeMillis(),
)

/**
 * Convert a local [TrackEntity] into a [SharedTrack] for Firestore publishing.
 */
fun TrackEntity.toSharedTrack(
    uid: String,
    displayName: String?,
    photoUrl: String?,
): SharedTrack = SharedTrack(
    trackId = id,
    ownerUid = uid,
    ownerDisplayName = displayName,
    ownerPhotoUrl = photoUrl,
    name = name,
    description = description,
    recordedAt = recordedAt,
    durationMs = durationMs,
    distanceMeters = distanceMeters,
    maxSpeedMps = maxSpeedMps,
    avgSpeedMps = avgSpeedMps,
    elevationGainM = elevationGainM,
    boundingBoxNorthLat = boundingBoxNorthLat,
    boundingBoxSouthLat = boundingBoxSouthLat,
    boundingBoxEastLon = boundingBoxEastLon,
    boundingBoxWestLon = boundingBoxWestLon,
    routeType = routeType,
    difficultyRating = difficultyRating,
    primarySurface = primarySurface,
    thumbnailPath = thumbnailPath,
)
