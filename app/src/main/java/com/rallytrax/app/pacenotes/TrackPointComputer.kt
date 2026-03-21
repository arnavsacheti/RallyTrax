package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes accelMps2 and curvatureDegPerM for a list of TrackPoints.
 * Uses a 3-point moving average on speed before computing acceleration
 * to reduce GPS noise (per spec risk mitigation).
 */
object TrackPointComputer {

    /**
     * Returns a new list of TrackPointEntity with accelMps2 and curvatureDegPerM populated.
     */
    fun computeFields(points: List<TrackPointEntity>): List<TrackPointEntity> {
        if (points.size < 3) return points

        val distances = computeCumulativeDistances(points)
        val bearings = computeBearings(points)
        val smoothedSpeeds = smoothSpeeds(points, windowSize = 3)

        return points.mapIndexed { i, pt ->
            val accel = computeAcceleration(i, points, smoothedSpeeds)
            val curvature = computeCurvature(i, bearings, distances)
            pt.copy(accelMps2 = accel, curvatureDegPerM = curvature)
        }
    }

    private fun computeAcceleration(
        index: Int,
        points: List<TrackPointEntity>,
        smoothedSpeeds: DoubleArray,
    ): Double? {
        if (index == 0 || index == points.lastIndex) return null
        val dt = (points[index + 1].timestamp - points[index - 1].timestamp) / 1000.0
        if (dt <= 0) return null
        val dv = smoothedSpeeds[index + 1] - smoothedSpeeds[index - 1]
        return dv / dt
    }

    private fun computeCurvature(
        index: Int,
        bearings: DoubleArray,
        distances: DoubleArray,
    ): Double? {
        if (index == 0 || index >= bearings.size - 1) return null
        val dd = distances[index + 1] - distances[index - 1]
        if (dd < 1.0) return null // avoid division by near-zero
        val bearingDelta = normalizeDelta(bearings[index] - bearings[index - 1])
        val segmentDist = distances[index] - distances[index - 1]
        if (segmentDist < 0.5) return null
        return abs(bearingDelta) / segmentDist
    }

    private fun smoothSpeeds(points: List<TrackPointEntity>, windowSize: Int): DoubleArray {
        val raw = DoubleArray(points.size) { points[it].speed ?: 0.0 }
        val smoothed = DoubleArray(points.size)
        val half = windowSize / 2
        for (i in raw.indices) {
            var sum = 0.0
            var count = 0
            for (j in (i - half)..(i + half)) {
                if (j in raw.indices) {
                    sum += raw[j]
                    count++
                }
            }
            smoothed[i] = if (count > 0) sum / count else 0.0
        }
        return smoothed
    }

    private fun computeCumulativeDistances(points: List<TrackPointEntity>): DoubleArray {
        val distances = DoubleArray(points.size)
        for (i in 1 until points.size) {
            distances[i] = distances[i - 1] + haversine(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon,
            )
        }
        return distances
    }

    private fun computeBearings(points: List<TrackPointEntity>): DoubleArray {
        val bearings = DoubleArray(points.size)
        for (i in 0 until points.size - 1) {
            bearings[i] = bearing(
                points[i].lat, points[i].lon,
                points[i + 1].lat, points[i + 1].lon,
            )
        }
        if (points.size >= 2) {
            bearings[points.lastIndex] = bearings[points.lastIndex - 1]
        }
        return bearings
    }

    private fun normalizeDelta(delta: Double): Double {
        var d = delta % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
