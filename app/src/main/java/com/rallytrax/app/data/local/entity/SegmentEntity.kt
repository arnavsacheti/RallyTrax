package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceMeters: Double,
    val isFavorite: Boolean = false,
    val isUserDefined: Boolean = false, // false = auto-detected
    val createdAt: Long = System.currentTimeMillis(),
    // Bounding box for fast SQL pre-filtering
    val boundingBoxNorthLat: Double = 0.0,
    val boundingBoxSouthLat: Double = 0.0,
    val boundingBoxEastLon: Double = 0.0,
    val boundingBoxWestLon: Double = 0.0,
)
