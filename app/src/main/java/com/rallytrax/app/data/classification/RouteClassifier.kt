package com.rallytrax.app.data.classification

import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Route classification engine. Analyzes GPS track data to compute:
 * - Curviness score (circumscribed-circle-radius algorithm)
 * - Speed profile → suggested route type
 * - Elevation profile → mountain pass detection
 * - Difficulty rating (weighted composite)
 */
object RouteClassifier {

    // ── Route Types ──────────────────────────────────────────────────────────
    const val TYPE_CANYON_ROAD = "Canyon Road"
    const val TYPE_MOUNTAIN_PASS = "Mountain Pass"
    const val TYPE_COASTAL_ROAD = "Coastal Road"
    const val TYPE_SCENIC_BYWAY = "Scenic Byway"
    const val TYPE_HIGHWAY = "Highway"
    const val TYPE_BACKROAD = "Backroad"
    const val TYPE_URBAN = "Urban"
    const val TYPE_OFF_ROAD = "Off-Road/Trail"

    val allRouteTypes = listOf(
        TYPE_CANYON_ROAD, TYPE_MOUNTAIN_PASS, TYPE_COASTAL_ROAD, TYPE_SCENIC_BYWAY,
        TYPE_HIGHWAY, TYPE_BACKROAD, TYPE_URBAN, TYPE_OFF_ROAD,
    )

    // ── Activity Tags ────────────────────────────────────────────────────────
    val allActivityTags = listOf(
        "Solo Drive", "Group Drive / Ridealong", "Road Trip",
        "Commute", "Track Day", "Rally Stage", "Photography Run", "Test Drive",
    )

    // ── Difficulty Ratings ───────────────────────────────────────────────────
    const val DIFFICULTY_CASUAL = "Casual"
    const val DIFFICULTY_MODERATE = "Moderate"
    const val DIFFICULTY_SPIRITED = "Spirited"
    const val DIFFICULTY_EXPERT = "Expert"

    val difficultyColors = mapOf(
        DIFFICULTY_CASUAL to 0xFF34A853.toInt(),    // green
        DIFFICULTY_MODERATE to 0xFFFBBC04.toInt(),  // amber
        DIFFICULTY_SPIRITED to 0xFFE8710A.toInt(),  // orange
        DIFFICULTY_EXPERT to 0xFFEA4335.toInt(),    // red
    )

    // ── Surface Types ────────────────────────────────────────────────────────
    const val SURFACE_PAVED = "Paved"
    const val SURFACE_GRAVEL = "Gravel"
    const val SURFACE_DIRT = "Dirt"
    const val SURFACE_COBBLESTONE = "Cobblestone"
    const val SURFACE_MIXED = "Mixed"
    const val SURFACE_UNKNOWN = "Unknown"

    // ── Classification Result ────────────────────────────────────────────────

    data class ClassificationResult(
        val suggestedRouteType: String,
        val curvinessScore: Double,
        val difficultyRating: String,
        val avgSpeedKmh: Double,
        val maxElevationChangeM: Double,
        val stopCount: Int,
    )

    /**
     * Classify a track from its GPS points.
     */
    fun classify(points: List<TrackPointEntity>): ClassificationResult {
        if (points.size < 10) {
            return ClassificationResult(TYPE_URBAN, 0.0, DIFFICULTY_CASUAL, 0.0, 0.0, 0)
        }

        val curvinessScore = computeCurvinessScore(points)
        val avgSpeedKmh = computeAvgSpeedKmh(points)
        val maxElevationChange = computeMaxElevationChange(points)
        val stopCount = countStops(points)
        val routeType = classifyRouteType(curvinessScore, avgSpeedKmh, maxElevationChange, stopCount)
        val difficulty = classifyDifficulty(curvinessScore, maxElevationChange, avgSpeedKmh)

        return ClassificationResult(
            suggestedRouteType = routeType,
            curvinessScore = curvinessScore,
            difficultyRating = difficulty,
            avgSpeedKmh = avgSpeedKmh,
            maxElevationChangeM = maxElevationChange,
            stopCount = stopCount,
        )
    }

    /**
     * Curviness score: For every 3 consecutive GPS points, compute the circumscribed
     * circle radius. Classify segments and sum weighted by length.
     * Scale: < 300 = mostly straight, 300-1000 = moderate, 1000-5000 = twisty, 5000+ = highly technical
     */
    fun computeCurvinessScore(points: List<TrackPointEntity>): Double {
        if (points.size < 3) return 0.0

        var totalScore = 0.0
        for (i in 1 until points.size - 1) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]

            val a = haversineM(p0.lat, p0.lon, p1.lat, p1.lon)
            val b = haversineM(p1.lat, p1.lon, p2.lat, p2.lon)
            val c = haversineM(p0.lat, p0.lon, p2.lat, p2.lon)

            if (a < 1 || b < 1) continue

            // Circumscribed circle radius via triangle sides
            val s = (a + b + c) / 2.0
            val area = sqrt(maxOf(0.0, s * (s - a) * (s - b) * (s - c)))
            val radius = if (area > 0.01) (a * b * c) / (4 * area) else Double.MAX_VALUE

            val segmentLength = (a + b) / 2.0
            val segmentScore = when {
                radius > 500 -> 0.0                    // straight
                radius in 200.0..500.0 -> 1.0          // sweeping
                radius in 50.0..200.0 -> 3.0           // tight
                else -> 6.0                             // super-tight (< 50m)
            }
            totalScore += segmentScore * segmentLength
        }

        // Normalize by total distance (per km)
        val totalDistanceM = (0 until points.size - 1).sumOf { i ->
            haversineM(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon)
        }
        return if (totalDistanceM > 100) totalScore / (totalDistanceM / 1000.0) else 0.0
    }

    private fun computeAvgSpeedKmh(points: List<TrackPointEntity>): Double {
        val speeds = points.mapNotNull { it.speed }.filter { it > 0.5 }
        return if (speeds.isNotEmpty()) speeds.average() * 3.6 else 0.0
    }

    private fun computeMaxElevationChange(points: List<TrackPointEntity>): Double {
        val elevations = points.mapNotNull { it.elevation }
        return if (elevations.size >= 2) (elevations.max() - elevations.min()) else 0.0
    }

    private fun countStops(points: List<TrackPointEntity>): Int {
        var stopCount = 0
        var wasStopped = false
        for (p in points) {
            val speed = p.speed ?: continue
            if (speed < 0.5 && !wasStopped) {
                stopCount++
                wasStopped = true
            } else if (speed > 2.0) {
                wasStopped = false
            }
        }
        return stopCount
    }

    private fun classifyRouteType(
        curviness: Double,
        avgSpeedKmh: Double,
        maxElevationChange: Double,
        stopCount: Int,
    ): String {
        return when {
            maxElevationChange > 300 && curviness > 500 -> TYPE_MOUNTAIN_PASS
            avgSpeedKmh > 90 && curviness < 300 -> TYPE_HIGHWAY
            avgSpeedKmh < 40 && stopCount > 10 -> TYPE_URBAN
            curviness > 1000 && maxElevationChange > 100 -> TYPE_CANYON_ROAD
            curviness > 500 -> TYPE_SCENIC_BYWAY
            maxElevationChange < 50 && curviness > 300 -> TYPE_COASTAL_ROAD
            curviness < 200 && avgSpeedKmh < 60 -> TYPE_BACKROAD
            else -> TYPE_SCENIC_BYWAY
        }
    }

    /**
     * Difficulty rating from weighted combination:
     * curviness (50%), elevation change (20%), avg speed (15%), road surface (15% — default paved).
     */
    private fun classifyDifficulty(
        curviness: Double,
        maxElevationChange: Double,
        avgSpeedKmh: Double,
    ): String {
        // Normalize each factor to 0-100 scale
        val curvinessFactor = (curviness / 50.0).coerceIn(0.0, 100.0)
        val elevationFactor = (maxElevationChange / 10.0).coerceIn(0.0, 100.0)
        val speedFactor = (avgSpeedKmh / 1.5).coerceIn(0.0, 100.0)

        val weighted = curvinessFactor * 0.50 + elevationFactor * 0.20 + speedFactor * 0.15

        return when {
            curviness < 300 -> DIFFICULTY_CASUAL
            curviness < 1500 -> DIFFICULTY_MODERATE
            curviness < 5000 -> DIFFICULTY_SPIRITED
            else -> DIFFICULTY_EXPERT
        }
    }

    /**
     * Suggest the best vehicle for a given route type.
     * Off-road → prefer AWD/4WD, Highway → prefer fuel-efficient, default → active vehicle.
     */
    fun suggestVehicle(routeType: String, vehicles: List<VehicleEntity>): VehicleEntity? {
        if (vehicles.isEmpty()) return null
        val active = vehicles.firstOrNull { it.isActive }

        return when (routeType) {
            TYPE_OFF_ROAD -> {
                // Prefer AWD/4WD drivetrain
                vehicles.firstOrNull {
                    val dt = it.drivetrain?.uppercase() ?: ""
                    dt.contains("AWD") || dt.contains("4WD") || dt.contains("4X4")
                } ?: active ?: vehicles.first()
            }
            TYPE_HIGHWAY, TYPE_SCENIC_BYWAY -> {
                // Prefer most fuel-efficient (highest EPA combined MPG)
                vehicles.maxByOrNull { it.epaCombinedMpg ?: 0.0 } ?: active ?: vehicles.first()
            }
            else -> active ?: vehicles.first()
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
