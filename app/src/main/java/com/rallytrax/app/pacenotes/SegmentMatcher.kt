package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.GridCellEntity
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects geographic overlap between track polylines and segments.
 *
 * Algorithm:
 * 1. Grid cell pre-filter (~111m cells, reuses GridCellEntity bucket logic)
 * 2. Point-by-point proximity matching (50m threshold)
 * 3. Requires ≥80% coverage over ≥500m
 */
object SegmentMatcher {

    private const val PROXIMITY_THRESHOLD_M = 50.0
    private const val MIN_OVERLAP_DISTANCE_M = 500.0
    private const val MIN_COVERAGE_RATIO = 0.8
    private const val MIN_CONSECUTIVE_CELLS = 5

    data class MatchResult(
        val segmentId: String,
        val startPointIndex: Int,
        val endPointIndex: Int,
        val coverageRatio: Double,
        val matchedDistanceM: Double,
    )

    data class OverlapCandidate(
        val startIdxA: Int,
        val endIdxA: Int,
        val startIdxB: Int,
        val endIdxB: Int,
        val overlapDistanceM: Double,
        val startLat: Double,
        val startLon: Double,
        val endLat: Double,
        val endLon: Double,
    )

    /**
     * Find where an existing segment appears in the given track points.
     * Returns null if no match, or a MatchResult with the matched point range.
     */
    fun matchSegmentToTrack(
        segment: SegmentEntity,
        trackPoints: List<TrackPointEntity>,
        segmentRefPoints: List<TrackPointEntity>,
    ): MatchResult? {
        if (trackPoints.size < 10 || segmentRefPoints.size < 5) return null

        // Step 1: Grid cell overlap pre-filter
        val trackCells = trackPoints.map {
            GridCellEntity.encodeCellId(GridCellEntity.gridLatFor(it.lat), GridCellEntity.gridLonFor(it.lon))
        }.toSet()

        val segCells = segmentRefPoints.map {
            GridCellEntity.encodeCellId(GridCellEntity.gridLatFor(it.lat), GridCellEntity.gridLonFor(it.lon))
        }

        // Count consecutive shared cells
        var maxConsecutive = 0
        var current = 0
        for (cell in segCells) {
            if (cell in trackCells) {
                current++
                maxConsecutive = max(maxConsecutive, current)
            } else {
                current = 0
            }
        }
        if (maxConsecutive < MIN_CONSECUTIVE_CELLS) return null

        // Step 2: Point-by-point proximity matching
        var firstMatchIdx = -1
        var lastMatchIdx = -1
        var matchCount = 0

        for (segPt in segmentRefPoints) {
            val nearestIdx = findNearestWithinThreshold(trackPoints, segPt.lat, segPt.lon, PROXIMITY_THRESHOLD_M)
            if (nearestIdx >= 0) {
                if (firstMatchIdx < 0) firstMatchIdx = nearestIdx
                lastMatchIdx = nearestIdx
                matchCount++
            }
        }

        if (firstMatchIdx < 0 || lastMatchIdx <= firstMatchIdx) return null

        // Step 3: Check coverage ratio
        val coverageRatio = matchCount.toDouble() / segmentRefPoints.size
        if (coverageRatio < MIN_COVERAGE_RATIO) return null

        // Compute matched distance
        val matchedDistance = computeDistanceBetweenIndices(trackPoints, firstMatchIdx, lastMatchIdx)
        if (matchedDistance < MIN_OVERLAP_DISTANCE_M * 0.5) return null // Allow shorter if high coverage

        return MatchResult(
            segmentId = segment.id,
            startPointIndex = firstMatchIdx,
            endPointIndex = lastMatchIdx,
            coverageRatio = coverageRatio,
            matchedDistanceM = matchedDistance,
        )
    }

    /**
     * Find overlapping road sections between two track polylines.
     * Returns candidates where both tracks share a continuous stretch ≥ MIN_OVERLAP_DISTANCE_M.
     */
    fun findOverlaps(
        pointsA: List<TrackPointEntity>,
        pointsB: List<TrackPointEntity>,
    ): List<OverlapCandidate> {
        if (pointsA.size < 20 || pointsB.size < 20) return emptyList()

        // Grid cell overlap to find shared regions
        val cellsB = mutableMapOf<Long, MutableList<Int>>()
        for ((idx, pt) in pointsB.withIndex()) {
            val cellId = GridCellEntity.encodeCellId(
                GridCellEntity.gridLatFor(pt.lat),
                GridCellEntity.gridLonFor(pt.lon),
            )
            cellsB.getOrPut(cellId) { mutableListOf() }.add(idx)
        }

        // Walk through A and find runs of points that have nearby B points
        data class Run(val startA: Int, val endA: Int, val startB: Int, val endB: Int)
        val runs = mutableListOf<Run>()
        var runStartA = -1
        var runStartB = -1
        var runEndB = -1
        var prevMatchA = -2

        for (idxA in pointsA.indices) {
            val cellId = GridCellEntity.encodeCellId(
                GridCellEntity.gridLatFor(pointsA[idxA].lat),
                GridCellEntity.gridLonFor(pointsA[idxA].lon),
            )
            val candidatesB = cellsB[cellId]
            if (candidatesB != null) {
                // Find closest B point within threshold
                val nearestB = candidatesB.minByOrNull {
                    haversine(pointsA[idxA].lat, pointsA[idxA].lon, pointsB[it].lat, pointsB[it].lon)
                }
                if (nearestB != null) {
                    val dist = haversine(pointsA[idxA].lat, pointsA[idxA].lon, pointsB[nearestB].lat, pointsB[nearestB].lon)
                    if (dist < PROXIMITY_THRESHOLD_M) {
                        if (idxA == prevMatchA + 1 && runStartA >= 0) {
                            // Extend current run
                            runEndB = nearestB
                        } else {
                            // Save previous run if significant
                            if (runStartA >= 0) {
                                runs.add(Run(runStartA, prevMatchA, runStartB, runEndB))
                            }
                            // Start new run
                            runStartA = idxA
                            runStartB = nearestB
                            runEndB = nearestB
                        }
                        prevMatchA = idxA
                    }
                }
            }
        }
        // Save last run
        if (runStartA >= 0) {
            runs.add(Run(runStartA, prevMatchA, runStartB, runEndB))
        }

        // Filter runs by minimum distance and convert to candidates
        return runs.mapNotNull { run ->
            val distance = computeDistanceBetweenIndices(pointsA, run.startA, run.endA)
            if (distance >= MIN_OVERLAP_DISTANCE_M) {
                OverlapCandidate(
                    startIdxA = run.startA,
                    endIdxA = run.endA,
                    startIdxB = run.startB,
                    endIdxB = run.endB,
                    overlapDistanceM = distance,
                    startLat = pointsA[run.startA].lat,
                    startLon = pointsA[run.startA].lon,
                    endLat = pointsA[run.endA].lat,
                    endLon = pointsA[run.endA].lon,
                )
            } else null
        }
    }

    /** Compute run stats from track points between two indices. */
    fun computeRunStats(
        points: List<TrackPointEntity>,
        startIdx: Int,
        endIdx: Int,
    ): RunStats {
        val start = startIdx.coerceIn(0, points.lastIndex)
        val end = endIdx.coerceIn(start, points.lastIndex)
        if (start >= end) return RunStats(0L, 0.0, 0.0)

        val durationMs = points[end].timestamp - points[start].timestamp
        val speeds = (start..end).mapNotNull { points[it].speed }.filter { it > 0 }
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0
        val maxSpeed = speeds.maxOrNull() ?: 0.0

        return RunStats(durationMs, avgSpeed, maxSpeed)
    }

    data class RunStats(val durationMs: Long, val avgSpeedMps: Double, val maxSpeedMps: Double)

    // ── Utility ─────────────────────────────────────────────────────────

    private fun findNearestWithinThreshold(
        points: List<TrackPointEntity>,
        lat: Double,
        lon: Double,
        thresholdM: Double,
    ): Int {
        var bestIdx = -1
        var bestDist = thresholdM

        for (i in points.indices) {
            val d = haversine(lat, lon, points[i].lat, points[i].lon)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        return bestIdx
    }

    private fun computeDistanceBetweenIndices(
        points: List<TrackPointEntity>,
        startIdx: Int,
        endIdx: Int,
    ): Double {
        var dist = 0.0
        for (i in startIdx until endIdx) {
            dist += haversine(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon)
        }
        return dist
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
