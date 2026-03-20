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
)
