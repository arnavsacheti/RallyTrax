package com.rallytrax.app.data.repository

import com.rallytrax.app.data.local.dao.SegmentDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.local.entity.SegmentRunEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.pacenotes.SegmentMatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class SegmentStats(
    val runCount: Int,
    val bestTimeMs: Long?,
    val averageTimeMs: Long?,
    val isFavorite: Boolean,
)

data class TrackSegmentMatch(
    val segment: SegmentEntity,
    val startPointIndex: Int,
    val endPointIndex: Int,
    val durationMs: Long,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val stats: SegmentStats,
)

@Singleton
class SegmentRepository @Inject constructor(
    private val segmentDao: SegmentDao,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) {
    fun getAllSegments(): Flow<List<SegmentEntity>> = segmentDao.getAllSegments()

    suspend fun getSegmentById(segmentId: String) = segmentDao.getSegmentById(segmentId)

    fun getRunsForSegment(segmentId: String) = segmentDao.getRunsForSegment(segmentId)

    suspend fun getSegmentStats(segmentId: String): SegmentStats {
        val segment = segmentDao.getSegmentById(segmentId) ?: return SegmentStats(0, null, null, false)
        return SegmentStats(
            runCount = segmentDao.getRunCount(segmentId),
            bestTimeMs = segmentDao.getBestTimeMs(segmentId),
            averageTimeMs = segmentDao.getAverageTimeMs(segmentId)?.toLong(),
            isFavorite = segment.isFavorite,
        )
    }

    suspend fun toggleFavorite(segmentId: String) {
        val segment = segmentDao.getSegmentById(segmentId) ?: return
        segmentDao.updateSegment(segment.copy(isFavorite = !segment.isFavorite))
    }

    suspend fun deleteSegment(segmentId: String) {
        segmentDao.deleteSegment(segmentId)
    }

    suspend fun updateSegmentName(segmentId: String, name: String) {
        val segment = segmentDao.getSegmentById(segmentId) ?: return
        segmentDao.updateSegment(segment.copy(name = name.trim()))
    }

    /**
     * Detect which existing segments appear in the given track.
     * Returns matches with run stats for each. Also inserts SegmentRunEntities.
     */
    suspend fun detectSegmentsForTrack(trackId: String): List<TrackSegmentMatch> {
        val track = trackDao.getTrackById(trackId) ?: return emptyList()
        val trackPoints = trackPointDao.getPointsForTrackOnce(trackId)
        if (trackPoints.size < 20) return emptyList()

        // Pre-filter segments by bounding box
        val candidates = segmentDao.getSegmentsOverlappingBounds(
            northLat = track.boundingBoxNorthLat,
            southLat = track.boundingBoxSouthLat,
            eastLon = track.boundingBoxEastLon,
            westLon = track.boundingBoxWestLon,
        )
        if (candidates.isEmpty()) return emptyList()

        // Check existing runs for this track to avoid duplicates
        val existingRuns = segmentDao.getRunsForTrack(trackId)
        val existingSegmentIds = existingRuns.map { it.segmentId }.toSet()

        val matches = mutableListOf<TrackSegmentMatch>()

        for (segment in candidates) {
            // Load reference polyline from the first run of this segment
            val refRun = segmentDao.getRunsForSegmentOnce(segment.id).firstOrNull()
            val refPoints = if (refRun != null) {
                val allRefPoints = trackPointDao.getPointsForTrackOnce(refRun.trackId)
                allRefPoints.filter { it.index in refRun.startPointIndex..refRun.endPointIndex }
            } else {
                // No reference run yet — skip (shouldn't happen for existing segments)
                continue
            }

            val matchResult = SegmentMatcher.matchSegmentToTrack(segment, trackPoints, refPoints) ?: continue

            // Compute run stats
            val runStats = SegmentMatcher.computeRunStats(trackPoints, matchResult.startPointIndex, matchResult.endPointIndex)

            // Insert run if not already recorded
            if (segment.id !in existingSegmentIds) {
                segmentDao.insertRun(
                    SegmentRunEntity(
                        segmentId = segment.id,
                        trackId = trackId,
                        startPointIndex = matchResult.startPointIndex,
                        endPointIndex = matchResult.endPointIndex,
                        durationMs = runStats.durationMs,
                        avgSpeedMps = runStats.avgSpeedMps,
                        maxSpeedMps = runStats.maxSpeedMps,
                        timestamp = track.recordedAt,
                    )
                )
            }

            val stats = getSegmentStats(segment.id)
            matches.add(
                TrackSegmentMatch(
                    segment = segment,
                    startPointIndex = matchResult.startPointIndex,
                    endPointIndex = matchResult.endPointIndex,
                    durationMs = runStats.durationMs,
                    avgSpeedMps = runStats.avgSpeedMps,
                    maxSpeedMps = runStats.maxSpeedMps,
                    stats = stats,
                )
            )
        }

        return matches
    }

    /**
     * Find new segment candidates by comparing this track against other overlapping tracks.
     */
    suspend fun findNewSegmentCandidates(trackId: String): List<SegmentMatcher.OverlapCandidate> {
        val track = trackDao.getTrackById(trackId) ?: return emptyList()
        val trackPoints = trackPointDao.getPointsForTrackOnce(trackId)
        if (trackPoints.size < 20) return emptyList()

        val overlappingTracks = trackDao.getTracksOverlappingBounds(
            excludeTrackId = trackId,
            northLat = track.boundingBoxNorthLat,
            southLat = track.boundingBoxSouthLat,
            eastLon = track.boundingBoxEastLon,
            westLon = track.boundingBoxWestLon,
        )

        val allCandidates = mutableListOf<SegmentMatcher.OverlapCandidate>()

        for (otherTrack in overlappingTracks.take(20)) { // Limit comparisons for performance
            val otherPoints = trackPointDao.getPointsForTrackOnce(otherTrack.id)
            if (otherPoints.size < 20) continue

            val overlaps = SegmentMatcher.findOverlaps(trackPoints, otherPoints)
            allCandidates.addAll(overlaps)
        }

        // Deduplicate candidates that represent the same road section
        return deduplicateCandidates(allCandidates)
    }

    /**
     * Create a user-defined segment from a point range on a track.
     */
    suspend fun createUserSegment(
        name: String,
        trackId: String,
        startIndex: Int,
        endIndex: Int,
    ): SegmentEntity? {
        val track = trackDao.getTrackById(trackId) ?: return null
        val points = trackPointDao.getPointsForTrackOnce(trackId)
        if (startIndex < 0 || endIndex >= points.size || startIndex >= endIndex) return null

        val startPt = points[startIndex]
        val endPt = points[endIndex]

        // Compute distance and bounding box
        var dist = 0.0
        var northLat = startPt.lat
        var southLat = startPt.lat
        var eastLon = startPt.lon
        var westLon = startPt.lon

        for (i in startIndex until endIndex) {
            dist += haversine(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon)
            northLat = max(northLat, points[i + 1].lat)
            southLat = min(southLat, points[i + 1].lat)
            eastLon = max(eastLon, points[i + 1].lon)
            westLon = min(westLon, points[i + 1].lon)
        }

        val segment = SegmentEntity(
            name = name.trim(),
            startLat = startPt.lat,
            startLon = startPt.lon,
            endLat = endPt.lat,
            endLon = endPt.lon,
            distanceMeters = dist,
            isUserDefined = true,
            boundingBoxNorthLat = northLat,
            boundingBoxSouthLat = southLat,
            boundingBoxEastLon = eastLon,
            boundingBoxWestLon = westLon,
        )
        segmentDao.insertSegment(segment)

        // Insert the initial run
        val runStats = SegmentMatcher.computeRunStats(points, startIndex, endIndex)
        segmentDao.insertRun(
            SegmentRunEntity(
                segmentId = segment.id,
                trackId = trackId,
                startPointIndex = startIndex,
                endPointIndex = endIndex,
                durationMs = runStats.durationMs,
                avgSpeedMps = runStats.avgSpeedMps,
                maxSpeedMps = runStats.maxSpeedMps,
                timestamp = track.recordedAt,
            )
        )

        // Backfill: scan other tracks for this segment
        backfillRunsForSegment(segment.id)

        return segment
    }

    /**
     * Save an auto-detected overlap candidate as a named segment.
     */
    suspend fun saveOverlapAsSegment(
        name: String,
        candidate: SegmentMatcher.OverlapCandidate,
        sourceTrackId: String,
    ): SegmentEntity? {
        val track = trackDao.getTrackById(sourceTrackId) ?: return null

        val segment = SegmentEntity(
            name = name.trim(),
            startLat = candidate.startLat,
            startLon = candidate.startLon,
            endLat = candidate.endLat,
            endLon = candidate.endLon,
            distanceMeters = candidate.overlapDistanceM,
            isUserDefined = false,
            boundingBoxNorthLat = max(candidate.startLat, candidate.endLat),
            boundingBoxSouthLat = min(candidate.startLat, candidate.endLat),
            boundingBoxEastLon = max(candidate.startLon, candidate.endLon),
            boundingBoxWestLon = min(candidate.startLon, candidate.endLon),
        )
        segmentDao.insertSegment(segment)

        // Insert the source track's run
        val points = trackPointDao.getPointsForTrackOnce(sourceTrackId)
        val runStats = SegmentMatcher.computeRunStats(points, candidate.startIdxA, candidate.endIdxA)
        segmentDao.insertRun(
            SegmentRunEntity(
                segmentId = segment.id,
                trackId = sourceTrackId,
                startPointIndex = candidate.startIdxA,
                endPointIndex = candidate.endIdxA,
                durationMs = runStats.durationMs,
                avgSpeedMps = runStats.avgSpeedMps,
                maxSpeedMps = runStats.maxSpeedMps,
                timestamp = track.recordedAt,
            )
        )

        // Backfill other tracks
        backfillRunsForSegment(segment.id)

        return segment
    }

    /**
     * Scan all tracks for a newly created segment and insert runs.
     */
    suspend fun backfillRunsForSegment(segmentId: String) {
        val segment = segmentDao.getSegmentById(segmentId) ?: return
        val existingRuns = segmentDao.getRunsForSegmentOnce(segmentId)
        val existingTrackIds = existingRuns.map { it.trackId }.toSet()

        // Get reference polyline from first run
        val refRun = existingRuns.firstOrNull() ?: return
        val refPoints = trackPointDao.getPointsForTrackOnce(refRun.trackId)
            .filter { it.index in refRun.startPointIndex..refRun.endPointIndex }
        if (refPoints.size < 5) return

        // Find overlapping tracks
        val candidates = trackDao.getTracksOverlappingBounds(
            excludeTrackId = "",
            northLat = segment.boundingBoxNorthLat,
            southLat = segment.boundingBoxSouthLat,
            eastLon = segment.boundingBoxEastLon,
            westLon = segment.boundingBoxWestLon,
        )

        for (track in candidates) {
            if (track.id in existingTrackIds) continue

            val trackPoints = trackPointDao.getPointsForTrackOnce(track.id)
            val matchResult = SegmentMatcher.matchSegmentToTrack(segment, trackPoints, refPoints) ?: continue

            val runStats = SegmentMatcher.computeRunStats(trackPoints, matchResult.startPointIndex, matchResult.endPointIndex)
            segmentDao.insertRun(
                SegmentRunEntity(
                    segmentId = segmentId,
                    trackId = track.id,
                    startPointIndex = matchResult.startPointIndex,
                    endPointIndex = matchResult.endPointIndex,
                    durationMs = runStats.durationMs,
                    avgSpeedMps = runStats.avgSpeedMps,
                    maxSpeedMps = runStats.maxSpeedMps,
                    timestamp = track.recordedAt,
                )
            )
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun deduplicateCandidates(
        candidates: List<SegmentMatcher.OverlapCandidate>,
    ): List<SegmentMatcher.OverlapCandidate> {
        if (candidates.size <= 1) return candidates

        // Sort by overlap distance descending and remove candidates whose start/end
        // are within 200m of an already-kept candidate
        val sorted = candidates.sortedByDescending { it.overlapDistanceM }
        val kept = mutableListOf<SegmentMatcher.OverlapCandidate>()

        for (candidate in sorted) {
            val isDuplicate = kept.any { existing ->
                haversine(candidate.startLat, candidate.startLon, existing.startLat, existing.startLon) < 200.0 &&
                    haversine(candidate.endLat, candidate.endLon, existing.endLat, existing.endLon) < 200.0
            }
            if (!isDuplicate) {
                kept.add(candidate)
            }
        }

        return kept
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}
