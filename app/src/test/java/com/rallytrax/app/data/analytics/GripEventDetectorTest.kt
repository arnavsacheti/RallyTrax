package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GripEventDetectorTest {

    private fun makePoint(
        index: Int,
        lat: Double = 37.0 + index * 0.0001,
        lon: Double = -122.0,
        speed: Double? = 15.0,
        curvatureDegPerM: Double? = null,
        yawRateDegPerS: Double? = null,
        lateralAccelMps2: Double? = null,
        verticalAccelMps2: Double? = null,
        accelMps2: Double? = null,
    ) = TrackPointEntity(
        trackId = "test-track",
        index = index,
        lat = lat,
        lon = lon,
        timestamp = 1000L * index,
        speed = speed,
        curvatureDegPerM = curvatureDegPerM,
        yawRateDegPerS = yawRateDegPerS,
        lateralAccelMps2 = lateralAccelMps2,
        verticalAccelMps2 = verticalAccelMps2,
        accelMps2 = accelMps2,
    )

    // ── Empty / too few points ─────────────────────────────────────────

    @Test
    fun detect_emptyPoints_returnsEmpty() {
        assertEquals(emptyList<GripEventDetector.GripEvent>(), GripEventDetector.detect(emptyList()))
    }

    @Test
    fun detect_fewerThan5Points_returnsEmpty() {
        val points = (0..3).map { makePoint(it) }
        assertEquals(emptyList<GripEventDetector.GripEvent>(), GripEventDetector.detect(points))
    }

    // ── Low speed skip ─────────────────────────────────────────────────

    @Test
    fun detect_lowSpeed_skipsAnalysis() {
        // Points with high yaw ratio but speed below 3 m/s should be ignored
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 2.0, // below threshold
                curvatureDegPerM = 2.0,
                yawRateDegPerS = 100.0, // very high yaw that would trigger oversteer at normal speed
            )
        }
        assertTrue(GripEventDetector.detect(points).isEmpty())
    }

    // ── Steady cornering → no events ──────────────────────────────────

    @Test
    fun detect_steadyCornering_noEvents() {
        // Yaw rate matches expected (ratio ~1.0)
        val speed = 10.0 // m/s
        val curvature = 2.0 // deg/m
        val expectedYaw = speed * curvature // 20 deg/s
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = expectedYaw, // ratio = 1.0
            )
        }
        assertTrue(GripEventDetector.detect(points).isEmpty())
    }

    // ── Oversteer detection ────────────────────────────────────────────

    @Test
    fun detect_yawSpike_detectsOversteer() {
        val speed = 10.0
        val curvature = 2.0
        val expectedYaw = speed * curvature // 20 deg/s
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = if (i in 3..5) expectedYaw * 2.5 else expectedYaw, // 2.5x → MODERATE
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue("Expected at least one oversteer event", events.isNotEmpty())
        assertTrue(
            "Expected OVERSTEER type",
            events.any { it.type == GripEventDetector.GripEventType.OVERSTEER },
        )
        // 2.5x ratio → MODERATE
        val oversteer = events.first { it.type == GripEventDetector.GripEventType.OVERSTEER }
        assertEquals(GripEventDetector.Severity.MODERATE, oversteer.severity)
    }

    // ── Understeer detection ───────────────────────────────────────────

    @Test
    fun detect_lowLateralG_detectsUndersteer() {
        val speed = 10.0
        val curvature = 3.0 // deg/m (> 1.0 threshold)
        // expected lateral G = speed^2 * toRadians(curvature) / 9.81
        val curvatureRad = Math.toRadians(curvature)
        val expectedG = speed * speed * curvatureRad / 9.81
        val lowActualAccel = expectedG * 0.3 * 9.81 // 30% of expected (< 50% threshold)

        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                lateralAccelMps2 = if (i in 3..5) lowActualAccel else expectedG * 9.81,
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue("Expected understeer event", events.any { it.type == GripEventDetector.GripEventType.UNDERSTEER })
    }

    // ── ABS detection ──────────────────────────────────────────────────

    @Test
    fun detect_verticalGSpikeDuringBraking_detectsABS() {
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 15.0,
                accelMps2 = if (i in 3..5) -3.0 else 0.0, // braking
                verticalAccelMps2 = when (i) {
                    2 -> 1.0 // prev: low vertical G (1.0/9.81 ≈ 0.10)
                    3 -> 5.0 // spike: 5.0/9.81 ≈ 0.51G, > 0.3 threshold, > 0.10*1.5
                    4 -> 5.0
                    else -> 0.5
                },
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue("Expected ABS event", events.any { it.type == GripEventDetector.GripEventType.ABS_ACTIVATION })
    }

    // ── Traction loss detection ────────────────────────────────────────

    @Test
    fun detect_speedStallOnCornerExit_detectsTractionLoss() {
        // Curvature decreasing (exiting corner) but speed stays flat
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 10.0, // constant speed (not increasing)
                curvatureDegPerM = when {
                    i <= 3 -> 5.0 // in corner
                    i == 4 -> 3.0 // exiting: 3.0 < 5.0 * 0.8 = 4.0
                    i == 5 -> 2.0
                    else -> 1.0
                },
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue(
            "Expected traction loss event",
            events.any { it.type == GripEventDetector.GripEventType.TRACTION_LOSS },
        )
    }

    // ── Deduplication ──────────────────────────────────────────────────

    @Test
    fun detect_deduplicatesNearbyEvents() {
        val speed = 10.0
        val curvature = 2.0
        val expectedYaw = speed * curvature
        // Consecutive points with high yaw → should be deduplicated into one event
        val points = (0..12).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = if (i in 3..6) expectedYaw * 3.5 else expectedYaw, // SEVERE
            )
        }
        val events = GripEventDetector.detect(points)
        val oversteerEvents = events.filter { it.type == GripEventDetector.GripEventType.OVERSTEER }
        // Indices 3,4,5,6 are all within 5 of each other → should deduplicate to 1
        assertEquals("Expected deduplication to 1 event", 1, oversteerEvents.size)
        assertEquals(GripEventDetector.Severity.SEVERE, oversteerEvents.first().severity)
    }
}
