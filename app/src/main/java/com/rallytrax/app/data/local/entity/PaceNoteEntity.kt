package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class NoteType {
    LEFT,
    RIGHT,
    STRAIGHT,
    CREST,
    DIP,
    HAIRPIN_LEFT,
    HAIRPIN_RIGHT,
    SQUARE_LEFT,
    SQUARE_RIGHT,
    SMALL_CREST,
    SMALL_DIP,
    BIG_CREST,
    BIG_DIP,
}

enum class NoteModifier {
    NONE,
    TIGHTENS,
    OPENS,
    LONG,
    INTO,
    OVER,
    DONT_CUT,
    KEEP_IN,
    SHORT,
    VERY_LONG,
}

enum class SeverityHalf {
    MINUS,
    NONE,
    PLUS,
}

enum class Conjunction {
    INTO,
    AND,
    DISTANCE,
}

@Entity(
    tableName = "pace_notes",
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
data class PaceNoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val pointIndex: Int,
    val distanceFromStart: Double,
    val noteType: NoteType,
    val severity: Int, // 1 (hairpin) to 6 (flat-out kink)
    val modifier: NoteModifier = NoteModifier.NONE,
    val callText: String,
    val callDistanceM: Double = 0.0, // distance to next note
    val severityHalf: SeverityHalf = SeverityHalf.NONE, // +/- half-step
    val conjunction: Conjunction = Conjunction.DISTANCE, // into/and/distance
    val turnRadiusM: Double? = null, // computed radius for calibration
)
