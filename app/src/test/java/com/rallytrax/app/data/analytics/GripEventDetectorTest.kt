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
        rollRateDegPerS: Double? = null,
        timestamp: Long = 1000L * index,
    ) = TrackPointEntity(
        trackId = "test-track",
        index = index,
        lat = lat,
        lon = lon,
        timestamp = timestamp,
        speed = speed,
        curvatureDegPerM = curvatureDegPerM,
        yawRateDegPerS = yawRateDegPerS,
        lateralAccelMps2 = lateralAccelMps2,
        verticalAccelMps2 = verticalAccelMps2,
        accelMps2 = accelMps2,
        rollRateDegPerS = rollRateDegPerS,
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
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 2.0,
                curvatureDegPerM = 2.0,
                yawRateDegPerS = 100.0,
            )
        }
        assertTrue(GripEventDetector.detect(points).isEmpty())
    }

    // ── Steady cornering → no events ──────────────────────────────────

    @Test
    fun detect_steadyCornering_noEvents() {
        val speed = 10.0
        val curvature = 2.0
        val expectedYaw = speed * curvature
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = expectedYaw,
            )
        }
        assertTrue(GripEventDetector.detect(points).isEmpty())
    }

    // ── Oversteer detection ────────────────────────────────────────────

    @Test
    fun detect_yawSpike_detectsOversteer() {
        val speed = 10.0
        val curvature = 2.0
        val expectedYaw = speed * curvature
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = if (i in 3..5) expectedYaw * 2.5 else expectedYaw,
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue("Expected at least one oversteer event", events.isNotEmpty())
        assertTrue(
            "Expected OVERSTEER type",
            events.any { it.type == GripEventDetector.GripEventType.OVERSTEER },
        )
        val oversteer = events.first { it.type == GripEventDetector.GripEventType.OVERSTEER }
        assertEquals(GripEventDetector.Severity.MODERATE, oversteer.severity)
    }

    // ── Understeer detection ───────────────────────────────────────────

    @Test
    fun detect_lowLateralG_detectsUndersteer() {
        val speed = 10.0
        val curvature = 3.0
        val curvatureRad = Math.toRadians(curvature)
        val expectedG = speed * speed * curvatureRad / 9.81
        val lowActualAccel = expectedG * 0.3 * 9.81

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
                accelMps2 = if (i in 3..5) -3.0 else 0.0,
                verticalAccelMps2 = when (i) {
                    2 -> 1.0
                    3 -> 5.0
                    4 -> 5.0
                    else -> 0.5
                },
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue("Expected ABS event", events.any { it.type == GripEventDetector.GripEventType.ABS_ACTIVATION })
    }

    // ── Traction loss detection (new: requires throttle + instability) ─

    @Test
    fun detect_throttleLiftOnExit_noFalsePositive() {
        // This is the key false-positive scenario: driver lifts off throttle exiting a corner.
        // Curvature decreasing, speed flat or slightly decreasing, but accel <= 0 (coasting).
        // Should NOT trigger traction loss.
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 10.0 - i * 0.1, // slight coast-down
                accelMps2 = -0.2, // slight decel = coasting / throttle lift
                curvatureDegPerM = when {
                    i <= 3 -> 5.0
                    i == 4 -> 3.0
                    i == 5 -> 2.0
                    else -> 1.0
                },
            )
        }
        val events = GripEventDetector.detect(points)
        val tractionEvents = events.filter { it.type == GripEventDetector.GripEventType.TRACTION_LOSS }
        assertTrue("Throttle lift should NOT trigger traction loss", tractionEvents.isEmpty())
    }

    @Test
    fun detect_tractionLossWithThrottleAndInstability() {
        // Real traction loss: driver is on throttle exiting corner but speed stalls,
        // with yaw instability present.
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 10.0, // constant — not responding to throttle
                accelMps2 = 1.5, // driver is on throttle
                curvatureDegPerM = when {
                    i <= 3 -> 5.0
                    i == 4 -> 3.0 // exiting: 3.0 < 5.0 * 0.8
                    i == 5 -> 2.0
                    else -> 1.0
                },
                // Instability signals: yaw spike and lateral divergence
                yawRateDegPerS = if (i in 4..5) 25.0 else 5.0,
                lateralAccelMps2 = if (i in 4..5) 5.0 else 1.0,
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue(
            "Expected traction loss event with throttle + instability",
            events.any { it.type == GripEventDetector.GripEventType.TRACTION_LOSS },
        )
    }

    @Test
    fun detect_tractionLossWithThrottleButNoInstability_noEvent() {
        // Driver is on throttle, speed stalls on corner exit, but no instability signals.
        // This could be a false positive (engine just not producing enough power).
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 10.0,
                accelMps2 = 0.8, // on throttle
                curvatureDegPerM = when {
                    i <= 3 -> 5.0
                    i == 4 -> 3.0
                    i == 5 -> 2.0
                    else -> 1.0
                },
                // No instability signals
                yawRateDegPerS = 3.0,
                lateralAccelMps2 = 1.0,
                verticalAccelMps2 = 0.5,
                rollRateDegPerS = 2.0,
            )
        }
        val events = GripEventDetector.detect(points)
        val tractionEvents = events.filter { it.type == GripEventDetector.GripEventType.TRACTION_LOSS }
        assertTrue("No instability = no traction loss event", tractionEvents.isEmpty())
    }

    // ── Wheelspin detection ────────────────────────────────────────────

    @Test
    fun detect_wheelspinOnStraight() {
        // Hard throttle on straight but speed doesn't respond, with vertical vibration
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 12.0, // flat speed
                accelMps2 = 3.0, // hard throttle
                curvatureDegPerM = 0.1, // essentially straight
                verticalAccelMps2 = if (i in 4..6) 4.0 else 0.5, // wheel hop
                timestamp = 1000L * i,
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue(
            "Expected wheelspin event",
            events.any { it.type == GripEventDetector.GripEventType.WHEELSPIN },
        )
    }

    @Test
    fun detect_normalAcceleration_noWheelspin() {
        // Normal acceleration — speed responds to throttle
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = 10.0 + i * 2.0, // speed increasing normally
                accelMps2 = 2.0,
                curvatureDegPerM = 0.1,
                timestamp = 1000L * i,
            )
        }
        val events = GripEventDetector.detect(points)
        val wheelspinEvents = events.filter { it.type == GripEventDetector.GripEventType.WHEELSPIN }
        assertTrue("Normal acceleration should NOT trigger wheelspin", wheelspinEvents.isEmpty())
    }

    // ── Corner entry lock detection ────────────────────────────────────

    @Test
    fun detect_cornerEntryLock() {
        // Heavy braking into a curve, car not rotating (yaw way below expected)
        val speed = 20.0
        val curvature = 2.0
        val expectedYaw = speed * curvature // 40 deg/s
        val points = (0..9).map { i ->
            makePoint(
                index = i,
                speed = speed,
                accelMps2 = if (i in 3..5) -4.0 else 0.0, // heavy braking
                curvatureDegPerM = curvature,
                yawRateDegPerS = if (i in 3..5) expectedYaw * 0.3 else expectedYaw, // only 30% rotation
            )
        }
        val events = GripEventDetector.detect(points)
        assertTrue(
            "Expected corner entry lock event",
            events.any { it.type == GripEventDetector.GripEventType.CORNER_ENTRY_LOCK },
        )
    }

    // ── Deduplication ──────────────────────────────────────────────────

    @Test
    fun detect_deduplicatesNearbyEvents() {
        val speed = 10.0
        val curvature = 2.0
        val expectedYaw = speed * curvature
        val points = (0..12).map { i ->
            makePoint(
                index = i,
                speed = speed,
                curvatureDegPerM = curvature,
                yawRateDegPerS = if (i in 3..6) expectedYaw * 3.5 else expectedYaw,
            )
        }
        val events = GripEventDetector.detect(points)
        val oversteerEvents = events.filter { it.type == GripEventDetector.GripEventType.OVERSTEER }
        assertEquals("Expected deduplication to 1 event", 1, oversteerEvents.size)
        assertEquals(GripEventDetector.Severity.SEVERE, oversteerEvents.first().severity)
    }
}
