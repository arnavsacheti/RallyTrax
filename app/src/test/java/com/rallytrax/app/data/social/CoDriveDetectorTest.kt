package com.rallytrax.app.data.social

import com.rallytrax.app.data.local.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoDriveDetectorTest {

    private val baseTime = 1_000_000_000L // arbitrary epoch millis
    private val oneHourMs = 3_600_000L

    private fun sharedTrack(
        trackId: String = "friend-1",
        recordedAt: Long = baseTime,
        durationMs: Long = 60_000L,
        northLat: Double = 38.0,
        southLat: Double = 37.0,
        eastLon: Double = -121.0,
        westLon: Double = -122.0,
    ) = SharedTrack(
        trackId = trackId,
        ownerUid = "uid-$trackId",
        name = "Friend Track $trackId",
        recordedAt = recordedAt,
        durationMs = durationMs,
        boundingBoxNorthLat = northLat,
        boundingBoxSouthLat = southLat,
        boundingBoxEastLon = eastLon,
        boundingBoxWestLon = westLon,
    )

    private fun localTrack(
        id: String = "local-1",
        recordedAt: Long = baseTime,
        durationMs: Long = 60_000L,
        northLat: Double = 38.0,
        southLat: Double = 37.0,
        eastLon: Double = -121.0,
        westLon: Double = -122.0,
    ) = TrackEntity(
        id = id,
        name = "Local Track $id",
        recordedAt = recordedAt,
        durationMs = durationMs,
        boundingBoxNorthLat = northLat,
        boundingBoxSouthLat = southLat,
        boundingBoxEastLon = eastLon,
        boundingBoxWestLon = westLon,
    )

    @Test
    fun `overlapping bbox and overlapping time produces match`() {
        val friends = listOf(sharedTrack())
        val locals = listOf(localTrack())

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertEquals(1, matches.size)
        assertEquals("friend-1", matches[0].friendTrack.trackId)
        assertEquals("local-1", matches[0].localTrackId)
        assertEquals("Local Track local-1", matches[0].localTrackName)
    }

    @Test
    fun `overlapping bbox and non-overlapping time produces no match`() {
        val friends = listOf(
            sharedTrack(recordedAt = baseTime + oneHourMs + 60_000L + 2),
        )
        val locals = listOf(localTrack())

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `non-overlapping bbox and overlapping time produces no match`() {
        val friends = listOf(
            sharedTrack(northLat = 50.0, southLat = 49.0, eastLon = 10.0, westLon = 9.0),
        )
        val locals = listOf(localTrack())

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `neither overlapping bbox nor time produces no match`() {
        val friends = listOf(
            sharedTrack(
                recordedAt = baseTime + oneHourMs + 60_000L + 2,
                northLat = 50.0,
                southLat = 49.0,
                eastLon = 10.0,
                westLon = 9.0,
            ),
        )
        val locals = listOf(localTrack())

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `exactly one hour tolerance is a match`() {
        // Friend track ends at baseTime + 60_000. Local starts at baseTime + 60_000 + oneHourMs.
        // friendEnd (baseTime+60000) >= localStart (baseTime+60000+3600000) - tolerance (3600000)
        // baseTime+60000 >= baseTime+60000 => true
        val friends = listOf(sharedTrack(recordedAt = baseTime, durationMs = 60_000L))
        val locals = listOf(localTrack(recordedAt = baseTime + 60_000L + oneHourMs, durationMs = 60_000L))

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertEquals(1, matches.size)
    }

    @Test
    fun `one hour plus one millisecond tolerance is not a match`() {
        // Friend track ends at baseTime + 60_000. Local starts at baseTime + 60_000 + oneHourMs + 1.
        // friendEnd (baseTime+60000) >= localStart (baseTime+60001+3600000) - tolerance (3600000)
        // baseTime+60000 >= baseTime+60001 => false
        val friends = listOf(sharedTrack(recordedAt = baseTime, durationMs = 60_000L))
        val locals = listOf(localTrack(recordedAt = baseTime + 60_000L + oneHourMs + 1, durationMs = 60_000L))

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `empty friend tracks list produces no matches`() {
        val locals = listOf(localTrack())

        val matches = CoDriveDetector.detectCoDrives(emptyList(), locals)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `empty local tracks list produces no matches`() {
        val friends = listOf(sharedTrack())

        val matches = CoDriveDetector.detectCoDrives(friends, emptyList())

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `both lists empty produces no matches`() {
        val matches = CoDriveDetector.detectCoDrives(emptyList(), emptyList())

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `multiple friends matching multiple locals`() {
        val friends = listOf(
            sharedTrack(trackId = "f1", recordedAt = baseTime),
            sharedTrack(trackId = "f2", recordedAt = baseTime + 30_000L),
        )
        val locals = listOf(
            localTrack(id = "l1", recordedAt = baseTime),
            localTrack(id = "l2", recordedAt = baseTime + 10_000L),
        )

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        // Each friend overlaps with each local (same bbox, close times)
        assertEquals(4, matches.size)
        val pairs = matches.map { it.friendTrack.trackId to it.localTrackId }.toSet()
        assertEquals(
            setOf("f1" to "l1", "f1" to "l2", "f2" to "l1", "f2" to "l2"),
            pairs,
        )
    }

    @Test
    fun `partial bbox overlap produces match`() {
        // Friend bbox partially overlaps local bbox
        val friends = listOf(
            sharedTrack(northLat = 37.5, southLat = 36.5, eastLon = -121.5, westLon = -122.5),
        )
        val locals = listOf(
            localTrack(northLat = 38.0, southLat = 37.0, eastLon = -121.0, westLon = -122.0),
        )

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertEquals(1, matches.size)
    }

    @Test
    fun `bbox touching at edge produces match`() {
        // Friend's north == local's south (touching, not separated)
        val friends = listOf(
            sharedTrack(northLat = 37.0, southLat = 36.0),
        )
        val locals = listOf(
            localTrack(northLat = 38.0, southLat = 37.0),
        )

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertEquals(1, matches.size)
    }

    @Test
    fun `bbox just separated produces no match`() {
        // Friend's north is just below local's south
        val friends = listOf(
            sharedTrack(northLat = 36.999, southLat = 36.0),
        )
        val locals = listOf(
            localTrack(northLat = 38.0, southLat = 37.0),
        )

        val matches = CoDriveDetector.detectCoDrives(friends, locals)

        assertTrue(matches.isEmpty())
    }
}
