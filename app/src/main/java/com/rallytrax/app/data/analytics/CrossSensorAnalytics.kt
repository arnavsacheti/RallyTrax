package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Combines GPS + accelerometer + gyroscope + barometer data into
 * meaningful driving-insight hero stats for a single track.
 */
object CrossSensorAnalytics {

    data class TrackInsights(
        val corneringG: CorneringStats?,
        val smoothnessScore: Int?,
        val roadRoughnessIndex: Double?,
        val brakingEfficiency: BrakingStats?,
        val elevationAdjustedAvgSpeed: Double?,
    )

    data class CorneringStats(
        val peakLateralG: Double,
        val avgLateralG: Double,
        val peakCornerSpeedMps: Double,
    )

    data class BrakingStats(
        val avgDecelerationMps2: Double,
        val maxDecelerationMps2: Double,
        val smoothBrakingRatio: Double,
        val score: Int,
    )

    fun analyze(points: List<TrackPointEntity>): TrackInsights {
        if (points.size < 3) return TrackInsights(null, null, null, null, null)

        return TrackInsights(
            corneringG = computeCorneringG(points),
            smoothnessScore = computeSmoothness(points),
            roadRoughnessIndex = computeRoadRoughness(points),
            brakingEfficiency = computeBrakingEfficiency(points),
            elevationAdjustedAvgSpeed = computeElevationAdjustedSpeed(points),
        )
    }

    // ── Cornering G ─────────────────────────────────────────────────────

    private fun computeCorneringG(points: List<TrackPointEntity>): CorneringStats? {
        var peakG = 0.0
        var sumG = 0.0
        var count = 0
        var peakCornerSpeed = 0.0

        for (pt in points) {
            val curvature = pt.curvatureDegPerM ?: continue
            if (abs(curvature) < 0.5) continue

            val g = if (pt.lateralAccelMps2 != null) {
                abs(pt.lateralAccelMps2) / 9.81
            } else {
                val speed = pt.speed ?: continue
                val curvatureRadPerM = Math.toRadians(abs(curvature))
                speed * speed * curvatureRadPerM / 9.81
            }

            if (g > peakG) peakG = g
            sumG += g
            count++

            val speed = pt.speed ?: 0.0
            if (speed > peakCornerSpeed) peakCornerSpeed = speed
        }

        if (count == 0) return null
        return CorneringStats(
            peakLateralG = peakG,
            avgLateralG = sumG / count,
            peakCornerSpeedMps = peakCornerSpeed,
        )
    }

    // ── Smoothness Score ────────────────────────────────────────────────

    private fun computeSmoothness(points: List<TrackPointEntity>): Int? {
        val longitudinal = points.mapNotNull { it.accelMps2 }
        val lateral = points.mapNotNull { it.lateralAccelMps2 }
        val braking = longitudinal.filter { it < -0.5 }

        if (longitudinal.size < 10 && lateral.size < 10) return null

        val longScore = if (longitudinal.size >= 10) normaliseVariance(variance(longitudinal)) else null
        val latScore = if (lateral.size >= 10) normaliseVariance(variance(lateral)) else null
        val brakeScore = if (braking.size >= 5) normaliseVariance(variance(braking)) else null

        val weights = mutableListOf<Pair<Double, Int>>()
        if (longScore != null) weights += 0.4 to longScore
        if (latScore != null) weights += 0.4 to latScore
        if (brakeScore != null) weights += 0.2 to brakeScore

        if (weights.isEmpty()) return null

        val totalWeight = weights.sumOf { it.first }
        val weighted = weights.sumOf { it.first * it.second } / totalWeight
        return weighted.toInt().coerceIn(0, 100)
    }

    private fun normaliseVariance(v: Double): Int {
        // variance < 1.0 → 100, > 5.0 → 0, linear between
        return ((1.0 - ((v - 1.0) / 4.0).coerceIn(0.0, 1.0)) * 100).toInt()
    }

    private fun variance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean).pow(2) } / (values.size - 1)
    }

    // ── Road Roughness ──────────────────────────────────────────────────

    private fun computeRoadRoughness(points: List<TrackPointEntity>): Double? {
        val verticals = points.mapNotNull { it.verticalAccelMps2 }
        if (verticals.size < 10) return null
        return sqrt(variance(verticals))
    }

    // ── Braking Efficiency ──────────────────────────────────────────────

    private fun computeBrakingEfficiency(points: List<TrackPointEntity>): BrakingStats? {
        val accelValues = points.mapNotNull { it.accelMps2 }
        if (accelValues.size < 10) return null

        // Identify braking zones: consecutive points with accelMps2 < -0.5
        data class BrakingZone(val values: List<Double>)

        val zones = mutableListOf<BrakingZone>()
        var current = mutableListOf<Double>()

        for (a in accelValues) {
            if (a < -0.5) {
                current.add(a)
            } else if (current.isNotEmpty()) {
                if (current.size >= 3) zones.add(BrakingZone(current.toList()))
                current = mutableListOf()
            }
        }
        if (current.size >= 3) zones.add(BrakingZone(current.toList()))

        if (zones.isEmpty()) return null

        var smoothCount = 0
        var totalDecel = 0.0
        var maxDecel = 0.0

        for (zone in zones) {
            val absVals = zone.values.map { abs(it) }
            val peak = absVals.max()
            if (peak > maxDecel) maxDecel = peak

            totalDecel += absVals.average()

            // Progressive: peak deceleration is in the middle-to-late portion
            val peakIdx = absVals.indexOf(peak)
            val isProgressive = peakIdx >= absVals.size / 3
            if (isProgressive) smoothCount++
        }

        val ratio = smoothCount.toDouble() / zones.size
        return BrakingStats(
            avgDecelerationMps2 = totalDecel / zones.size,
            maxDecelerationMps2 = maxDecel,
            smoothBrakingRatio = ratio,
            score = (ratio * 100).toInt().coerceIn(0, 100),
        )
    }

    // ── Elevation-Adjusted Speed ────────────────────────────────────────

    private fun computeElevationAdjustedSpeed(points: List<TrackPointEntity>): Double? {
        if (points.size < 2) return null

        val speeds = points.mapNotNull { it.speed }
        if (speeds.isEmpty()) return null
        val avgSpeed = speeds.average()

        var totalDistance = 0.0
        var elevationGain = 0.0

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += haversineM(prev.lat, prev.lon, curr.lat, curr.lon)

            val prevElev = prev.elevation ?: continue
            val currElev = curr.elevation ?: continue
            val delta = currElev - prevElev
            if (delta > 0) elevationGain += delta
        }

        if (totalDistance < 1.0) return null
        return avgSpeed * (1.0 + elevationGain / totalDistance)
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
}
