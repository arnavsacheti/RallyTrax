package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects grip loss and traction events from phone sensor data
 * (lateral accel, vertical accel, yaw rate, roll rate, speed, curvature).
 */
object GripEventDetector {

    enum class GripEventType { OVERSTEER, UNDERSTEER, ABS_ACTIVATION, TRACTION_LOSS }
    enum class Severity { MILD, MODERATE, SEVERE }

    data class GripEvent(
        val pointIndex: Int,
        val distanceFromStart: Double,
        val type: GripEventType,
        val severity: Severity,
        val description: String,
    )

    fun detect(points: List<TrackPointEntity>): List<GripEvent> {
        if (points.size < 5) return emptyList()
        val events = mutableListOf<GripEvent>()
        var cumulativeDistance = 0.0

        for (i in 2 until points.size - 2) {
            val prev = points[i - 1]
            val pt = points[i]
            val next = points[i + 1]

            // Compute cumulative distance
            cumulativeDistance += haversine(prev.lat, prev.lon, pt.lat, pt.lon)

            val speed = pt.speed ?: continue
            if (speed < 3.0) continue // Skip low-speed points

            // 1. OVERSTEER: actual yaw rate >> expected yaw rate
            val curvature = pt.curvatureDegPerM
            val actualYaw = pt.yawRateDegPerS
            if (curvature != null && actualYaw != null && abs(curvature) > 0.5) {
                val expectedYawDegPerS = speed * abs(curvature) // speed (m/s) * curvature (deg/m) = deg/s
                if (expectedYawDegPerS > 5.0) { // minimum threshold
                    val ratio = abs(actualYaw) / expectedYawDegPerS
                    if (ratio > 1.5) {
                        val severity = when {
                            ratio > 3.0 -> Severity.SEVERE
                            ratio > 2.0 -> Severity.MODERATE
                            else -> Severity.MILD
                        }
                        events.add(
                            GripEvent(
                                i, cumulativeDistance, GripEventType.OVERSTEER, severity,
                                "Oversteer: yaw rate ${String.format("%.0f", abs(actualYaw))}\u00B0/s vs expected ${String.format("%.0f", expectedYawDegPerS)}\u00B0/s",
                            ),
                        )
                    }
                }
            }

            // 2. UNDERSTEER: actual lateral G << expected lateral G
            val lateralAccel = pt.lateralAccelMps2
            if (curvature != null && lateralAccel != null && abs(curvature) > 1.0 && speed > 5.0) {
                val curvatureRad = Math.toRadians(abs(curvature))
                val expectedLateralG = speed * speed * curvatureRad / 9.81
                val actualLateralG = abs(lateralAccel) / 9.81
                if (expectedLateralG > 0.2 && actualLateralG < expectedLateralG * 0.5) {
                    events.add(
                        GripEvent(
                            i, cumulativeDistance, GripEventType.UNDERSTEER, Severity.MODERATE,
                            "Understeer: ${String.format("%.2f", actualLateralG)}G vs expected ${String.format("%.2f", expectedLateralG)}G",
                        ),
                    )
                }
            }

            // 3. ABS: vertical G spike during braking
            val accel = pt.accelMps2
            val verticalAccel = pt.verticalAccelMps2
            if (accel != null && accel < -2.0 && verticalAccel != null) {
                val vertG = abs(verticalAccel) / 9.81
                val prevVertG = prev.verticalAccelMps2?.let { abs(it) / 9.81 } ?: 0.0
                if (vertG > 0.3 && vertG > prevVertG * 1.5) {
                    events.add(
                        GripEvent(
                            i, cumulativeDistance, GripEventType.ABS_ACTIVATION, Severity.MILD,
                            "ABS detected: vertical G spike ${String.format("%.2f", vertG)}G during braking",
                        ),
                    )
                }
            }

            // 4. TRACTION LOSS on exit: speed stalls while curvature decreasing
            val prevCurv = prev.curvatureDegPerM
            val prevSpeed = prev.speed
            if (curvature != null && prevCurv != null && prevSpeed != null && speed > 5.0) {
                if (abs(curvature) < abs(prevCurv) * 0.8 && speed < prevSpeed * 1.02) {
                    // Curvature decreasing (exiting corner) but speed not increasing
                    val nextSpeed = next.speed
                    if (nextSpeed != null && nextSpeed < speed * 1.05) {
                        events.add(
                            GripEvent(
                                i, cumulativeDistance, GripEventType.TRACTION_LOSS, Severity.MILD,
                                "Traction loss on exit: speed stalled at ${String.format("%.0f", speed * 3.6)} km/h",
                            ),
                        )
                    }
                }
            }
        }

        // Deduplicate: merge events within 5 points of each other (keep highest severity)
        return deduplicateEvents(events)
    }

    private fun deduplicateEvents(events: List<GripEvent>): List<GripEvent> {
        if (events.isEmpty()) return events
        val sorted = events.sortedBy { it.pointIndex }
        val result = mutableListOf(sorted.first())
        for (event in sorted.drop(1)) {
            val last = result.last()
            if (event.pointIndex - last.pointIndex < 5 && event.type == last.type) {
                // Keep the more severe one
                if (event.severity.ordinal > last.severity.ordinal) {
                    result[result.lastIndex] = event
                }
            } else {
                result.add(event)
            }
        }
        return result
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }
}
