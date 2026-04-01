package com.rallytrax.app.data.classification

import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.GridCellEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects when a user repeatedly drives the same route and suggests saving
 * it as a named route so they can track times across attempts.
 *
 * Algorithm:
 * 1. Find tracks with overlapping bounding boxes
 * 2. Compare grid-cell signatures for fast similarity check
 * 3. Verify with point-level proximity matching
 * 4. Group similar tracks and report frequency
 */
@Singleton
class FrequentRouteDetector @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) {
    /**
     * A suggestion to save a frequently driven route.
     *
     * @param similarTrackIds IDs of all tracks (including the current one) that share this route
     * @param timesDriven total number of times this route was driven
     * @param bestTimeMs fastest completion time among similar tracks (null if unavailable)
     * @param avgDistanceM average distance across similar tracks
     * @param representativeTrackId the track whose points best represent the route shape
     */
    data class RouteSuggestion(
        val similarTrackIds: List<String>,
        val timesDriven: Int,
        val bestTimeMs: Long?,
        val avgDistanceM: Double,
        val representativeTrackId: String,
    )

    companion object {
        /** Minimum fraction of grid cells that must overlap to consider two tracks similar. */
        private const val MIN_CELL_OVERLAP_RATIO = 0.60

        /** Point-level proximity threshold in metres. */
        private const val PROXIMITY_THRESHOLD_M = 75.0

        /** Minimum fraction of points within proximity to confirm similarity. */
        private const val MIN_POINT_COVERAGE = 0.65

        /** A route must be driven at least this many times to trigger a suggestion. */
        private const val MIN_DRIVE_COUNT = 3

        /** Maximum number of other tracks to compare against (performance guard). */
        private const val MAX_COMPARISONS = 50

        /** Minimum track distance in metres to be considered for route detection. */
        private const val MIN_TRACK_DISTANCE_M = 500.0
    }

    /**
     * Analyse the given track and return a [RouteSuggestion] if the user has driven
     * a similar route [MIN_DRIVE_COUNT] or more times without saving it as a route.
     *
     * Returns null if no frequent route is detected or if the route is already saved.
     */
    suspend fun detectFrequentRoute(trackId: String): RouteSuggestion? {
        val track = trackDao.getTrackById(trackId) ?: return null
        if (track.distanceMeters < MIN_TRACK_DISTANCE_M) return null

        // Don't suggest if this track is already categorised as a route
        if (track.trackCategory == "route") return null

        val trackPoints = trackPointDao.getPointsForTrackOnce(trackId)
        if (trackPoints.size < 20) return null

        val trackCells = buildCellSignature(trackPoints)
        if (trackCells.isEmpty()) return null

        // Find candidate tracks with overlapping bounding boxes
        val candidates = trackDao.getTracksOverlappingBounds(
            excludeTrackId = trackId,
            northLat = track.boundingBoxNorthLat,
            southLat = track.boundingBoxSouthLat,
            eastLon = track.boundingBoxEastLon,
            westLon = track.boundingBoxWestLon,
        )

        // Filter to stints only (routes are already saved)
        val stintCandidates = candidates
            .filter { it.trackCategory == "stint" && it.distanceMeters >= MIN_TRACK_DISTANCE_M }
            .take(MAX_COMPARISONS)

        val similarTrackIds = mutableListOf(trackId)

        for (candidate in stintCandidates) {
            val candidatePoints = trackPointDao.getPointsForTrackOnce(candidate.id)
            if (candidatePoints.size < 20) continue

            if (areTracksSimilar(trackCells, trackPoints, candidatePoints)) {
                similarTrackIds.add(candidate.id)
            }
        }

        if (similarTrackIds.size < MIN_DRIVE_COUNT) return null

        // Gather stats from similar tracks
        val similarTracks = similarTrackIds.mapNotNull { trackDao.getTrackById(it) }
        val bestTime = similarTracks
            .filter { it.durationMs > 0 }
            .minOfOrNull { it.durationMs }
        val avgDistance = similarTracks
            .map { it.distanceMeters }
            .average()

        // Use the track with median distance as the representative shape
        val representative = similarTracks
            .sortedBy { it.distanceMeters }
            .let { it[it.size / 2] }

        return RouteSuggestion(
            similarTrackIds = similarTrackIds,
            timesDriven = similarTrackIds.size,
            bestTimeMs = bestTime,
            avgDistanceM = avgDistance,
            representativeTrackId = representative.id,
        )
    }

    /**
     * Save the current track as a named route and back-link all similar tracks to it
     * by giving them the same name and setting the representative as category "route".
     */
    suspend fun saveAsRoute(
        suggestion: RouteSuggestion,
        routeName: String,
    ): TrackEntity? {
        val representative = trackDao.getTrackById(suggestion.representativeTrackId) ?: return null

        // Promote the representative track to a route
        val route = representative.copy(
            name = routeName.trim(),
            trackCategory = "route",
        )
        trackDao.updateTrack(route)

        // Update similar tracks to share the same name so PR detection works
        for (id in suggestion.similarTrackIds) {
            if (id == suggestion.representativeTrackId) continue
            val t = trackDao.getTrackById(id) ?: continue
            trackDao.updateTrack(t.copy(name = routeName.trim()))
        }

        return route
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Two-phase similarity check:
     * 1. Fast grid-cell signature overlap
     * 2. Point-level proximity verification
     */
    private fun areTracksSimilar(
        referenceCells: Set<Long>,
        referencePoints: List<TrackPointEntity>,
        candidatePoints: List<TrackPointEntity>,
    ): Boolean {
        // Phase 1: Grid cell overlap
        val candidateCells = buildCellSignature(candidatePoints)
        val intersection = referenceCells.intersect(candidateCells)
        val smallerSize = min(referenceCells.size, candidateCells.size)
        if (smallerSize == 0) return false

        val cellOverlap = intersection.size.toDouble() / smallerSize
        if (cellOverlap < MIN_CELL_OVERLAP_RATIO) return false

        // Phase 2: Point-level proximity (sample every Nth point for performance)
        val sampleStep = max(1, referencePoints.size / 100)
        val sampledRef = referencePoints.filterIndexed { i, _ -> i % sampleStep == 0 }

        var matchCount = 0
        for (refPt in sampledRef) {
            if (hasNearbyPoint(candidatePoints, refPt.lat, refPt.lon, PROXIMITY_THRESHOLD_M)) {
                matchCount++
            }
        }

        val pointCoverage = matchCount.toDouble() / sampledRef.size
        return pointCoverage >= MIN_POINT_COVERAGE
    }

    private fun buildCellSignature(points: List<TrackPointEntity>): Set<Long> {
        return points.mapTo(mutableSetOf()) {
            GridCellEntity.encodeCellId(
                GridCellEntity.gridLatFor(it.lat),
                GridCellEntity.gridLonFor(it.lon),
            )
        }
    }

    /**
     * Check if any point in [points] is within [thresholdM] of ([lat], [lon]).
     * Uses early exit for performance.
     */
    private fun hasNearbyPoint(
        points: List<TrackPointEntity>,
        lat: Double,
        lon: Double,
        thresholdM: Double,
    ): Boolean {
        // Quick lat/lon degree filter (~111m per 0.001 degree)
        val degThreshold = thresholdM / 111_000.0
        for (pt in points) {
            if (kotlin.math.abs(pt.lat - lat) > degThreshold) continue
            if (kotlin.math.abs(pt.lon - lon) > degThreshold * 1.5) continue
            if (haversine(lat, lon, pt.lat, pt.lon) < thresholdM) return true
        }
        return false
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
