package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A commonly driven route detected by clustering stints with similar
 * start/end points and trajectory overlap.
 *
 * [stintIds] is a semicolon-delimited list of track IDs that match this route.
 * [representativeTrackId] is the "best" track to use as the canonical polyline.
 */
@Entity(tableName = "common_routes")
data class CommonRouteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val stintIds: String, // semicolon-delimited track IDs
    val representativeTrackId: String, // canonical track for map display
    val driveCount: Int = 0,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    val avgDistanceMeters: Double = 0.0,
    val avgDurationMs: Long = 0L,
    val bestDurationMs: Long = 0L,
    val avgSpeedMps: Double = 0.0,
    val lastDrivenAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aiDescription: String? = null, // Gemini-generated description
)
