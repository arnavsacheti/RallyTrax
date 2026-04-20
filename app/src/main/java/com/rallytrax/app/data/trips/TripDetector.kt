package com.rallytrax.app.data.trips

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects trip candidates from a list of stints based on temporal and spatial proximity.
 *
 * Two stints are considered part of the same trip if:
 * 1. The time gap between the end of one stint and the start of the next is ≤ [maxGapMs]
 * 2. The start of the next stint is within [maxStartDistanceM] of the end of the previous stint
 *
 * Both conditions must be met — temporal proximity alone isn't enough (user might
 * make two unrelated short drives in the same day), and spatial proximity alone
 * isn't enough (user might drive the same route a week apart).
 */
@Singleton
class TripDetector @Inject constructor() {

    companion object {
        /** Default maximum time gap between consecutive stints (2 hours). */
        const val DEFAULT_MAX_GAP_MS = 2L * 60 * 60 * 1000

        /** Default maximum distance between end of one stint and start of the next (2 km). */
        const val DEFAULT_MAX_START_DISTANCE_M = 2000.0

        /** Minimum stints to form a trip candidate. */
        const val MIN_STINTS_PER_TRIP = 2
    }

    /**
     * A candidate trip grouping — a list of stints that should be grouped together.
     */
    data class TripCandidate(
        val stints: List<TrackEntity>,
        val suggestedName: String,
        val confidenceScore: Double,
        val totalDistanceMeters: Double,
        val totalDurationMs: Long,
        val startTimestamp: Long,
        val endTimestamp: Long,
    )

    /**
     * Detect trip candidates from unassigned stints.
     *
     * @param stints All stints sorted by recordedAt ASC. Should be pre-filtered to
     *               exclude stints already assigned to a trip.
     * @param startPoints Map of trackId to first TrackPoint (for end-to-start distance check).
     *                    If a stint's start point is missing, it can still be grouped by time only.
     * @param endPoints Map of trackId to last TrackPoint.
     * @param maxGapMs Maximum time gap between consecutive stints.
     * @param maxStartDistanceM Maximum distance between end→start.
     */
    fun detect(
        stints: List<TrackEntity>,
        startPoints: Map<String, TrackPointEntity>,
        endPoints: Map<String, TrackPointEntity>,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        maxStartDistanceM: Double = DEFAULT_MAX_START_DISTANCE_M,
    ): List<TripCandidate> {
        if (stints.size < MIN_STINTS_PER_TRIP) return emptyList()

        val sorted = stints.sortedBy { it.recordedAt }
        val groups = mutableListOf<MutableList<TrackEntity>>()
        var currentGroup = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            val prevEndTime = prev.recordedAt + prev.durationMs
            val timeGap = curr.recordedAt - prevEndTime

            if (timeGap in 0..maxGapMs) {
                // Time is within threshold — check spatial proximity
                val prevEnd = endPoints[prev.id]
                val currStart = startPoints[curr.id]

                val spatialOk = if (prevEnd != null && currStart != null) {
                    haversineM(prevEnd.lat, prevEnd.lon, currStart.lat, currStart.lon) <= maxStartDistanceM
                } else {
                    // Missing GPS data — use bounding box overlap as fallback
                    boundingBoxesOverlap(prev, curr, maxStartDistanceM)
                }

                if (spatialOk) {
                    currentGroup.add(curr)
                } else {
                    // Spatial check failed — start new group
                    groups.add(currentGroup)
                    currentGroup = mutableListOf(curr)
                }
            } else {
                // Time gap too large — start new group
                groups.add(currentGroup)
                currentGroup = mutableListOf(curr)
            }
        }
        groups.add(currentGroup)

        return groups
            .filter { it.size >= MIN_STINTS_PER_TRIP }
            .map { group -> buildCandidate(group, startPoints, endPoints) }
    }

    private fun buildCandidate(
        stints: List<TrackEntity>,
        startPoints: Map<String, TrackPointEntity>,
        endPoints: Map<String, TrackPointEntity>,
    ): TripCandidate {
        val totalDistance = stints.sumOf { it.distanceMeters }
        val totalDuration = stints.sumOf { it.durationMs }
        val startTime = stints.minOf { it.recordedAt }
        val endTime = stints.maxOf { it.recordedAt + it.durationMs }

        // Confidence: higher for more stints, closer temporal/spatial grouping
        val stintCountFactor = (stints.size.toDouble() / 5.0).coerceAtMost(1.0) // max confidence at 5+ stints
        val durationFactor = if (totalDuration > 30 * 60 * 1000) 1.0 else 0.7 // bonus for >30min total
        val confidence = (stintCountFactor * 0.6 + durationFactor * 0.4).coerceIn(0.0, 1.0)

        val name = generateLocalName(stints, startTime)

        return TripCandidate(
            stints = stints,
            suggestedName = name,
            confidenceScore = confidence,
            totalDistanceMeters = totalDistance,
            totalDurationMs = totalDuration,
            startTimestamp = startTime,
            endTimestamp = endTime,
        )
    }

    /**
     * Generate a local (non-AI) trip name from stint data.
     * Format: "{Day of week} {time period}" e.g. "Saturday Afternoon Drive"
     */
    internal fun generateLocalName(stints: List<TrackEntity>, startTime: Long): String {
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val dayName = dayFormat.format(Date(startTime))

        val hourFormat = SimpleDateFormat("H", Locale.getDefault())
        val hour = hourFormat.format(Date(startTime)).toIntOrNull() ?: 12

        val period = when {
            hour < 6 -> "Early Morning"
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            hour < 21 -> "Evening"
            else -> "Night"
        }

        val stintCount = stints.size
        val suffix = if (stintCount > 3) "Road Trip" else "Drive"

        return "$dayName $period $suffix"
    }

    /**
     * Fallback spatial check using bounding boxes when individual track points aren't available.
     * Checks if the bounding boxes are within [maxDistanceM] of each other.
     */
    private fun boundingBoxesOverlap(
        a: TrackEntity,
        b: TrackEntity,
        maxDistanceM: Double,
    ): Boolean {
        // Check if bounding boxes are close (use center-to-center as rough approximation)
        val aCenterLat = (a.boundingBoxNorthLat + a.boundingBoxSouthLat) / 2.0
        val aCenterLon = (a.boundingBoxEastLon + a.boundingBoxWestLon) / 2.0
        val bCenterLat = (b.boundingBoxNorthLat + b.boundingBoxSouthLat) / 2.0
        val bCenterLon = (b.boundingBoxEastLon + b.boundingBoxWestLon) / 2.0

        // Use generous multiplier since we're comparing centers, not edges
        return haversineM(aCenterLat, aCenterLon, bCenterLat, bCenterLon) <= maxDistanceM * 3
    }

    /**
     * Haversine distance between two lat/lon points in meters.
     */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
