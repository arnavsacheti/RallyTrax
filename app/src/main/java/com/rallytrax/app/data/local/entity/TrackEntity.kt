package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tracks")
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
)
