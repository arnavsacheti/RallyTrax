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
 *
 * Event types:
 * - OVERSTEER: actual yaw rate exceeds expected for the curvature (rear grip loss)
 * - UNDERSTEER: actual lateral G is lower than expected for the speed/curvature (front grip loss)
 * - ABS_ACTIVATION: vertical G spike during braking (wheel hop / ABS chatter)
 * - TRACTION_LOSS: driver is on throttle exiting a corner but speed doesn't respond —
 *   requires positive accel demand plus at least one instability signal (yaw spike,
 *   lateral divergence, or vertical vibration) to avoid false positives from coasting
 * - WHEELSPIN: hard throttle on straight or exit with speed stall plus instability
 * - CORNER_ENTRY_LOCK: heavy braking into a curve with yaw divergence (front lock-up)
 */
object GripEventDetector {

    enum class GripEventType {
        OVERSTEER,
        UNDERSTEER,
        ABS_ACTIVATION,
        TRACTION_LOSS,
        WHEELSPIN,
        CORNER_ENTRY_LOCK,
    }

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

            val curvature = pt.curvatureDegPerM
            val actualYaw = pt.yawRateDegPerS
            val lateralAccel = pt.lateralAccelMps2
            val verticalAccel = pt.verticalAccelMps2
            val accel = pt.accelMps2

            // 1. OVERSTEER: actual yaw rate >> expected yaw rate
            if (curvature != null && actualYaw != null && abs(curvature) > 0.5) {
                val expectedYawDegPerS = speed * abs(curvature)
                if (expectedYawDegPerS > 5.0) {
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

            // 4. TRACTION LOSS on corner exit: driver is on throttle (positive accel)
            //    but speed isn't responding, AND at least one instability signal is present.
            //    This distinguishes real grip loss from normal coasting/throttle-lift.
            val prevCurv = prev.curvatureDegPerM
            val prevSpeed = prev.speed
            if (curvature != null && prevCurv != null && prevSpeed != null && speed > 5.0) {
                val exitingCorner = abs(curvature) < abs(prevCurv) * 0.8 && abs(prevCurv) > 0.5
                val onThrottle = accel != null && accel > 0.5 // driver is accelerating, not coasting
                val speedNotResponding = speed < prevSpeed * 1.02

                if (exitingCorner && onThrottle && speedNotResponding) {
                    // Require at least one instability signal to confirm grip loss
                    val instabilityScore = computeInstabilityScore(pt, prev, speed, curvature)
                    if (instabilityScore > 0) {
                        val severity = when {
                            instabilityScore >= 3 -> Severity.SEVERE
                            instabilityScore >= 2 -> Severity.MODERATE
                            else -> Severity.MILD
                        }
                        events.add(
                            GripEvent(
                                i, cumulativeDistance, GripEventType.TRACTION_LOSS, severity,
                                "Traction loss on exit: throttle applied at ${String.format("%.0f", speed * 3.6)} km/h but speed stalled",
                            ),
                        )
                    }
                }
            }

            // 5. WHEELSPIN: hard throttle (high accel demand) but speed response is poor,
            //    with instability signals — on straights or very gentle curves
            if (accel != null && accel > 1.5 && speed > 4.0) {
                val onStraightOrGentle = curvature == null || abs(curvature) < 1.0
                val nextSpeed = next.speed
                if (onStraightOrGentle && prevSpeed != null && nextSpeed != null) {
                    // Expected speed gain from accel: v + a*dt
                    val dt = (next.timestamp - pt.timestamp) / 1000.0
                    if (dt > 0) {
                        val expectedGain = accel * dt
                        val actualGain = nextSpeed - speed
                        // Speed gained less than 30% of what physics says it should
                        if (expectedGain > 0.5 && actualGain < expectedGain * 0.3) {
                            val instabilityScore = computeInstabilityScore(pt, prev, speed, curvature)
                            if (instabilityScore > 0) {
                                val severity = when {
                                    instabilityScore >= 3 -> Severity.SEVERE
                                    instabilityScore >= 2 -> Severity.MODERATE
                                    else -> Severity.MILD
                                }
                                events.add(
                                    GripEvent(
                                        i, cumulativeDistance, GripEventType.WHEELSPIN, severity,
                                        "Wheelspin: accelerating at ${String.format("%.1f", accel)} m/s² but gained only ${String.format("%.1f", actualGain * 3.6)} km/h",
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // 6. CORNER ENTRY LOCK: heavy braking into a curve with yaw divergence
            //    (front wheels locking up, car ploughing straight instead of turning)
            if (accel != null && accel < -3.0 && curvature != null && abs(curvature) > 0.5 && speed > 8.0) {
                val nextCurv = next.curvatureDegPerM
                if (actualYaw != null && nextCurv != null) {
                    val expectedYaw = speed * abs(curvature)
                    if (expectedYaw > 5.0) {
                        val yawDeficit = 1.0 - (abs(actualYaw) / expectedYaw)
                        // Car is not rotating as much as the road demands (front lock)
                        if (yawDeficit > 0.4) {
                            val severity = when {
                                yawDeficit > 0.7 -> Severity.SEVERE
                                yawDeficit > 0.55 -> Severity.MODERATE
                                else -> Severity.MILD
                            }
                            events.add(
                                GripEvent(
                                    i, cumulativeDistance, GripEventType.CORNER_ENTRY_LOCK, severity,
                                    "Corner entry lock: braking at ${String.format("%.1f", abs(accel))} m/s², yaw ${String.format("%.0f", abs(actualYaw))}°/s vs expected ${String.format("%.0f", expectedYaw)}°/s",
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Deduplicate: merge events within 5 points of each other (keep highest severity)
        return deduplicateEvents(events)
    }

    /**
     * Computes a score (0-4) for how many instability signals are present at this point.
     * Used by TRACTION_LOSS and WHEELSPIN to avoid false positives from normal driving.
     *
     * Signals checked:
     * - Yaw rate spike (actual >> expected for curvature)
     * - Lateral acceleration divergence (sudden change from previous point)
     * - Vertical vibration (wheel hop / surface break)
     * - Roll rate spike (weight transfer instability)
     */
    private fun computeInstabilityScore(
        pt: TrackPointEntity,
        prev: TrackPointEntity,
        speed: Double,
        curvature: Double?,
    ): Int {
        var score = 0

        // Yaw spike: actual yaw significantly exceeds expected
        val actualYaw = pt.yawRateDegPerS
        if (actualYaw != null && curvature != null && abs(curvature) > 0.1) {
            val expectedYaw = speed * abs(curvature)
            if (expectedYaw > 2.0 && abs(actualYaw) > expectedYaw * 1.3) {
                score++
            }
        }

        // Lateral accel divergence: sudden lateral change between consecutive points
        val lateralAccel = pt.lateralAccelMps2
        val prevLateral = prev.lateralAccelMps2
        if (lateralAccel != null && prevLateral != null) {
            val lateralDelta = abs(lateralAccel - prevLateral)
            if (lateralDelta > 2.0) { // > 0.2G sudden lateral shift
                score++
            }
        }

        // Vertical vibration: elevated vertical G (wheel hop, surface break)
        val verticalAccel = pt.verticalAccelMps2
        if (verticalAccel != null && abs(verticalAccel) / 9.81 > 0.25) {
            score++
        }

        // Roll rate spike: sudden weight transfer
        val rollRate = pt.rollRateDegPerS
        val prevRoll = prev.rollRateDegPerS
        if (rollRate != null && prevRoll != null) {
            val rollDelta = abs(rollRate - prevRoll)
            if (rollDelta > 15.0) { // > 15 deg/s change
                score++
            }
        }

        return score
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
