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
    /**
     * Driver speed profile: maps radius bucket (in metres, rounded to nearest 10)
     * to the driver's average speed (m/s) through corners of that radius.
     * Used for adaptive pace note call timing.
     */
    private val driverProfile: Map<Int, Double>? = null,
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

            // Pre-call distance: trigger based on speed and driver profile
            val preCallDistance = computePreCallDistance(speedMps, candidate)

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
     * Adaptive pre-call distance based on current speed, lookahead time, and driver profile.
     *
     * When a driver profile is available and the upcoming note has a turn radius, the
     * engine looks up the driver's typical speed through that radius bucket. If the
     * driver will need to slow down significantly, the call is issued earlier to give
     * more reaction time. Without a profile, falls back to the base calculation.
     *
     * Base formula: distance = speed * lookaheadSeconds, clamped to [40m, 400m].
     * Adaptive formula extends the upper clamp to 500m when extra braking time is needed.
     */
    private fun computePreCallDistance(speedMps: Double, nextNote: PaceNoteEntity?): Double {
        val baseDistance = speedMps * lookaheadSeconds

        if (driverProfile == null || nextNote == null) {
            return baseDistance.coerceIn(40.0, 400.0)
        }

        val turnRadius = nextNote.turnRadiusM
            ?: return baseDistance.coerceIn(40.0, 400.0)

        val radiusBucket = (turnRadius / 10.0).toInt().coerceAtLeast(1) * 10 // Round to nearest 10m, min 10m
        val expectedSpeed = driverProfile[radiusBucket]

        if (expectedSpeed != null && expectedSpeed < speedMps) {
            // Driver needs to slow down — call earlier to give more reaction time
            val speedDelta = speedMps - expectedSpeed
            val extraTime = (speedDelta / speedMps) * lookaheadSeconds * 0.5
            return ((lookaheadSeconds + extraTime) * speedMps).coerceIn(40.0, 500.0)
        }

        return baseDistance.coerceIn(40.0, 400.0)
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

    // ── Ghost replay support ───────────────────────────────────────────────

    data class GhostState(
        val position: LatLng?,
        val deltaMs: Long,
        val ghostProgress: Float,
    )

    private var cachedGhostPoints: List<TrackPointEntity> = emptyList()
    private var ghostCumulativeDistances: DoubleArray = DoubleArray(0)
    private var ghostTotalDistance: Double = 0.0

    /**
     * Pre-compute cumulative distances for a ghost track so that
     * [getGhostPosition] avoids redundant haversine work on every GPS update.
     */
    fun setGhostTrack(ghostPoints: List<TrackPointEntity>) {
        cachedGhostPoints = ghostPoints
        ghostCumulativeDistances = DoubleArray(ghostPoints.size)
        for (i in 1 until ghostPoints.size) {
            ghostCumulativeDistances[i] = ghostCumulativeDistances[i - 1] + haversine(
                ghostPoints[i - 1].lat, ghostPoints[i - 1].lon,
                ghostPoints[i].lat, ghostPoints[i].lon,
            )
        }
        ghostTotalDistance = if (ghostPoints.isNotEmpty()) ghostCumulativeDistances.last() else 0.0
    }

    /**
     * Compute the ghost's position on a personal-best track at the same distance
     * as the driver's current progress on the main track.
     *
     * @param currentDistanceM distance the driver has covered on the main track
     * @param elapsedMs        wall-clock time since the driver started the replay
     * @return [GhostState] with interpolated position and time delta
     */
    fun getGhostPosition(
        currentDistanceM: Double,
        elapsedMs: Long,
    ): GhostState {
        val points = cachedGhostPoints
        if (points.size < 2 || ghostTotalDistance <= 0.0) return GhostState(null, 0L, 0f)

        val clampedDist = currentDistanceM.coerceIn(0.0, ghostTotalDistance)

        // Binary search for the segment containing clampedDist
        var lo = 0
        var hi = points.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (ghostCumulativeDistances[mid] <= clampedDist) lo = mid else hi = mid
        }

        // Interpolate position between points[lo] and points[hi]
        val segLen = ghostCumulativeDistances[hi] - ghostCumulativeDistances[lo]
        val frac = if (segLen > 0) ((clampedDist - ghostCumulativeDistances[lo]) / segLen).coerceIn(0.0, 1.0) else 0.0
        val lat = points[lo].lat + frac * (points[hi].lat - points[lo].lat)
        val lon = points[lo].lon + frac * (points[hi].lon - points[lo].lon)

        // Interpolate ghost timestamp at this distance
        val ghostTimeAtDist = points[lo].timestamp + (frac * (points[hi].timestamp - points[lo].timestamp)).toLong()
        val ghostElapsedAtDist = ghostTimeAtDist - points.first().timestamp

        // Positive delta means the driver is slower than the ghost (behind PB)
        val deltaMs = elapsedMs - ghostElapsedAtDist

        val ghostProgress = (clampedDist / ghostTotalDistance).toFloat().coerceIn(0f, 1f)

        return GhostState(LatLng(lat, lon), deltaMs, ghostProgress)
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
