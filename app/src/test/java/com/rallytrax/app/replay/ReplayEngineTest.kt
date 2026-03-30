package com.rallytrax.app.replay

import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ReplayEngine].
 *
 * Uses a synthetic straight-line track running due east from (0.0, 0.0).
 * Each point is spaced ~111m apart (0.001 degrees of longitude at the equator),
 * giving a total track length of approximately 11,100m (100 segments).
 */
class ReplayEngineTest {

    private lateinit var trackPoints: List<TrackPointEntity>
    private lateinit var paceNotes: List<PaceNoteEntity>
    private lateinit var engine: ReplayEngine

    /** Approximate metres per 0.001 degree of longitude at the equator. */
    private val metersPerStep = 111.32

    @Before
    fun setUp() {
        // 101 points along the equator, spaced ~111m apart (total ~11,100m)
        trackPoints = (0..100).map { i ->
            TrackPointEntity(
                trackId = "track1",
                index = i,
                lat = 0.0,
                lon = i * 0.001,
                timestamp = 1000L * i,
            )
        }

        // Place pace notes at roughly 2000m, 5000m, and 9000m along the track
        paceNotes = listOf(
            PaceNoteEntity(
                id = "note1",
                trackId = "track1",
                pointIndex = 18,
                distanceFromStart = 2000.0,
                noteType = NoteType.LEFT,
                severity = 3,
                callText = "Left 3",
            ),
            PaceNoteEntity(
                id = "note2",
                trackId = "track1",
                pointIndex = 45,
                distanceFromStart = 5000.0,
                noteType = NoteType.RIGHT,
                severity = 4,
                callText = "Right 4",
            ),
            PaceNoteEntity(
                id = "note3",
                trackId = "track1",
                pointIndex = 81,
                distanceFromStart = 9000.0,
                noteType = NoteType.CREST,
                severity = 5,
                callText = "Crest 5",
            ),
        )

        engine = ReplayEngine(trackPoints, paceNotes, lookaheadSeconds = 6.0)
    }

    // ── Initialization ────────────────────────────────────────────────────

    @Test
    fun `totalDistance is positive for non-empty track`() {
        assertTrue(engine.totalDistance > 0.0)
    }

    @Test
    fun `totalDistance is roughly correct for synthetic track`() {
        // 100 segments of ~111m each => ~11,100m total
        val expected = 100 * metersPerStep
        assertEquals(expected, engine.totalDistance, expected * 0.02) // 2% tolerance
    }

    @Test
    fun `initial state is zeroed`() {
        assertEquals(0.0, engine.currentProgress, 0.001)
        assertEquals(0, engine.closestPointIndex)
        assertFalse(engine.isFinished)
    }

    @Test
    fun `empty track yields zero totalDistance`() {
        val empty = ReplayEngine(emptyList(), emptyList())
        assertEquals(0.0, empty.totalDistance, 0.0)
    }

    // ── Progress fraction ─────────────────────────────────────────────────

    @Test
    fun `update at start returns near-zero progress`() {
        val result = engine.update(0.0, 0.0, speedMps = 10.0)
        assertEquals(0f, result.progressFraction, 0.01f)
        assertFalse(result.isOffRoute)
        assertFalse(result.isFinished)
    }

    @Test
    fun `update at midpoint returns approximately half progress`() {
        val result = engine.update(0.0, 0.050, speedMps = 10.0)
        assertEquals(0.5f, result.progressFraction, 0.05f)
    }

    @Test
    fun `progress increases monotonically along track`() {
        var prev = 0f
        for (i in 0..100 step 10) {
            val result = engine.update(0.0, i * 0.001, speedMps = 10.0)
            assertTrue(
                "Progress should increase: prev=$prev, current=${result.progressFraction}",
                result.progressFraction >= prev,
            )
            prev = result.progressFraction
        }
    }

    // ── Off-route detection ───────────────────────────────────────────────

    @Test
    fun `on-route point is not flagged off-route`() {
        val result = engine.update(0.0, 0.025, speedMps = 10.0)
        assertFalse(result.isOffRoute)
    }

    @Test
    fun `point more than 200m from track is off-route`() {
        // 0.003 degrees latitude at equator is ~333m north of the track
        val result = engine.update(0.003, 0.025, speedMps = 10.0)
        assertTrue(result.isOffRoute)
    }

    @Test
    fun `point just under 200m from track is not off-route`() {
        // 0.0015 degrees latitude ~167m — within threshold
        val result = engine.update(0.0015, 0.025, speedMps = 10.0)
        assertFalse(result.isOffRoute)
    }

    @Test
    fun `off-route suppresses pace note triggering`() {
        // Move close enough to trigger note1 (at 2000m) but far off-route
        val result = engine.update(0.003, 0.018, speedMps = 20.0)
        assertTrue(result.isOffRoute)
        assertNull(result.noteToSpeak)
    }

    // ── Finish detection ──────────────────────────────────────────────────

    @Test
    fun `finish detected when near end and past 50 percent progress`() {
        // First advance past halfway so currentProgress > 50%
        engine.update(0.0, 0.060, speedMps = 10.0)
        // Now move to the last point
        val result = engine.update(0.0, 0.100, speedMps = 10.0)
        assertTrue(result.isFinished)
        assertEquals(1f, result.progressFraction, 0.001f)
    }

    @Test
    fun `finish detected even on first update when snapped to end`() {
        // A fresh engine snapping directly to the last point gets 100% progress,
        // which passes the >50% gate, so finish is correctly detected.
        val freshEngine = ReplayEngine(trackPoints, paceNotes)
        val result = freshEngine.update(0.0, 0.1000, speedMps = 10.0)
        assertTrue(result.isFinished)
    }

    @Test
    fun `isFinished flag persists after finish`() {
        engine.update(0.0, 0.060, speedMps = 10.0)
        engine.update(0.0, 0.100, speedMps = 10.0)
        assertTrue(engine.isFinished)

        // Subsequent updates still report finished
        val result = engine.update(0.0, 0.050, speedMps = 10.0)
        assertTrue(result.isFinished)
    }

    // ── Pace note triggering ──────────────────────────────────────────────

    @Test
    fun `pace note triggers when within pre-call distance`() {
        // At 20 m/s with 6s lookahead, pre-call distance = 120m.
        // Walk toward note1 (at 2000m) until it triggers.
        var triggered = false
        for (i in 15..20) {
            val result = engine.update(0.0, i * 0.001, speedMps = 20.0)
            if (result.noteToSpeak?.id == "note1") {
                triggered = true
                break
            }
        }
        assertTrue("note1 should trigger when approaching 2000m", triggered)
    }

    @Test
    fun `pace note is not triggered twice`() {
        // Walk past note1 to ensure it triggers
        for (i in 0..20) {
            engine.update(0.0, i * 0.001, speedMps = 20.0)
        }

        // Simulate GPS jitter back into the trigger zone
        val result = engine.update(0.0, 0.017, speedMps = 20.0)
        assertTrue(
            "note1 must not re-trigger after already being spoken",
            result.noteToSpeak?.id != "note1",
        )
    }

    @Test
    fun `notes trigger in order along the track`() {
        val triggered = mutableListOf<String>()
        // Walk along the track in steps, collecting triggered notes
        for (i in 0..100) {
            val result = engine.update(0.0, i * 0.001, speedMps = 20.0)
            if (result.noteToSpeak != null) {
                triggered.add(result.noteToSpeak!!.id)
            }
        }
        // All three notes should have triggered in order
        val noteOrder = listOf("note1", "note2", "note3")
        assertEquals(noteOrder, triggered)
    }

    // ── Reset ─────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        // Advance the engine
        engine.update(0.0, 0.050, speedMps = 10.0)
        assertTrue(engine.currentProgress > 0)

        engine.reset()

        assertEquals(0.0, engine.currentProgress, 0.001)
        assertEquals(0, engine.closestPointIndex)
        assertFalse(engine.isFinished)
    }

    @Test
    fun `notes can re-trigger after reset`() {
        // Trigger note1
        for (i in 0..20) {
            engine.update(0.0, i * 0.001, speedMps = 20.0)
        }

        engine.reset()

        // Walk through again — note1 should trigger again
        val triggered = mutableListOf<String>()
        for (i in 0..25) {
            val result = engine.update(0.0, i * 0.001, speedMps = 20.0)
            if (result.noteToSpeak != null) {
                triggered.add(result.noteToSpeak!!.id)
            }
        }
        assertTrue("note1 should re-trigger after reset", "note1" in triggered)
    }

    // ── Snapped position ──────────────────────────────────────────────────

    @Test
    fun `snapped position is on the track`() {
        // Slightly off-track (50m north), but within 200m threshold
        val result = engine.update(0.0005, 0.025, speedMps = 10.0)
        assertNotNull(result.snappedPosition)
        // Snapped lat should be 0.0 (on the equator track)
        assertEquals(0.0, result.snappedPosition!!.latitude, 0.0001)
    }

    // ── Empty track edge case ─────────────────────────────────────────────

    @Test
    fun `update on empty track returns finished with full progress`() {
        val emptyEngine = ReplayEngine(emptyList(), emptyList())
        val result = emptyEngine.update(0.0, 0.0, speedMps = 10.0)
        // Empty track is considered finished (nothing to replay)
        assertEquals(1f, result.progressFraction, 0.001f)
    }
}
