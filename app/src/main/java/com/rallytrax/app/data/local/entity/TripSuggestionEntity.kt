package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A suggested trip grouping detected by TripDetector.
 *
 * [stintIds] is a semicolon-delimited list of track IDs that belong to this suggestion.
 * [status]: "pending", "accepted", "dismissed"
 */
@Entity(tableName = "trip_suggestions")
data class TripSuggestionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val stintIds: String, // semicolon-delimited track IDs
    val suggestedName: String,
    val status: String = "pending", // pending | accepted | dismissed
    val confidenceScore: Double = 0.0, // 0.0–1.0
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMs: Long = 0L,
    val stintCount: Int = 0,
    val startTimestamp: Long = 0L, // earliest stint recordedAt
    val endTimestamp: Long = 0L, // latest stint end time
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aiGeneratedName: String? = null, // Gemini-generated name, null if not yet enriched
)
