package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pace note generation engine.
 *
 * Pipeline:
 * 1. Smoothing (RDP simplification + Gaussian bearing smoothing)
 * 2. Cumulative distance & bearing computation
 * 3. Bearing delta analysis (50m windows, configurable threshold)
 * 4. Turn classification (severity 1-6 by angular deflection)
 * 5. Modifier detection (tightens / opens / long)
 * 6. Straight detection (>200m gap between notes)
 * 7. Elevation events (crests / dips from gradient reversals, min 4m)
 * 8. Call-distance computation (80–300m)
 * 9. Text assembly
 */
object PaceNoteGenerator {

    /** Sensitivity presets – lower value = more notes generated */
    data class Sensitivity(
        val bearingThresholdDeg: Double = 15.0,
        val rdpEpsilon: Double = 5.0,
        val minStraightM: Double = 200.0,
        val minElevationChangeM: Double = 4.0,
    ) {
        companion object {
            val LOW = Sensitivity(bearingThresholdDeg = 25.0, rdpEpsilon = 8.0, minStraightM = 300.0, minElevationChangeM = 6.0)
            val MEDIUM = Sensitivity()
            val HIGH = Sensitivity(bearingThresholdDeg = 10.0, rdpEpsilon = 3.0, minStraightM = 150.0, minElevationChangeM = 3.0)
        }
    }

    fun generate(
        trackId: String,
        points: List<TrackPointEntity>,
        sensitivity: Sensitivity = Sensitivity.MEDIUM,
    ): List<PaceNoteEntity> {
        if (points.size < 10) return emptyList()

        // 1. RDP simplification
        val simplified = rdpSimplify(points, sensitivity.rdpEpsilon)
        if (simplified.size < 3) return emptyList()

        // 2. Compute cumulative distances and bearings
        val distances = computeCumulativeDistances(simplified)
        val bearings = computeBearings(simplified)

        // 3. Gaussian-smooth bearings
        val smoothedBearings = gaussianSmoothBearings(bearings, sigma = 3)

        // 4. Find turns via bearing delta analysis
        val rawNotes = mutableListOf<PaceNoteEntity>()
        detectTurns(trackId, simplified, distances, smoothedBearings, sensitivity, rawNotes)

        // 5. Detect elevation events
        detectElevationEvents(trackId, simplified, distances, sensitivity, rawNotes)

        // 6. Sort by distance
        rawNotes.sortBy { it.distanceFromStart }

        // 7. Insert straights
        val withStraights = insertStraights(trackId, rawNotes, distances, sensitivity)

        // 8. Compute call distances
        val withCallDistances = computeCallDistances(withStraights)

        // 9. Assemble call text
        return withCallDistances.map { note ->
            note.copy(callText = assembleCallText(note))
        }
    }

    // ── RDP Simplification ──────────────────────────────────────────────

    private fun rdpSimplify(
        points: List<TrackPointEntity>,
        epsilon: Double,
    ): List<TrackPointEntity> {
        if (points.size < 3) return points

        // Convert epsilon from meters to approximate degree threshold
        val epsDeg = epsilon / 111_000.0

        return rdpRecursive(points, epsDeg)
    }

    private fun rdpRecursive(
        points: List<TrackPointEntity>,
        epsilon: Double,
    ): List<TrackPointEntity> {
        if (points.size < 3) return points

        var maxDist = 0.0
        var maxIndex = 0

        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                maxIndex = i
            }
        }

        return if (maxDist > epsilon) {
            val left = rdpRecursive(points.subList(0, maxIndex + 1), epsilon)
            val right = rdpRecursive(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(
        point: TrackPointEntity,
        lineStart: TrackPointEntity,
        lineEnd: TrackPointEntity,
    ): Double {
        val dx = lineEnd.lon - lineStart.lon
        val dy = lineEnd.lat - lineStart.lat

        if (dx == 0.0 && dy == 0.0) {
            val pdx = point.lon - lineStart.lon
            val pdy = point.lat - lineStart.lat
            return sqrt(pdx * pdx + pdy * pdy)
        }

        val t = ((point.lon - lineStart.lon) * dx + (point.lat - lineStart.lat) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)

        val closestX = lineStart.lon + clampedT * dx
        val closestY = lineStart.lat + clampedT * dy

        val resultDx = point.lon - closestX
        val resultDy = point.lat - closestY

        return sqrt(resultDx * resultDx + resultDy * resultDy)
    }

    // ── Distance & Bearing ──────────────────────────────────────────────

    private fun computeCumulativeDistances(points: List<TrackPointEntity>): DoubleArray {
        val distances = DoubleArray(points.size)
        for (i in 1 until points.size) {
            distances[i] = distances[i - 1] + haversine(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon,
            )
        }
        return distances
    }

    private fun computeBearings(points: List<TrackPointEntity>): DoubleArray {
        val bearings = DoubleArray(points.size)
        for (i in 0 until points.size - 1) {
            bearings[i] = bearing(
                points[i].lat, points[i].lon,
                points[i + 1].lat, points[i + 1].lon,
            )
        }
        if (points.size > 1) {
            bearings[points.size - 1] = bearings[points.size - 2]
        }
        return bearings
    }

    private fun gaussianSmoothBearings(bearings: DoubleArray, sigma: Int): DoubleArray {
        val smoothed = DoubleArray(bearings.size)
        val kernelSize = sigma * 3
        for (i in bearings.indices) {
            var sinSum = 0.0
            var cosSum = 0.0
            var weightSum = 0.0
            for (j in -kernelSize..kernelSize) {
                val idx = (i + j).coerceIn(0, bearings.size - 1)
                val weight = exp(-0.5 * (j.toDouble() / sigma) * (j.toDouble() / sigma))
                val rad = Math.toRadians(bearings[idx])
                sinSum += sin(rad) * weight
                cosSum += cos(rad) * weight
                weightSum += weight
            }
            smoothed[i] = (Math.toDegrees(atan2(sinSum / weightSum, cosSum / weightSum)) + 360) % 360
        }
        return smoothed
    }

    // ── Turn Detection ──────────────────────────────────────────────────

    private fun detectTurns(
        trackId: String,
        points: List<TrackPointEntity>,
        distances: DoubleArray,
        bearings: DoubleArray,
        sensitivity: Sensitivity,
        output: MutableList<PaceNoteEntity>,
    ) {
        val windowM = 50.0
        var i = 0

        while (i < points.size - 1) {
            // Find window end
            var j = i + 1
            while (j < points.size && (distances[j] - distances[i]) < windowM) {
                j++
            }
            if (j >= points.size) break

            val bearingDelta = normalizeDelta(bearings[j] - bearings[i])
            val absDelta = abs(bearingDelta)

            if (absDelta >= sensitivity.bearingThresholdDeg) {
                // Found a turn – extend to find full turn extent
                val turnStart = i
                var turnEnd = j
                var totalDelta = bearingDelta

                // Keep extending while turn continues in same direction
                while (turnEnd < points.size - 1) {
                    var nextJ = turnEnd + 1
                    while (nextJ < points.size && (distances[nextJ] - distances[turnEnd]) < windowM) {
                        nextJ++
                    }
                    if (nextJ >= points.size) break

                    val nextDelta = normalizeDelta(bearings[nextJ] - bearings[turnEnd])
                    // Same direction and still significant
                    if ((nextDelta > 0 == totalDelta > 0) && abs(nextDelta) >= sensitivity.bearingThresholdDeg * 0.5) {
                        totalDelta += nextDelta
                        turnEnd = nextJ
                    } else {
                        break
                    }
                }

                val totalAbsDelta = abs(totalDelta)
                val isLeft = totalDelta < 0
                val turnMidIdx = (turnStart + turnEnd) / 2
                val midPointIdx = turnMidIdx.coerceIn(0, points.size - 1)

                // Classify severity (1 = tightest, 6 = fastest)
                val severity = classifySeverity(totalAbsDelta)
                val noteType = classifyTurnType(isLeft, severity)

                // Detect modifier
                val modifier = detectTurnModifier(
                    points, distances, bearings,
                    turnStart, turnEnd, totalDelta,
                )

                // Find closest original point index
                val originalPointIndex = points[midPointIdx].index

                output.add(
                    PaceNoteEntity(
                        trackId = trackId,
                        pointIndex = originalPointIndex,
                        distanceFromStart = distances[midPointIdx],
                        noteType = noteType,
                        severity = severity,
                        modifier = modifier,
                        callText = "", // assembled later
                    )
                )

                i = turnEnd + 1
            } else {
                i++
            }
        }
    }

    private fun classifySeverity(absDeltaDeg: Double): Int {
        return when {
            absDeltaDeg >= 150 -> 1 // Hairpin
            absDeltaDeg >= 120 -> 1
            absDeltaDeg >= 90 -> 2
            absDeltaDeg >= 70 -> 3
            absDeltaDeg >= 50 -> 4
            absDeltaDeg >= 30 -> 5
            else -> 6
        }
    }

    private fun classifyTurnType(isLeft: Boolean, severity: Int): NoteType {
        return if (severity <= 1) {
            if (isLeft) NoteType.HAIRPIN_LEFT else NoteType.HAIRPIN_RIGHT
        } else {
            if (isLeft) NoteType.LEFT else NoteType.RIGHT
        }
    }

    private fun detectTurnModifier(
        points: List<TrackPointEntity>,
        distances: DoubleArray,
        bearings: DoubleArray,
        turnStart: Int,
        turnEnd: Int,
        totalDelta: Double,
    ): NoteModifier {
        if (turnEnd - turnStart < 3) return NoteModifier.NONE

        val mid = (turnStart + turnEnd) / 2
        val firstHalfDelta = abs(normalizeDelta(bearings[mid] - bearings[turnStart]))
        val secondHalfDelta = abs(normalizeDelta(bearings[turnEnd] - bearings[mid]))

        val turnLength = distances[turnEnd] - distances[turnStart]

        // Long turn > 100m
        if (turnLength > 100.0) return NoteModifier.LONG

        // Tightens: second half has more curvature
        if (secondHalfDelta > firstHalfDelta * 1.5) return NoteModifier.TIGHTENS

        // Opens: first half has more curvature
        if (firstHalfDelta > secondHalfDelta * 1.5) return NoteModifier.OPENS

        return NoteModifier.NONE
    }

    // ── Elevation Events ────────────────────────────────────────────────

    private fun detectElevationEvents(
        trackId: String,
        points: List<TrackPointEntity>,
        distances: DoubleArray,
        sensitivity: Sensitivity,
        output: MutableList<PaceNoteEntity>,
    ) {
        // Need elevation data
        val elevationPoints = points.mapIndexedNotNull { idx, p ->
            p.elevation?.let { Triple(idx, distances[idx], it) }
        }
        if (elevationPoints.size < 5) return

        // Find gradient reversals
        var prevGradient: Double? = null
        var prevElevation: Double? = null
        var prevDistance: Double? = null
        var accumulatedChange = 0.0
        var changeStartIdx = 0

        for (i in 1 until elevationPoints.size) {
            val (idx, dist, ele) = elevationPoints[i]
            val (prevIdx, prevDist, prevEle) = elevationPoints[i - 1]

            val segmentDist = dist - prevDist
            if (segmentDist < 1.0) continue

            val gradient = (ele - prevEle) / segmentDist

            if (prevGradient != null) {
                // Check for reversal
                val isReversal = (prevGradient > 0.02 && gradient < -0.02) ||
                    (prevGradient < -0.02 && gradient > 0.02)

                if (isReversal) {
                    val change = abs(ele - (prevElevation ?: ele))
                    if (change >= sensitivity.minElevationChangeM) {
                        val isCrest = prevGradient > 0
                        val noteType = if (isCrest) NoteType.CREST else NoteType.DIP

                        output.add(
                            PaceNoteEntity(
                                trackId = trackId,
                                pointIndex = points[prevIdx].index,
                                distanceFromStart = prevDist,
                                noteType = noteType,
                                severity = 0, // not applicable for elevation events
                                modifier = NoteModifier.NONE,
                                callText = "",
                            )
                        )
                    }
                    accumulatedChange = 0.0
                    changeStartIdx = i
                }
            }

            prevGradient = gradient
            prevElevation = ele
            prevDistance = dist
        }
    }

    // ── Straights ───────────────────────────────────────────────────────

    private fun insertStraights(
        trackId: String,
        notes: List<PaceNoteEntity>,
        distances: DoubleArray,
        sensitivity: Sensitivity,
    ): List<PaceNoteEntity> {
        if (notes.isEmpty()) return notes
        val result = mutableListOf<PaceNoteEntity>()

        // Check gap before first note
        if (notes.first().distanceFromStart > sensitivity.minStraightM) {
            val straightDist = notes.first().distanceFromStart
            result.add(
                PaceNoteEntity(
                    trackId = trackId,
                    pointIndex = 0,
                    distanceFromStart = 0.0,
                    noteType = NoteType.STRAIGHT,
                    severity = 0,
                    modifier = NoteModifier.NONE,
                    callText = "",
                )
            )
        }

        for (i in notes.indices) {
            result.add(notes[i])

            if (i < notes.size - 1) {
                val gap = notes[i + 1].distanceFromStart - notes[i].distanceFromStart
                if (gap > sensitivity.minStraightM) {
                    val midDist = notes[i].distanceFromStart + gap / 2
                    result.add(
                        PaceNoteEntity(
                            trackId = trackId,
                            pointIndex = notes[i].pointIndex,
                            distanceFromStart = midDist,
                            noteType = NoteType.STRAIGHT,
                            severity = 0,
                            modifier = NoteModifier.NONE,
                            callText = "",
                        )
                    )
                }
            }
        }

        return result
    }

    // ── Call Distance ───────────────────────────────────────────────────

    private fun computeCallDistances(notes: List<PaceNoteEntity>): List<PaceNoteEntity> {
        return notes.mapIndexed { i, note ->
            val callDist = if (i < notes.size - 1) {
                (notes[i + 1].distanceFromStart - note.distanceFromStart)
                    .coerceIn(0.0, 300.0)
            } else {
                0.0
            }
            note.copy(callDistanceM = callDist)
        }
    }

    // ── Text Assembly ───────────────────────────────────────────────────

    private fun assembleCallText(note: PaceNoteEntity): String {
        val parts = mutableListOf<String>()

        when (note.noteType) {
            NoteType.LEFT -> parts.add("Left ${note.severity}")
            NoteType.RIGHT -> parts.add("Right ${note.severity}")
            NoteType.HAIRPIN_LEFT -> parts.add("Hairpin Left")
            NoteType.HAIRPIN_RIGHT -> parts.add("Hairpin Right")
            NoteType.CREST -> parts.add("Crest")
            NoteType.DIP -> parts.add("Dip")
            NoteType.STRAIGHT -> {
                parts.add("Straight")
            }
        }

        when (note.modifier) {
            NoteModifier.TIGHTENS -> parts.add("tightens")
            NoteModifier.OPENS -> parts.add("opens")
            NoteModifier.LONG -> parts.add("long")
            NoteModifier.INTO -> parts.add("into")
            NoteModifier.OVER -> parts.add("over")
            NoteModifier.DONT_CUT -> parts.add("don't cut")
            NoteModifier.KEEP_IN -> parts.add("keep in")
            NoteModifier.NONE -> {}
        }

        // Append call distance
        if (note.callDistanceM > 0) {
            val roundedDist = (note.callDistanceM / 10).toInt() * 10
            if (roundedDist >= 80) {
                parts.add("${roundedDist}m")
            }
        }

        return parts.joinToString(" ")
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private fun normalizeDelta(delta: Double): Double {
        var d = delta % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val dLonR = Math.toRadians(lon2 - lon1)
        val y = sin(dLonR) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLonR)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
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
