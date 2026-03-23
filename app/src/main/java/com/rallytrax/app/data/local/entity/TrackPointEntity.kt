package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_points",
    primaryKeys = ["trackId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class TrackPointEntity(
    val trackId: String,
    val index: Int,
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val timestamp: Long, // epoch millis (UTC)
    val speed: Double? = null,
    val bearing: Double? = null,
    val accuracy: Float? = null,
    val accelMps2: Double? = null, // m/s² (positive=accel, negative=decel)
    val curvatureDegPerM: Double? = null, // bearing change per metre
    val surfaceType: String? = null, // paved/gravel/dirt/cobblestone
    val segmentMarker: String? = null, // fuel/photo/break — marks user-created segment boundaries
)
