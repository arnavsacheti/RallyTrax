package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tracks",
    indices = [
        Index("recordedAt"),
        Index("vehicleId"),
    ],
)
data class TrackEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val recordedAt: Long, // epoch millis (UTC)
    val durationMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val boundingBoxNorthLat: Double = 0.0,
    val boundingBoxSouthLat: Double = 0.0,
    val boundingBoxEastLon: Double = 0.0,
    val boundingBoxWestLon: Double = 0.0,
    val tags: String = "", // comma-separated
    val gpxFilePath: String = "",
    val thumbnailPath: String? = null,
    val vehicleId: String? = null,
    // Route classification (Stage 1.2.5)
    val routeType: String? = null,
    val activityTags: String = "",
    val difficultyRating: String? = null,
    val curvinessScore: Double? = null,
    val primarySurface: String? = null,
    val surfaceBreakdown: String? = null,
    val trackCategory: String = "stint", // "route" or "stint"
    // Cross-sensor insight cache (computed post-recording)
    val peakCorneringG: Double? = null,
    val avgCorneringG: Double? = null,
    val smoothnessScore: Int? = null,
    val roadRoughnessIndex: Double? = null,
    val brakingEfficiencyScore: Int? = null,
    val elevationAdjustedAvgSpeedMps: Double? = null,
)
