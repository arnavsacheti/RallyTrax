package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.Conjunction
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PaceNoteExplanation(
    val radiusM: Double?,
    val gradeReason: String?,
    val safeSpeedKmh: Double?,
    val modifierReason: String?,
    val elevationReason: String?,
    val conjunctionReason: String?,
    val segmentLengthM: Double?,
    val bearingChangeDeg: Double?,
    val entryBearingDeg: Double?,
    val exitBearingDeg: Double?,
)

/**
 * Generates human-readable explanations for why a pace note call was made.
 */
object PaceNoteExplainer {

    private data class GradeBand(val minRadius: Double, val maxRadius: Double, val grade: Int)

    private val GRADE_BANDS = listOf(
        GradeBand(0.0, 7.0, 1),
        GradeBand(7.0, 12.0, 2),
        GradeBand(12.0, 22.0, 3),
        GradeBand(22.0, 43.0, 4),
        GradeBand(43.0, 71.0, 5),
        GradeBand(71.0, 148.0, 6),
    )

    private val TURN_TYPES = setOf(
        NoteType.LEFT, NoteType.RIGHT,
        NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT,
        NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT,
    )

    private val ELEVATION_TYPES = setOf(
        NoteType.CREST, NoteType.DIP,
        NoteType.SMALL_CREST, NoteType.SMALL_DIP,
        NoteType.BIG_CREST, NoteType.BIG_DIP,
    )

    fun explain(
        note: PaceNoteEntity,
        allPoints: List<TrackPointEntity>,
        prevNote: PaceNoteEntity? = null,
        nextNote: PaceNoteEntity? = null,
    ): PaceNoteExplanation {
        val segmentPoints = extractSegmentPoints(note, allPoints)

        return PaceNoteExplanation(
            radiusM = note.turnRadiusM,
            gradeReason = buildGradeReason(note),
            safeSpeedKmh = computeSafeSpeed(note),
            modifierReason = buildModifierReason(note, segmentPoints),
            elevationReason = buildElevationReason(note, segmentPoints),
            conjunctionReason = buildConjunctionReason(note),
            segmentLengthM = computeSegmentLength(segmentPoints),
            bearingChangeDeg = computeBearingChange(segmentPoints),
            entryBearingDeg = computeEntryBearing(segmentPoints),
            exitBearingDeg = computeExitBearing(segmentPoints),
        )
    }

    private fun extractSegmentPoints(
        note: PaceNoteEntity,
        allPoints: List<TrackPointEntity>,
    ): List<TrackPointEntity> {
        val start = note.segmentStartIndex ?: return emptyList()
        val end = note.segmentEndIndex ?: return emptyList()
        return allPoints.filter { it.index in start..end }
    }

    // ── Grade reason ────────────────────────────────────────────────────

    private fun buildGradeReason(note: PaceNoteEntity): String? {
        val radius = note.turnRadiusM ?: return null
        if (note.noteType !in TURN_TYPES) return null

        val band = GRADE_BANDS.find { radius >= it.minRadius && radius < it.maxRadius }
            ?: return "Radius %.1fm beyond grade bands".format(radius)

        return "Radius %.1fm \u2192 Grade %d (%.0f\u2013%.0fm band)".format(
            radius, band.grade, band.minRadius, band.maxRadius,
        )
    }

    // ── Safe speed ──────────────────────────────────────────────────────

    private fun computeSafeSpeed(note: PaceNoteEntity): Double? {
        val radius = note.turnRadiusM ?: return null
        if (note.noteType !in TURN_TYPES) return null
        if (radius <= 0) return null
        // v = sqrt(0.3 * g * r), converted to km/h
        return sqrt(0.3 * 9.81 * radius) * 3.6
    }

    // ── Modifier reason ─────────────────────────────────────────────────

    private fun buildModifierReason(
        note: PaceNoteEntity,
        segmentPoints: List<TrackPointEntity>,
    ): String? {
        val lengthM = computeSegmentLength(segmentPoints)
        val lengthStr = if (lengthM != null) "%.0fm".format(lengthM) else "segment"

        return when (note.modifier) {
            NoteModifier.TIGHTENS -> "Turn tightens progressively through the corner"
            NoteModifier.OPENS -> "Turn opens up \u2014 radius increases through the corner"
            NoteModifier.LONG -> "Extended turn over $lengthStr"
            NoteModifier.VERY_LONG -> "Very long turn over $lengthStr"
            NoteModifier.SHORT -> "Brief turn under $lengthStr"
            NoteModifier.INTO -> "Leads directly into the next feature"
            NoteModifier.OVER -> "Turn passes over an elevation change"
            NoteModifier.DONT_CUT -> "Don\u2019t cut \u2014 hazard on inside of turn"
            NoteModifier.KEEP_IN -> "Keep tight to the inside line"
            NoteModifier.NONE -> null
        }
    }

    // ── Elevation reason ────────────────────────────────────────────────

    private fun buildElevationReason(
        note: PaceNoteEntity,
        segmentPoints: List<TrackPointEntity>,
    ): String? {
        if (note.noteType !in ELEVATION_TYPES) return null

        val elevations = segmentPoints.mapNotNull { it.elevation }
        if (elevations.size < 2) {
            return describeElevationType(note.noteType)
        }

        val delta = abs(elevations.last() - elevations.first())
        val distance = computeSegmentLength(segmentPoints) ?: return describeElevationType(note.noteType)
        val gradient = if (distance > 0) (delta / distance) * 100.0 else 0.0
        val direction = if (note.noteType in setOf(NoteType.CREST, NoteType.SMALL_CREST, NoteType.BIG_CREST)) "rise" else "drop"

        return "%s: %.1fm %s over %.0fm (%.0f%% gradient)".format(
            describeElevationType(note.noteType), delta, direction, distance, gradient,
        )
    }

    private fun describeElevationType(type: NoteType): String = when (type) {
        NoteType.SMALL_CREST -> "Small crest"
        NoteType.CREST -> "Crest"
        NoteType.BIG_CREST -> "Big crest"
        NoteType.SMALL_DIP -> "Small dip"
        NoteType.DIP -> "Dip"
        NoteType.BIG_DIP -> "Big dip"
        else -> "Elevation change"
    }

    // ── Conjunction reason ───────────────────────────────────────────────

    private fun buildConjunctionReason(note: PaceNoteEntity): String? {
        val dist = note.callDistanceM
        return when (note.conjunction) {
            Conjunction.INTO -> "INTO: only %.0fm to next turn \u2014 no recovery time".format(dist)
            Conjunction.AND -> "AND: %.0fm gap \u2014 brief transition".format(dist)
            Conjunction.DISTANCE -> if (dist > 0) "%.0fm to next note".format(dist) else null
        }
    }

    // ── Segment geometry ────────────────────────────────────────────────

    private fun computeSegmentLength(points: List<TrackPointEntity>): Double? {
        if (points.size < 2) return null
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineM(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
        return total
    }

    private fun computeBearingChange(points: List<TrackPointEntity>): Double? {
        val entry = computeEntryBearing(points) ?: return null
        val exit = computeExitBearing(points) ?: return null
        var delta = abs(exit - entry)
        if (delta > 180) delta = 360 - delta
        return delta
    }

    private fun computeEntryBearing(points: List<TrackPointEntity>): Double? {
        if (points.size < 2) return null
        return bearingDeg(points[0].lat, points[0].lon, points[1].lat, points[1].lon)
    }

    private fun computeExitBearing(points: List<TrackPointEntity>): Double? {
        if (points.size < 2) return null
        val n = points.size
        return bearingDeg(points[n - 2].lat, points[n - 2].lon, points[n - 1].lat, points[n - 1].lon)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }

    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
