package com.rallytrax.app.ui.map

import androidx.compose.ui.graphics.Color
import com.rallytrax.app.data.local.entity.Conjunction
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.SeverityHalf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoredSegmentBuilderTest {

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Minimal PaceNoteEntity factory for tests. */
    private fun paceNote(
        noteType: NoteType = NoteType.STRAIGHT,
        severity: Int = 6,
        pointIndex: Int = 50,
        segmentStartIndex: Int? = null,
        segmentEndIndex: Int? = null,
        trackId: String = "track-1",
    ) = PaceNoteEntity(
        id = "note-${noteType.name}-$pointIndex",
        trackId = trackId,
        pointIndex = pointIndex,
        distanceFromStart = pointIndex * 10.0,
        noteType = noteType,
        severity = severity,
        modifier = NoteModifier.NONE,
        callText = noteType.name,
        callDistanceM = 0.0,
        severityHalf = SeverityHalf.NONE,
        conjunction = Conjunction.DISTANCE,
        segmentStartIndex = segmentStartIndex,
        segmentEndIndex = segmentEndIndex,
    )

    private val STRAIGHT_COLOR = PaceNoteIconRenderer.severityColor(NoteType.STRAIGHT, 6)

    // ── Empty / degenerate ───────────────────────────────────────────────

    @Test
    fun `empty pace notes - single BASE_TRACK_COLOR segment`() {
        val result = buildColoredSegments(100, emptyList())
        assertEquals(1, result.size)
        assertEquals(0, result[0].startIndex)
        assertEquals(99, result[0].endIndex)
        assertEquals(BASE_TRACK_COLOR, result[0].color)
        assertFalse(result[0].isFeature)
    }

    @Test
    fun `totalPoints 0 - returns empty list`() {
        val result = buildColoredSegments(0, listOf(paceNote()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `totalPoints 1 - returns empty list`() {
        val result = buildColoredSegments(1, listOf(paceNote()))
        assertTrue(result.isEmpty())
    }

    // ── All STRAIGHTs with segment indices ───────────────────────────────

    @Test
    fun `all-STRAIGHT notes with segment indices - continuous colored coverage`() {
        val notes = listOf(
            paceNote(NoteType.STRAIGHT, pointIndex = 10, segmentStartIndex = 0, segmentEndIndex = 30),
            paceNote(NoteType.STRAIGHT, pointIndex = 50, segmentStartIndex = 30, segmentEndIndex = 60),
            paceNote(NoteType.STRAIGHT, pointIndex = 80, segmentStartIndex = 60, segmentEndIndex = 99),
        )
        val result = buildColoredSegments(100, notes)

        // No BASE_TRACK_COLOR gaps in the covered range
        val baseSegments = result.filter { it.color == BASE_TRACK_COLOR }
        assertTrue("Expected no BASE_TRACK_COLOR segments but found ${baseSegments.size}", baseSegments.isEmpty())

        // All segments are STRAIGHT colored and not features
        result.forEach { seg ->
            assertEquals(STRAIGHT_COLOR, seg.color)
            assertFalse(seg.isFeature)
        }

        // Continuous: first starts at 0, last ends at 99
        assertEquals(0, result.first().startIndex)
        assertEquals(99, result.last().endIndex)
    }

    // ── Mixed turns + straights ──────────────────────────────────────────

    @Test
    fun `mixed turns and straights - correct isFeature flags and colors`() {
        val notes = listOf(
            paceNote(NoteType.STRAIGHT, pointIndex = 15, segmentStartIndex = 0, segmentEndIndex = 20),
            paceNote(NoteType.LEFT, severity = 3, pointIndex = 25, segmentStartIndex = 20, segmentEndIndex = 40),
            paceNote(NoteType.STRAIGHT, pointIndex = 50, segmentStartIndex = 40, segmentEndIndex = 60),
            paceNote(NoteType.HAIRPIN_RIGHT, severity = 1, pointIndex = 70, segmentStartIndex = 60, segmentEndIndex = 80),
            paceNote(NoteType.STRAIGHT, pointIndex = 90, segmentStartIndex = 80, segmentEndIndex = 99),
        )
        val result = buildColoredSegments(100, notes)

        // No base-color gaps — full coverage 0..99
        val baseSegments = result.filter { it.color == BASE_TRACK_COLOR }
        assertTrue("Expected no BASE_TRACK_COLOR segments", baseSegments.isEmpty())

        // Check isFeature: STRAIGHTs → false, turns → true
        val straight1 = result.first { it.startIndex == 0 }
        assertFalse(straight1.isFeature)
        assertEquals(STRAIGHT_COLOR, straight1.color)

        val left = result.first { it.startIndex == 20 }
        assertTrue(left.isFeature)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.LEFT, 3), left.color)

        val hairpin = result.first { it.startIndex == 60 }
        assertTrue(hairpin.isFeature)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.HAIRPIN_RIGHT, 1), hairpin.color)
    }

    // ── Null segment indices → ±5 fallback ───────────────────────────────

    @Test
    fun `null segment indices - fallback to plus-minus 5 with base-color gaps`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 4, pointIndex = 20),
            paceNote(NoteType.RIGHT, severity = 3, pointIndex = 60),
        )
        val result = buildColoredSegments(100, notes)

        // Left note: start=15, end=25
        val leftSeg = result.first { it.isFeature && it.startIndex == 15 }
        assertEquals(25, leftSeg.endIndex)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.LEFT, 4), leftSeg.color)

        // Right note: start=55, end=65
        val rightSeg = result.first { it.isFeature && it.startIndex == 55 }
        assertEquals(65, rightSeg.endIndex)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.RIGHT, 3), rightSeg.color)

        // There should be BASE_TRACK_COLOR gaps: 0..15, 25..55, 65..99
        val gaps = result.filter { it.color == BASE_TRACK_COLOR }
        assertEquals(3, gaps.size)
        assertEquals(0, gaps[0].startIndex)
        assertEquals(15, gaps[0].endIndex)
        assertEquals(25, gaps[1].startIndex)
        assertEquals(55, gaps[1].endIndex)
        assertEquals(65, gaps[2].startIndex)
        assertEquals(99, gaps[2].endIndex)
    }

    // ── Full-coverage invariant ──────────────────────────────────────────

    @Test
    fun `full coverage with segment indices - no BASE_TRACK_COLOR segments`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 2, pointIndex = 20, segmentStartIndex = 0, segmentEndIndex = 50),
            paceNote(NoteType.STRAIGHT, pointIndex = 75, segmentStartIndex = 50, segmentEndIndex = 99),
        )
        val result = buildColoredSegments(100, notes)

        val baseSegments = result.filter { it.color == BASE_TRACK_COLOR }
        assertTrue("Expected no base-color gaps when notes cover full range", baseSegments.isEmpty())

        assertEquals(0, result.first().startIndex)
        assertEquals(99, result.last().endIndex)
    }

    // ── Overlapping segment indices ──────────────────────────────────────

    @Test
    fun `overlapping segment indices - cursor advances correctly, no duplicate coverage`() {
        // Use severity 1 (DifficultyRed) and severity 6 (DifficultyGreen) so colors differ
        val leftColor = PaceNoteIconRenderer.severityColor(NoteType.LEFT, 1)
        val rightColor = PaceNoteIconRenderer.severityColor(NoteType.RIGHT, 6)

        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 1, pointIndex = 20, segmentStartIndex = 10, segmentEndIndex = 50),
            paceNote(NoteType.RIGHT, severity = 6, pointIndex = 35, segmentStartIndex = 30, segmentEndIndex = 60),
            paceNote(NoteType.STRAIGHT, pointIndex = 80, segmentStartIndex = 60, segmentEndIndex = 90),
        )
        val result = buildColoredSegments(100, notes)

        // No point index should be covered by two segments (non-overlapping)
        for (i in result.indices) {
            for (j in i + 1 until result.size) {
                assertTrue(
                    "Segments $i and $j overlap: ${result[i]} vs ${result[j]}",
                    result[i].endIndex <= result[j].startIndex,
                )
            }
        }

        // Left turn: 10..50 (full), then Right overlaps → trimmed to 50..60
        val leftSeg = result.first { it.color == leftColor }
        assertEquals(10, leftSeg.startIndex)
        assertEquals(50, leftSeg.endIndex)

        val rightSeg = result.first { it.color == rightColor }
        assertEquals(50, rightSeg.startIndex)
        assertEquals(60, rightSeg.endIndex)
    }

    // ── Boundary: degenerate segment indices ─────────────────────────────

    @Test
    fun `segmentEndIndex less than or equal to segmentStartIndex - note skipped`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 3, pointIndex = 20, segmentStartIndex = 30, segmentEndIndex = 30),
            paceNote(NoteType.RIGHT, severity = 3, pointIndex = 40, segmentStartIndex = 50, segmentEndIndex = 40),
        )
        val result = buildColoredSegments(100, notes)

        // Both notes are degenerate → filtered out → single BASE_TRACK_COLOR segment
        assertEquals(1, result.size)
        assertEquals(BASE_TRACK_COLOR, result[0].color)
    }

    // ── Boundary: segment indices exceeding totalPoints ──────────────────

    @Test
    fun `segment indices exceeding totalPoints - clamped to valid range`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 3, pointIndex = 10, segmentStartIndex = -10, segmentEndIndex = 200),
        )
        val result = buildColoredSegments(50, notes)

        // Should be clamped to 0..49
        val feature = result.first { it.isFeature }
        assertEquals(0, feature.startIndex)
        assertEquals(49, feature.endIndex)
    }

    // ── Single note covering entire track ────────────────────────────────

    @Test
    fun `single note covering entire track - one feature segment`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 5, pointIndex = 50, segmentStartIndex = 0, segmentEndIndex = 99),
        )
        val result = buildColoredSegments(100, notes)

        assertEquals(1, result.size)
        assertTrue(result[0].isFeature)
        assertEquals(0, result[0].startIndex)
        assertEquals(99, result[0].endIndex)
    }

    // ── Crest and dip note types ─────────────────────────────────────────

    @Test
    fun `crest and dip notes are features with correct colors`() {
        val notes = listOf(
            paceNote(NoteType.CREST, severity = 3, pointIndex = 20, segmentStartIndex = 10, segmentEndIndex = 30),
            paceNote(NoteType.BIG_DIP, severity = 2, pointIndex = 50, segmentStartIndex = 40, segmentEndIndex = 60),
        )
        val result = buildColoredSegments(100, notes)

        val crest = result.first { it.startIndex == 10 }
        assertTrue(crest.isFeature)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.CREST, 3), crest.color)

        val dip = result.first { it.startIndex == 40 }
        assertTrue(dip.isFeature)
        assertEquals(PaceNoteIconRenderer.severityColor(NoteType.BIG_DIP, 2), dip.color)
    }

    // ── Edge: null index near track boundaries ───────────────────────────

    @Test
    fun `null indices near start - clamped to 0`() {
        val notes = listOf(
            paceNote(NoteType.LEFT, severity = 3, pointIndex = 2), // fallback: max(0, 2-5)=0, min(49, 2+5)=7
        )
        val result = buildColoredSegments(50, notes)

        val feature = result.first { it.isFeature }
        assertEquals(0, feature.startIndex)
        assertEquals(7, feature.endIndex)
    }

    @Test
    fun `null indices near end - clamped to totalPoints-1`() {
        val notes = listOf(
            paceNote(NoteType.RIGHT, severity = 4, pointIndex = 47), // fallback: 42..49
        )
        val result = buildColoredSegments(50, notes)

        val feature = result.first { it.isFeature }
        assertEquals(42, feature.startIndex)
        assertEquals(49, feature.endIndex)
    }

    // ── Continuity check ─────────────────────────────────────────────────

    @Test
    fun `segments are contiguous - no gaps in index coverage`() {
        val notes = listOf(
            paceNote(NoteType.STRAIGHT, pointIndex = 15, segmentStartIndex = 5, segmentEndIndex = 25),
            paceNote(NoteType.LEFT, severity = 3, pointIndex = 35, segmentStartIndex = 25, segmentEndIndex = 45),
            paceNote(NoteType.STRAIGHT, pointIndex = 55, segmentStartIndex = 45, segmentEndIndex = 65),
            paceNote(NoteType.CREST, severity = 5, pointIndex = 75, segmentStartIndex = 65, segmentEndIndex = 85),
        )
        val result = buildColoredSegments(100, notes)

        // Verify contiguous: each segment's start == previous segment's end
        for (i in 1 until result.size) {
            assertEquals(
                "Gap between segment ${i - 1} (end=${result[i - 1].endIndex}) and segment $i (start=${result[i].startIndex})",
                result[i - 1].endIndex,
                result[i].startIndex,
            )
        }

        // First starts at 0, last ends at 99
        assertEquals(0, result.first().startIndex)
        assertEquals(99, result.last().endIndex)
    }

    // ── Unsorted input ───────────────────────────────────────────────────

    @Test
    fun `unsorted notes - sorted by resolved start index`() {
        val notes = listOf(
            paceNote(NoteType.RIGHT, severity = 3, pointIndex = 70, segmentStartIndex = 60, segmentEndIndex = 80),
            paceNote(NoteType.LEFT, severity = 4, pointIndex = 20, segmentStartIndex = 10, segmentEndIndex = 30),
        )
        val result = buildColoredSegments(100, notes)

        // Left turn should come first despite being second in the list
        val features = result.filter { it.isFeature }
        assertEquals(2, features.size)
        assertEquals(10, features[0].startIndex)
        assertEquals(60, features[1].startIndex)
    }
}
