package com.rallytrax.app.ui.map

import androidx.compose.ui.graphics.Color
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity

/**
 * Segment of a track polyline with a specific color and feature flag.
 *
 * @property startIndex inclusive start in the track-point array
 * @property endIndex   inclusive end in the track-point array
 * @property color      polyline color for this segment
 * @property isFeature  true for turns/crests/dips, false for STRAIGHTs and base-color gaps
 */
data class ColoredSegment(
    val startIndex: Int,
    val endIndex: Int,
    val color: Color,
    val isFeature: Boolean,
)

/**
 * Dimmed Rally Blue used for gaps not covered by any pace note:
 * - Before the first note's segment
 * - After the last note's segment
 * - Legacy tracks with sparse null-index notes
 * - Tracks with no pace notes at all
 */
internal val BASE_TRACK_COLOR: Color = Color(0xFF1A73E8).copy(alpha = 0.3f)

/**
 * Builds a list of [ColoredSegment]s that continuously cover a track polyline.
 *
 * Every pace note (including STRAIGHT) produces a colored segment. STRAIGHTs
 * use `isFeature = false` with their severity color (purple). Turns, crests,
 * and dips use `isFeature = true`.
 *
 * [BASE_TRACK_COLOR] fills only uncovered gaps — typically the leading/trailing
 * edges and sparse regions from legacy notes with null segment indices.
 *
 * @param totalPoints number of points in the track
 * @param paceNotes   pace notes associated with the track
 * @return ordered, non-overlapping segments covering 0..<totalPoints
 */
fun buildColoredSegments(
    totalPoints: Int,
    paceNotes: List<PaceNoteEntity>,
): List<ColoredSegment> {
    if (totalPoints < 2) return emptyList()

    val maxIndex = totalPoints - 1

    // Resolve each pace note to a (start, end, color, isFeature) tuple.
    data class Resolved(val start: Int, val end: Int, val color: Color, val isFeature: Boolean)

    val resolved = paceNotes
        .map { note ->
            val start = (note.segmentStartIndex ?: (note.pointIndex - 5).coerceAtLeast(0))
                .coerceIn(0, maxIndex)
            val end = (note.segmentEndIndex ?: (note.pointIndex + 5).coerceAtMost(maxIndex))
                .coerceIn(0, maxIndex)
            Resolved(
                start = start,
                end = end,
                color = PaceNoteIconRenderer.severityColor(note.noteType, note.severity),
                isFeature = note.noteType != NoteType.STRAIGHT,
            )
        }
        .filter { it.end > it.start } // skip degenerate (zero-length or inverted)
        .sortedBy { it.start }

    val segments = mutableListOf<ColoredSegment>()
    var cursor = 0

    for (r in resolved) {
        // Skip if we've already advanced past this segment (overlap)
        if (r.start < cursor) {
            // Partial overlap: trim start to cursor
            if (r.end > cursor) {
                segments.add(ColoredSegment(cursor, r.end, r.color, r.isFeature))
                cursor = r.end
            }
            continue
        }

        // Gap before this segment → fill with BASE_TRACK_COLOR
        if (r.start > cursor) {
            segments.add(ColoredSegment(cursor, r.start, BASE_TRACK_COLOR, isFeature = false))
        }

        // The note's segment
        segments.add(ColoredSegment(r.start, r.end, r.color, r.isFeature))
        cursor = r.end
    }

    // Trailing gap after last note
    if (cursor < maxIndex) {
        segments.add(ColoredSegment(cursor, maxIndex, BASE_TRACK_COLOR, isFeature = false))
    }

    return segments
}
