package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "segment_runs",
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("segmentId"), Index("trackId")],
)
data class SegmentRunEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val segmentId: String,
    val trackId: String,
    val startPointIndex: Int,
    val endPointIndex: Int,
    val durationMs: Long,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val timestamp: Long, // when this run was recorded
)
