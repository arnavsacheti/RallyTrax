package com.rallytrax.app.data.trips

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects commonly driven routes by clustering stints with similar
 * start points, end points, and overall trajectory.
 *
 * Algorithm:
 * 1. Bounding-box pre-filter: skip stint pairs whose bounding boxes are too far apart
 * 2. Endpoint clustering: group stints where start→start and end→end are within threshold
 * 3. Trajectory similarity: sample N points along each track and compute average offset
 * 4. Merge overlapping clusters
 */
@Singleton
class CommonRouteDetector @Inject constructor() {

    companion object {
        /** Maximum distance between start points to be considered same origin (500m). */
        const val START_THRESHOLD_M = 500.0

        /** Maximum distance between end points to be considered same destination (500m). */
        const val END_THRESHOLD_M = 500.0

        /** Maximum average trajectory deviation to be considered same route (1km). */
        const val TRAJECTORY_THRESHOLD_M = 1000.0

        /** Number of sample points to compare along trajectory. */
        const val TRAJECTORY_SAMPLES = 10

        /** Minimum stints to form a common route. */
        const val MIN_DRIVES = 2
    }

    data class RouteCluster(
        val id: String = UUID.randomUUID().toString(),
        val stints: List<TrackEntity>,
        val representativeTrackId: String,
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val endLat: Double,
        val endLon: Double,
        val avgDistanceMeters: Double,
        val avgDurationMs: Long,
        val bestDurationMs: Long,
        val avgSpeedMps: Double,
        val lastDrivenAt: Long,
    )

    /**
     * Detect common route clusters from stints.
     *
     * @param stints All stints to analyze (should include all stints, not just unassigned).
     * @param startPoints Map of trackId to first TrackPoint.
     * @param endPoints Map of trackId to last TrackPoint.
     * @param sampledPoints Map of trackId to evenly-sampled track points (TRAJECTORY_SAMPLES count).
     */
    fun detect(
        stints: List<TrackEntity>,
        startPoints: Map<String, TrackPointEntity>,
        endPoints: Map<String, TrackPointEntity>,
        sampledPoints: Map<String, List<TrackPointEntity>> = emptyMap(),
    ): List<RouteCluster> {
        if (stints.size < MIN_DRIVES) return emptyList()

        // Only consider stints with known start/end points
        val viable = stints.filter { it.id in startPoints && it.id in endPoints }
        if (viable.size < MIN_DRIVES) return emptyList()

        // Build adjacency: which stints share similar start+end?
        val haversine = TripDetector().let { detector -> detector::haversineM }
        val clusters = mutableListOf<MutableSet<String>>()
        val assigned = mutableSetOf<String>()

        for (i in viable.indices) {
            if (viable[i].id in assigned) continue

            val cluster = mutableSetOf(viable[i].id)
            assigned.add(viable[i].id)

            for (j in i + 1 until viable.size) {
                if (viable[j].id in assigned) continue

                val startI = startPoints[viable[i].id]!!
                val startJ = startPoints[viable[j].id]!!
                val endI = endPoints[viable[i].id]!!
                val endJ = endPoints[viable[j].id]!!

                // Quick bounding box pre-filter
                if (!boundingBoxesNear(viable[i], viable[j])) continue

                val startDist = haversine(startI.lat, startI.lon, startJ.lat, startJ.lon)
                val endDist = haversine(endI.lat, endI.lon, endJ.lat, endJ.lon)

                if (startDist <= START_THRESHOLD_M && endDist <= END_THRESHOLD_M) {
                    // Endpoint match — optionally check trajectory similarity
                    val trajSimilar = checkTrajectorySimilarity(
                        viable[i].id, viable[j].id, sampledPoints, haversine
                    )
                    if (trajSimilar) {
                        cluster.add(viable[j].id)
                        assigned.add(viable[j].id)
                    }
                }

                // Also check reverse direction (A→B vs B→A)
                val startDistRev = haversine(startI.lat, startI.lon, endJ.lat, endJ.lon)
                val endDistRev = haversine(endI.lat, endI.lon, startJ.lat, startJ.lon)

                if (startDistRev <= START_THRESHOLD_M && endDistRev <= END_THRESHOLD_M) {
                    cluster.add(viable[j].id)
                    assigned.add(viable[j].id)
                }
            }

            if (cluster.size >= MIN_DRIVES) {
                clusters.add(cluster)
            }
        }

        // Build RouteCluster objects
        return clusters.map { clusterIds ->
            val clusterStints = viable.filter { it.id in clusterIds }
            buildCluster(clusterStints, startPoints, endPoints)
        }
    }

    private fun buildCluster(
        stints: List<TrackEntity>,
        startPoints: Map<String, TrackPointEntity>,
        endPoints: Map<String, TrackPointEntity>,
    ): RouteCluster {
        // Representative: the longest stint (likely most complete recording)
        val representative = stints.maxBy { it.distanceMeters }

        val avgDistance = stints.map { it.distanceMeters }.average()
        val avgDuration = stints.map { it.durationMs }.average().toLong()
        val bestDuration = stints.filter { it.durationMs > 0 }.minOfOrNull { it.durationMs } ?: 0L
        val avgSpeed = stints.mapNotNull { it.avgSpeedMps.takeIf { s -> s > 0 } }.average().takeIf { !it.isNaN() } ?: 0.0
        val lastDriven = stints.maxOf { it.recordedAt }

        // Average start/end coordinates
        val starts = stints.mapNotNull { startPoints[it.id] }
        val ends = stints.mapNotNull { endPoints[it.id] }
        val avgStartLat = starts.map { it.lat }.average()
        val avgStartLon = starts.map { it.lon }.average()
        val avgEndLat = ends.map { it.lat }.average()
        val avgEndLon = ends.map { it.lon }.average()

        val name = generateRouteName(stints)

        return RouteCluster(
            stints = stints,
            representativeTrackId = representative.id,
            name = name,
            startLat = avgStartLat,
            startLon = avgStartLon,
            endLat = avgEndLat,
            endLon = avgEndLon,
            avgDistanceMeters = avgDistance,
            avgDurationMs = avgDuration,
            bestDurationMs = bestDuration,
            avgSpeedMps = avgSpeed,
            lastDrivenAt = lastDriven,
        )
    }

    /**
     * Generate a default route name. Pattern: "Route #N" or based on distance.
     * AI enrichment will replace this later.
     */
    private fun generateRouteName(stints: List<TrackEntity>): String {
        val avgDistKm = stints.map { it.distanceMeters }.average() / 1000.0
        val label = when {
            avgDistKm < 5 -> "Short"
            avgDistKm < 20 -> "Medium"
            avgDistKm < 50 -> "Long"
            else -> "Extended"
        }
        return "$label Route (${stints.size} drives)"
    }

    private fun boundingBoxesNear(a: TrackEntity, b: TrackEntity): Boolean {
        // Quick check: if bounding boxes don't overlap or are far apart, skip
        val latOverlap = a.boundingBoxSouthLat <= b.boundingBoxNorthLat + 0.05 &&
            a.boundingBoxNorthLat >= b.boundingBoxSouthLat - 0.05
        val lonOverlap = a.boundingBoxWestLon <= b.boundingBoxEastLon + 0.05 &&
            a.boundingBoxEastLon >= b.boundingBoxWestLon - 0.05
        return latOverlap && lonOverlap
    }

    private fun checkTrajectorySimilarity(
        trackIdA: String,
        trackIdB: String,
        sampledPoints: Map<String, List<TrackPointEntity>>,
        haversine: (Double, Double, Double, Double) -> Double,
    ): Boolean {
        val samplesA = sampledPoints[trackIdA] ?: return true // no samples = accept on endpoint match alone
        val samplesB = sampledPoints[trackIdB] ?: return true

        if (samplesA.isEmpty() || samplesB.isEmpty()) return true

        // Compare sampled points pairwise (assumes both lists have same count)
        val minSize = minOf(samplesA.size, samplesB.size)
        var totalDeviation = 0.0
        for (i in 0 until minSize) {
            totalDeviation += haversine(samplesA[i].lat, samplesA[i].lon, samplesB[i].lat, samplesB[i].lon)
        }
        val avgDeviation = totalDeviation / minSize
        return avgDeviation <= TRAJECTORY_THRESHOLD_M
    }
}
