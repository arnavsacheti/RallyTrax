package com.rallytrax.app

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun trackEntity_defaultValues_areCorrect() {
        val track = TrackEntity(
            name = "Test Track",
            recordedAt = System.currentTimeMillis(),
        )
        assertNotNull(track.id)
        assertEquals("Test Track", track.name)
        assertNull(track.description)
        assertEquals(0L, track.durationMs)
        assertEquals(0.0, track.distanceMeters, 0.001)
        assertEquals(0.0, track.maxSpeedMps, 0.001)
        assertEquals(0.0, track.avgSpeedMps, 0.001)
        assertEquals(0.0, track.elevationGainM, 0.001)
        assertEquals("", track.tags)
        assertEquals("", track.gpxFilePath)
        assertNull(track.thumbnailPath)
    }

    @Test
    fun trackPointEntity_creation_isCorrect() {
        val point = TrackPointEntity(
            trackId = "test-id",
            index = 0,
            lat = 37.7749,
            lon = -122.4194,
            elevation = 10.0,
            timestamp = System.currentTimeMillis(),
            speed = 15.0,
            bearing = 180.0,
            accuracy = 5.0f,
        )
        assertEquals("test-id", point.trackId)
        assertEquals(0, point.index)
        assertEquals(37.7749, point.lat, 0.0001)
        assertEquals(-122.4194, point.lon, 0.0001)
        assertEquals(10.0, point.elevation)
        assertEquals(15.0, point.speed)
        assertEquals(180.0, point.bearing)
        assertEquals(5.0f, point.accuracy)
    }
}
