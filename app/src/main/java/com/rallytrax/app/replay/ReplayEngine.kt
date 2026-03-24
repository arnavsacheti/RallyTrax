package com.rallytrax.app.replay

import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.recording.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-logic replay engine. Matches the driver's live position to the recorded polyline,
 * determines progress, decides which pace note to trigger next, and detects off-route.
 */
class ReplayEngine(
    private val trackPoints: List<TrackPointEntity>,
    private val paceNotes: List<PaceNoteEntity>,
    /** Configurable lookahead time in seconds (default 6s, range 3-12). */
    var lookaheadSeconds: Double = 6.0,
) {
    /** Cumulative distance at each track point index */
    private val cumulativeDistances: DoubleArray

    /** Total track length in metres */
    val totalDistance: Double

    /** Sorted pace notes by distance from start */
    private val sortedNotes: List<PaceNoteEntity> = paceNotes.sortedBy { it.distanceFromStart }

    /** Index of the next note to trigger */
    private var nextNoteIndex = 0

    /** Set of note IDs already spoken */
    private val spokenNoteIds = mutableSetOf<String>()

    /** Distance along the track the driver has reached */
    var currentProgress: Double = 0.0
        private set

    /** Closest point index on the polyline */
    var closestPointIndex: Int = 0
        private set

    /** Whether the driver has finished the track */
    var isFinished: Boolean = false
        private set

    init {
        cumulativeDistances = DoubleArray(trackPoints.size)
        for (i in 1 until trackPoints.size) {
            cumulativeDistances[i] = cumulativeDistances[i - 1] + haversine(
                trackPoints[i - 1].lat, trackPoints[i - 1].lon,
                trackPoints[i].lat, trackPoints[i].lon,
            )
        }
        totalDistance = if (trackPoints.isNotEmpty()) cumulativeDistances.last() else 0.0
    }

    data class UpdateResult(
        val noteToSpeak: PaceNoteEntity? = null,
        val isOffRoute: Boolean = false,
        val isFinished: Boolean = false,
        val progressFraction: Float = 0f,
        val distanceToNextNote: Double = Double.MAX_VALUE,
        val nextNote: PaceNoteEntity? = null,
        val snappedPosition: LatLng? = null,
        val closestPointIndex: Int = 0,
    )

    /**
     * Call on each GPS update with the driver's current position and speed.
     * Returns which pace note to speak (if any), off-route status, and progress.
     */
    fun update(
        driverLat: Double,
        driverLon: Double,
        speedMps: Double,
    ): UpdateResult {
        if (trackPoints.isEmpty() || isFinished) {
            return UpdateResult(isFinished = isFinished, progressFraction = 1f)
        }

        // 1. Snap driver position to nearest point on polyline
        val (nearestIdx, nearestDist) = findClosestPoint(driverLat, driverLon)
        closestPointIndex = nearestIdx
        currentProgress = cumulativeDistances[nearestIdx]

        val snappedPos = LatLng(trackPoints[nearestIdx].lat, trackPoints[nearestIdx].lon)
        val progressFraction = if (totalDistance > 0) {
            (currentProgress / totalDistance).toFloat().coerceIn(0f, 1f)
        } else 0f

        // 2. Off-route detection (> 200m from polyline)
        val isOffRoute = nearestDist > 200.0

        // 3. Finish detection (within 50m of track end)
        val distToEnd = haversine(
            driverLat, driverLon,
            trackPoints.last().lat, trackPoints.last().lon,
        )
        if (distToEnd <= 50.0 && currentProgress > totalDistance * 0.5) {
            isFinished = true
            return UpdateResult(
                isFinished = true,
                progressFraction = 1f,
                snappedPosition = snappedPos,
                closestPointIndex = nearestIdx,
            )
        }

        // 4. Determine next note and whether to trigger
        var noteToSpeak: PaceNoteEntity? = null
        var distToNextNote = Double.MAX_VALUE
        var nextNote: PaceNoteEntity? = null

        // Advance past any notes we've already passed
        while (nextNoteIndex < sortedNotes.size &&
            sortedNotes[nextNoteIndex].distanceFromStart < currentProgress - 20.0
        ) {
            nextNoteIndex++
        }

        if (nextNoteIndex < sortedNotes.size) {
            val candidate = sortedNotes[nextNoteIndex]
            nextNote = candidate
            distToNextNote = candidate.distanceFromStart - currentProgress

            // Pre-call distance: trigger based on speed (higher speed = earlier call)
            val preCallDistance = computePreCallDistance(speedMps)

            if (distToNextNote <= preCallDistance && candidate.id !in spokenNoteIds) {
                // Speed-based priority filtering: drop straights when approaching fast
                val shouldDrop = candidate.noteType == NoteType.STRAIGHT &&
                    speedMps > 30.0 && // > ~108 km/h
                    nextNoteIndex + 1 < sortedNotes.size &&
                    (sortedNotes[nextNoteIndex + 1].distanceFromStart - candidate.distanceFromStart) < 150.0

                if (!shouldDrop) {
                    noteToSpeak = candidate
                }
                spokenNoteIds.add(candidate.id)
                nextNoteIndex++

                // Update nextNote to the one after
                nextNote = if (nextNoteIndex < sortedNotes.size) {
                    sortedNotes[nextNoteIndex]
                } else null
                distToNextNote = nextNote?.let { it.distanceFromStart - currentProgress } ?: Double.MAX_VALUE
            }
        }

        return UpdateResult(
            noteToSpeak = if (isOffRoute) null else noteToSpeak,
            isOffRoute = isOffRoute,
            isFinished = false,
            progressFraction = progressFraction,
            distanceToNextNote = distToNextNote,
            nextNote = nextNote,
            snappedPosition = snappedPos,
            closestPointIndex = nearestIdx,
        )
    }

    /**
     * Pre-call distance: time-based lookahead.
     * distance = speed * lookaheadSeconds, clamped to [40m, 400m].
     *
     * Default 6s lookahead gives:
     * - At 20 km/h (~5.5 m/s): ~33m ahead (clamped to 40m)
     * - At 100 km/h (~27.8 m/s): ~167m ahead
     * - At 160 km/h (~44.4 m/s): ~267m ahead
     */
    private fun computePreCallDistance(speedMps: Double): Double {
        return (speedMps * lookaheadSeconds).coerceIn(40.0, 400.0)
    }

    /**
     * Finds the closest track point to the given lat/lon.
     * Returns (index, distance in meters).
     */
    private fun findClosestPoint(lat: Double, lon: Double): Pair<Int, Double> {
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE

        // Optimisation: search around the current closest point first
        val searchStart = (closestPointIndex - 50).coerceAtLeast(0)
        val searchEnd = (closestPointIndex + 200).coerceAtMost(trackPoints.size - 1)

        for (i in searchStart..searchEnd) {
            val d = haversine(lat, lon, trackPoints[i].lat, trackPoints[i].lon)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        // If the best is at the boundary, expand the search to the full track
        if (bestIdx == searchStart || bestIdx == searchEnd) {
            for (i in trackPoints.indices) {
                val d = haversine(lat, lon, trackPoints[i].lat, trackPoints[i].lon)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
        }

        return Pair(bestIdx, bestDist)
    }

    fun reset() {
        nextNoteIndex = 0
        spokenNoteIds.clear()
        currentProgress = 0.0
        closestPointIndex = 0
        isFinished = false
    }

    companion object {
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
}
