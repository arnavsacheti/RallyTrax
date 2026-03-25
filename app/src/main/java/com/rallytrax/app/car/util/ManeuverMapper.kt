package com.rallytrax.app.car.util

import androidx.car.app.navigation.model.Maneuver
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity

/**
 * Maps RallyTrax pace note types to Android Auto navigation maneuver icons.
 * Uses severity grades (1-6 scale) to select appropriate icon sharpness:
 *   1-2: Sharp turns (very tight, heavy braking)
 *   3:   Normal turns (medium)
 *   4-5: Slight turns (open, light braking)
 *   6:   Straight (near flat-out)
 */
fun PaceNoteEntity.toManeuver(): Maneuver {
    val type = when (noteType) {
        NoteType.LEFT -> when {
            severity <= 2 -> Maneuver.TYPE_TURN_SHARP_LEFT
            severity <= 3 -> Maneuver.TYPE_TURN_NORMAL_LEFT
            severity <= 5 -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            else -> Maneuver.TYPE_STRAIGHT
        }
        NoteType.RIGHT -> when {
            severity <= 2 -> Maneuver.TYPE_TURN_SHARP_RIGHT
            severity <= 3 -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            severity <= 5 -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            else -> Maneuver.TYPE_STRAIGHT
        }
        NoteType.HAIRPIN_LEFT -> Maneuver.TYPE_U_TURN_LEFT
        NoteType.HAIRPIN_RIGHT -> Maneuver.TYPE_U_TURN_RIGHT
        NoteType.SQUARE_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
        NoteType.SQUARE_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
        NoteType.STRAIGHT -> Maneuver.TYPE_STRAIGHT
        NoteType.CREST, NoteType.BIG_CREST, NoteType.SMALL_CREST -> Maneuver.TYPE_STRAIGHT
        NoteType.DIP, NoteType.BIG_DIP, NoteType.SMALL_DIP -> Maneuver.TYPE_STRAIGHT
    }
    return Maneuver.Builder(type).build()
}

/**
 * Returns a human-readable severity label for display on the routing card.
 */
fun severityLabel(severity: Int): String = when (severity) {
    1 -> "Tight"
    2 -> "Bad"
    3 -> "Medium"
    4 -> "Moderate"
    5 -> "Easy"
    6 -> "Flat"
    else -> ""
}
