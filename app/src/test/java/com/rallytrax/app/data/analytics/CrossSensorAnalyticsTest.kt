package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSensorAnalyticsTest {

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun pt(
        index: Int = 0,
        lat: Double = 0.0,
        lon: Double = 0.0,
        speed: Double? = null,
        accelMps2: Double? = null,
        lateralAccelMps2: Double? = null,
        verticalAccelMps2: Double? = null,
        curvatureDegPerM: Double? = null,
        elevation: Double? = null,
    ) = TrackPointEntity(
        trackId = "t1",
        index = index,
        lat = lat,
        lon = lon,
        timestamp = 1_000_000L + index * 1000L,
        speed = speed,
        accelMps2 = accelMps2,
        lateralAccelMps2 = lateralAccelMps2,
        verticalAccelMps2 = verticalAccelMps2,
        curvatureDegPerM = curvatureDegPerM,
        elevation = elevation,
    )

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun `analyze with empty list returns all nulls`() {
        val result = CrossSensorAnalytics.analyze(emptyList())
        assertNull(result.corneringG)
        assertNull(result.smoothnessScore)
        assertNull(result.roadRoughnessIndex)
        assertNull(result.brakingEfficiency)
        assertNull(result.elevationAdjustedAvgSpeed)
    }

    @Test
    fun `analyze with fewer than 3 points returns all nulls`() {
        val points = listOf(pt(0), pt(1))
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.corneringG)
        assertNull(result.smoothnessScore)
        assertNull(result.roadRoughnessIndex)
        assertNull(result.brakingEfficiency)
        assertNull(result.elevationAdjustedAvgSpeed)
    }

    @Test
    fun `analyze with null sensor fields returns null sub-results`() {
        // 3 points with no sensor data at all
        val points = (0..2).map { pt(index = it) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.corneringG)
        assertNull(result.smoothnessScore)
        assertNull(result.roadRoughnessIndex)
        assertNull(result.brakingEfficiency)
        assertNull(result.elevationAdjustedAvgSpeed)
    }

    // ── Cornering G ─────────────────────────────────────────────────────

    @Test
    fun `corneringG uses lateralAccel when available`() {
        val points = (0..4).map { i ->
            pt(
                index = i,
                curvatureDegPerM = 1.0,
                lateralAccelMps2 = 4.905, // 0.5 G
                speed = 20.0,
            )
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.corneringG)
        assertEquals(0.5, result.corneringG!!.peakLateralG, 0.01)
        assertEquals(0.5, result.corneringG!!.avgLateralG, 0.01)
        assertEquals(20.0, result.corneringG!!.peakCornerSpeedMps, 0.01)
    }

    @Test
    fun `corneringG falls back to speed-based calculation when no lateral accel`() {
        val points = (0..4).map { i ->
            pt(
                index = i,
                curvatureDegPerM = 1.0,
                speed = 15.0,
            )
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.corneringG)
        assertTrue(result.corneringG!!.peakLateralG > 0.0)
    }

    @Test
    fun `corneringG returns null when curvature below threshold`() {
        val points = (0..4).map { i ->
            pt(
                index = i,
                curvatureDegPerM = 0.3, // below 0.5 threshold
                lateralAccelMps2 = 4.905,
                speed = 20.0,
            )
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.corneringG)
    }

    @Test
    fun `corneringG picks highest peak lateral G and fastest corner speed`() {
        val points = listOf(
            pt(0, curvatureDegPerM = 1.0, lateralAccelMps2 = 4.905, speed = 10.0),
            pt(1, curvatureDegPerM = 1.0, lateralAccelMps2 = 9.81, speed = 25.0),
            pt(2, curvatureDegPerM = 1.0, lateralAccelMps2 = 2.0, speed = 15.0),
        )
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.corneringG)
        assertEquals(1.0, result.corneringG!!.peakLateralG, 0.01)
        assertEquals(25.0, result.corneringG!!.peakCornerSpeedMps, 0.01)
    }

    // ── Smoothness Score ────────────────────────────────────────────────

    @Test
    fun `smoothness score is high for low variance accelerations`() {
        // Constant acceleration values -> variance near 0 -> score near 100
        val points = (0..19).map { i ->
            pt(
                index = i,
                accelMps2 = 1.0,
                lateralAccelMps2 = 0.5,
            )
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.smoothnessScore)
        assertTrue(
            "Expected high score but got ${result.smoothnessScore}",
            result.smoothnessScore!! >= 90,
        )
    }

    @Test
    fun `smoothness score is low for high variance accelerations`() {
        // Alternating large positive/negative -> high variance -> low score
        val points = (0..19).map { i ->
            val v = if (i % 2 == 0) 5.0 else -5.0
            pt(
                index = i,
                accelMps2 = v,
                lateralAccelMps2 = v,
            )
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.smoothnessScore)
        assertTrue(
            "Expected low score but got ${result.smoothnessScore}",
            result.smoothnessScore!! <= 20,
        )
    }

    @Test
    fun `smoothness returns null when fewer than 10 accel samples`() {
        val points = (0..8).map { i ->
            pt(index = i, accelMps2 = 1.0, lateralAccelMps2 = 0.5)
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.smoothnessScore)
    }

    // ── Road Roughness ──────────────────────────────────────────────────

    @Test
    fun `road roughness is low for smooth vertical accelerations`() {
        val points = (0..19).map { i ->
            pt(index = i, verticalAccelMps2 = 0.1)
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.roadRoughnessIndex)
        assertTrue(
            "Expected low roughness but got ${result.roadRoughnessIndex}",
            result.roadRoughnessIndex!! < 0.5,
        )
    }

    @Test
    fun `road roughness is high for bumpy vertical accelerations`() {
        val points = (0..19).map { i ->
            val v = if (i % 2 == 0) 5.0 else -5.0
            pt(index = i, verticalAccelMps2 = v)
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.roadRoughnessIndex)
        assertTrue(
            "Expected high roughness but got ${result.roadRoughnessIndex}",
            result.roadRoughnessIndex!! > 3.0,
        )
    }

    @Test
    fun `road roughness returns null when fewer than 10 vertical samples`() {
        val points = (0..8).map { i ->
            pt(index = i, verticalAccelMps2 = 1.0)
        }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.roadRoughnessIndex)
    }

    // ── Braking Efficiency ──────────────────────────────────────────────

    @Test
    fun `braking efficiency detects progressive braking as smooth`() {
        // Progressive: peak deceleration in middle-to-late portion
        // Zone: gradually increasing deceleration (peak at end)
        val accelValues = listOf(
            -0.6, -1.0, -1.5, -2.0, -3.0, // progressive zone (peak at index 4, >= size/3=1)
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, // padding to reach 10+
        )
        val points = accelValues.mapIndexed { i, a -> pt(index = i, accelMps2 = a) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.brakingEfficiency)
        assertEquals(1.0, result.brakingEfficiency!!.smoothBrakingRatio, 0.01)
        assertEquals(100, result.brakingEfficiency!!.score)
    }

    @Test
    fun `braking efficiency detects abrupt braking as not smooth`() {
        // Abrupt: peak deceleration at beginning (index 0, < size/3)
        val accelValues = listOf(
            -5.0, -1.0, -0.6, -0.6, -0.6, -0.6, // abrupt zone (peak at 0, < 6/3=2)
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, // padding
        )
        val points = accelValues.mapIndexed { i, a -> pt(index = i, accelMps2 = a) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.brakingEfficiency)
        assertEquals(0.0, result.brakingEfficiency!!.smoothBrakingRatio, 0.01)
        assertEquals(0, result.brakingEfficiency!!.score)
    }

    @Test
    fun `braking efficiency returns null when no braking zones exist`() {
        // All positive accelerations
        val points = (0..14).map { i -> pt(index = i, accelMps2 = 1.0) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.brakingEfficiency)
    }

    @Test
    fun `braking efficiency returns null when fewer than 10 accel samples`() {
        val points = (0..8).map { i -> pt(index = i, accelMps2 = -2.0) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.brakingEfficiency)
    }

    @Test
    fun `braking efficiency tracks max deceleration across zones`() {
        val accelValues = listOf(
            -0.6, -1.0, -2.0, // zone 1, peak 2.0
            0.0,
            -0.6, -1.0, -4.0, // zone 2, peak 4.0
            0.0, 0.0, 0.0, 0.0, 0.0, // padding to 10+
        )
        val points = accelValues.mapIndexed { i, a -> pt(index = i, accelMps2 = a) }
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.brakingEfficiency)
        assertEquals(4.0, result.brakingEfficiency!!.maxDecelerationMps2, 0.01)
    }

    // ── Elevation-Adjusted Speed ────────────────────────────────────────

    @Test
    fun `elevation adjusted speed increases with elevation gain`() {
        // Two points ~111m apart (0.001 degree lat difference at equator)
        val points = listOf(
            pt(0, lat = 0.0, lon = 0.0, speed = 10.0, elevation = 0.0),
            pt(1, lat = 0.001, lon = 0.0, speed = 10.0, elevation = 50.0),
            pt(2, lat = 0.002, lon = 0.0, speed = 10.0, elevation = 100.0),
        )
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.elevationAdjustedAvgSpeed)
        assertTrue(
            "Elevation-adjusted speed should exceed raw average",
            result.elevationAdjustedAvgSpeed!! > 10.0,
        )
    }

    @Test
    fun `elevation adjusted speed equals raw speed on flat terrain`() {
        val points = listOf(
            pt(0, lat = 0.0, lon = 0.0, speed = 10.0, elevation = 100.0),
            pt(1, lat = 0.001, lon = 0.0, speed = 10.0, elevation = 100.0),
            pt(2, lat = 0.002, lon = 0.0, speed = 10.0, elevation = 100.0),
        )
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.elevationAdjustedAvgSpeed)
        assertEquals(10.0, result.elevationAdjustedAvgSpeed!!, 0.01)
    }

    @Test
    fun `elevation adjusted speed returns null when no speed data`() {
        val points = listOf(
            pt(0, lat = 0.0, lon = 0.0, elevation = 0.0),
            pt(1, lat = 0.001, lon = 0.0, elevation = 50.0),
            pt(2, lat = 0.002, lon = 0.0, elevation = 100.0),
        )
        val result = CrossSensorAnalytics.analyze(points)
        assertNull(result.elevationAdjustedAvgSpeed)
    }

    @Test
    fun `elevation adjusted speed ignores descent (only counts gain)`() {
        val points = listOf(
            pt(0, lat = 0.0, lon = 0.0, speed = 10.0, elevation = 100.0),
            pt(1, lat = 0.001, lon = 0.0, speed = 10.0, elevation = 50.0),
            pt(2, lat = 0.002, lon = 0.0, speed = 10.0, elevation = 0.0),
        )
        val result = CrossSensorAnalytics.analyze(points)
        assertNotNull(result.elevationAdjustedAvgSpeed)
        // No elevation gain -> multiplier is 1.0
        assertEquals(10.0, result.elevationAdjustedAvgSpeed!!, 0.01)
    }
}
