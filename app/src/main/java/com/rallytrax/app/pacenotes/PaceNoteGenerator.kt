package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.Conjunction
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.SeverityHalf
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pace note generation engine using circumscribed circle radius method.
 *
 * Pipeline:
 * 1. RDP pre-filter for GPS noise
 * 2. Interpolate to uniform ~3m segments
 * 3. Compute circumscribed circle radius at each point
 * 4. Segment turns (radius below straight threshold)
 * 5. Classify severity via g-force threshold table (1-6 with optional +/-)
 * 6. Detect modifiers (tightens/opens/long/short)
 * 7. Determine conjunctions (into/and/distance)
 * 8. Detect elevation events (small/normal/big crests and dips)
 * 9. Insert straights for long gaps
 * 10. Compute call distances and assemble text
 */
object PaceNoteGenerator {

    /** Retained for backward compatibility; only rdpEpsilon and minStraightM are used. */
    data class Sensitivity(
        val bearingThresholdDeg: Double = 15.0, // unused by radius method
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

    /** Interpolated point for uniform-spacing analysis. */
    private data class InterpPoint(
        val lat: Double,
        val lon: Double,
        val elevation: Double?,
        val cumulativeDistance: Double,
        val originalIndex: Int, // closest original TrackPointEntity index
    )

    /** Detected turn region. */
    private data class TurnRegion(
        val startIdx: Int, // index into interpolated array
        val endIdx: Int,
        val apexIdx: Int, // index of minimum radius (tightest point)
        val apexRadiusM: Double,
        val isLeft: Boolean,
        val entryAvgRadius: Double, // average radius of first half
        val exitAvgRadius: Double, // average radius of second half
        val arcLengthM: Double,
    )

    // ── Severity thresholds ──────────────────────────────────────────────
    // Derived from: speed at which 0.3g lateral accel is reached for given radius
    // v = sqrt(0.3 * 9.81 * r), then convert m/s to km/h

    private data class GradeBand(val minRadius: Double, val maxRadius: Double, val grade: Int)

    private val GRADE_BANDS = listOf(
        GradeBand(0.0, 7.0, 1),      // Hairpin: 0-30 km/h
        GradeBand(7.0, 12.0, 2),     // 30-40 km/h
        GradeBand(12.0, 22.0, 3),    // 40-50 km/h
        GradeBand(22.0, 43.0, 4),    // 50-70 km/h
        GradeBand(43.0, 71.0, 5),    // 70-90 km/h
        GradeBand(71.0, 148.0, 6),   // 90-130 km/h
    )
    private const val STRAIGHT_RADIUS_THRESHOLD = 148.0 // Above this = straight

    private const val INTERP_SEGMENT_M = 3.0 // Uniform segment length
    private const val RADIUS_NEIGHBOR_M = 10.0 // Look-ahead/behind for circumscribed circle
    private const val RADIUS_REFINE_THRESHOLD = 25.0 // Refine radius for turns tighter than this
    private const val MIN_NEIGHBOR_M = 6.0 // Floor for adaptive neighbor (2× interpolation step)
    private const val TURN_MERGE_GAP_M = 5.0 // Merge turn regions closer than this
    private const val MAX_RADIUS_CAP = 10000.0 // Cap for near-collinear points

    // Conjunction thresholds (gap between consecutive turn apexes)
    private const val INTO_THRESHOLD_M = 15.0
    private const val AND_THRESHOLD_M = 30.0

    fun generate(
        trackId: String,
        points: List<TrackPointEntity>,
        sensitivity: Sensitivity = Sensitivity.MEDIUM,
        halfStepEnabled: Boolean = false,
        useSpeedCalibration: Boolean = false,
        driverProfile: Map<Int, Double>? = null, // radiusBucket -> avgSpeedMps
    ): List<PaceNoteEntity> {
        if (points.size < 10) return emptyList()

        // 1. RDP pre-filter for GPS noise reduction
        val simplified = rdpSimplify(points, sensitivity.rdpEpsilon)
        if (simplified.size < 3) return emptyList()

        // 2. Interpolate to uniform ~3m segments
        val interpolated = interpolateUniform(simplified, INTERP_SEGMENT_M)
        if (interpolated.size < 10) return emptyList()

        // 3. Compute circumscribed circle radius and direction at each point
        val radii = computeRadii(interpolated, RADIUS_NEIGHBOR_M)
        val directions = computeDirections(interpolated, RADIUS_NEIGHBOR_M, radii)

        // 4. Segment turns (regions where radius < straight threshold)
        val turnRegions = segmentTurns(interpolated, radii, directions)

        // 5. Build pace notes from turn regions
        val rawNotes = mutableListOf<PaceNoteEntity>()
        for (turn in turnRegions) {
            val apexPt = interpolated[turn.apexIdx]
            var severity = radiusToGrade(turn.apexRadiusM)
            var severityHalf = if (halfStepEnabled) {
                radiusToHalfStep(turn.apexRadiusM, severity)
            } else {
                SeverityHalf.NONE
            }

            // Speed calibration: shift severity based on driver profile
            if (useSpeedCalibration && driverProfile != null) {
                val bucket = (turn.apexRadiusM / 10.0).toInt() * 10
                val driverSpeed = driverProfile[bucket]
                if (driverSpeed != null) {
                    val refSpeed = referenceSpeedForGrade(severity)
                    // If driver is consistently faster, shift severity up (less severe)
                    if (driverSpeed > refSpeed * 1.15) {
                        severity = (severity + 1).coerceAtMost(6)
                        severityHalf = SeverityHalf.NONE
                    } else if (driverSpeed < refSpeed * 0.85) {
                        severity = (severity - 1).coerceAtLeast(1)
                        severityHalf = SeverityHalf.NONE
                    }
                }
            }

            // Classify turn type
            val noteType = classifyTurnType(turn.isLeft, severity, turn.apexRadiusM)

            // Detect modifier (tightens/opens/long/short)
            val modifier = detectModifier(turn)

            rawNotes.add(
                PaceNoteEntity(
                    trackId = trackId,
                    pointIndex = apexPt.originalIndex,
                    distanceFromStart = apexPt.cumulativeDistance,
                    noteType = noteType,
                    severity = severity,
                    severityHalf = severityHalf,
                    modifier = modifier,
                    turnRadiusM = turn.apexRadiusM,
                    callText = "",
                    segmentStartIndex = interpolated[turn.startIdx].originalIndex,
                    segmentEndIndex = interpolated[turn.endIdx].originalIndex,
                )
            )
        }

        // 6. Detect elevation events
        detectElevationEvents(trackId, interpolated, sensitivity, rawNotes)

        // 7. Sort by distance
        rawNotes.sortBy { it.distanceFromStart }

        // 8. Determine conjunctions between consecutive turn notes
        val withConjunctions = applyConjunctions(rawNotes)

        // 9. Insert straights for long gaps
        val withStraights = insertStraights(trackId, withConjunctions, sensitivity)

        // 10. Compute call distances and assemble text
        val withCallDistances = computeCallDistances(withStraights)
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
        // epsilon is in meters; perpendicularDistance now returns meters
        return rdpRecursive(points, epsilon)
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

    /**
     * Perpendicular distance from [point] to line([lineStart], [lineEnd]) in metres.
     * Uses a local tangent-plane projection with latitude-corrected longitude scaling.
     */
    private fun perpendicularDistance(
        point: TrackPointEntity,
        lineStart: TrackPointEntity,
        lineEnd: TrackPointEntity,
    ): Double {
        val midLat = (lineStart.lat + lineEnd.lat) / 2.0
        val cosLat = cos(Math.toRadians(midLat))
        val mPerDegLat = 111_319.5
        val mPerDegLon = 111_319.5 * cosLat

        val dx = (lineEnd.lon - lineStart.lon) * mPerDegLon
        val dy = (lineEnd.lat - lineStart.lat) * mPerDegLat
        if (dx == 0.0 && dy == 0.0) {
            val pdx = (point.lon - lineStart.lon) * mPerDegLon
            val pdy = (point.lat - lineStart.lat) * mPerDegLat
            return sqrt(pdx * pdx + pdy * pdy)
        }
        val t = (((point.lon - lineStart.lon) * mPerDegLon * dx +
            (point.lat - lineStart.lat) * mPerDegLat * dy) / (dx * dx + dy * dy))
        val clampedT = t.coerceIn(0.0, 1.0)
        val closestX = lineStart.lon * mPerDegLon + clampedT * dx
        val closestY = lineStart.lat * mPerDegLat + clampedT * dy
        val resultDx = point.lon * mPerDegLon - closestX
        val resultDy = point.lat * mPerDegLat - closestY
        return sqrt(resultDx * resultDx + resultDy * resultDy)
    }

    // ── Interpolation ───────────────────────────────────────────────────

    private fun interpolateUniform(
        points: List<TrackPointEntity>,
        segmentM: Double,
    ): List<InterpPoint> {
        if (points.size < 2) return emptyList()

        val result = mutableListOf<InterpPoint>()
        var cumDist = 0.0
        var nextTargetDist = 0.0
        var segIdx = 0

        // Add first point
        result.add(InterpPoint(points[0].lat, points[0].lon, points[0].elevation, 0.0, points[0].index))

        for (i in 1 until points.size) {
            val segDist = haversine(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)

            // Skip GPS jumps (>200m between consecutive points)
            if (segDist > 200.0) {
                cumDist += segDist
                nextTargetDist = cumDist + segmentM
                result.add(InterpPoint(points[i].lat, points[i].lon, points[i].elevation, cumDist, points[i].index))
                continue
            }

            val segStart = cumDist
            val segEnd = cumDist + segDist

            // Insert interpolated points at uniform intervals within this segment
            while (nextTargetDist <= segEnd && segDist > 0.01) {
                val frac = (nextTargetDist - segStart) / segDist
                val lat = points[i - 1].lat + frac * (points[i].lat - points[i - 1].lat)
                val lon = points[i - 1].lon + frac * (points[i].lon - points[i - 1].lon)
                val ele = if (points[i - 1].elevation != null && points[i].elevation != null) {
                    points[i - 1].elevation!! + frac * (points[i].elevation!! - points[i - 1].elevation!!)
                } else {
                    points[i].elevation ?: points[i - 1].elevation
                }
                // Assign original index of the closer point
                val origIdx = if (frac < 0.5) points[i - 1].index else points[i].index
                result.add(InterpPoint(lat, lon, ele, nextTargetDist, origIdx))
                nextTargetDist += segmentM
            }

            cumDist = segEnd
        }

        return result
    }

    // ── Circumscribed Circle Radius ─────────────────────────────────────

    /**
     * Two-pass radius computation:
     * 1. Coarse pass with fixed [neighborM] — accurate for turns ≥ 10m radius.
     * 2. Refinement pass — for points where coarse radius < [RADIUS_REFINE_THRESHOLD],
     *    recompute with a shorter neighbor proportional to the coarse radius so that
     *    tight hairpins aren't diluted by points outside the turn.
     */
    private fun computeRadii(points: List<InterpPoint>, neighborM: Double): DoubleArray {
        if (points.size < 3) return DoubleArray(points.size) { MAX_RADIUS_CAP }

        // Pass 1: coarse radii
        val radii = DoubleArray(points.size) { i ->
            computeRadiusAtPoint(points, i, neighborM)
        }

        // Pass 2: refine tight turns with adaptive neighbor distance
        for (i in radii.indices) {
            if (radii[i] < RADIUS_REFINE_THRESHOLD && radii[i] < MAX_RADIUS_CAP) {
                val adaptiveNeighbor = (radii[i] * 0.8).coerceIn(MIN_NEIGHBOR_M, neighborM)
                radii[i] = computeRadiusAtPoint(points, i, adaptiveNeighbor)
            }
        }

        return radii
    }

    /** Circumscribed circle radius at a single point using neighbors at ±[neighborM]. */
    private fun computeRadiusAtPoint(points: List<InterpPoint>, i: Int, neighborM: Double): Double {
        val targetBefore = points[i].cumulativeDistance - neighborM
        val beforeIdx = findClosestByDistance(points, targetBefore, 0, i)

        val targetAfter = points[i].cumulativeDistance + neighborM
        val afterIdx = findClosestByDistance(points, targetAfter, i, points.lastIndex)

        if (beforeIdx == i || afterIdx == i || beforeIdx == afterIdx) return MAX_RADIUS_CAP

        return circumscribedRadius(
            points[beforeIdx].lat, points[beforeIdx].lon,
            points[i].lat, points[i].lon,
            points[afterIdx].lat, points[afterIdx].lon,
        )
    }

    /**
     * Compute turn direction using cross product of vectors (before→point, point→after).
     * Uses adaptive neighbor distance matching the radius computation so tight hairpins
     * get correct direction from nearby points rather than points outside the turn.
     */
    private fun computeDirections(points: List<InterpPoint>, neighborM: Double, radii: DoubleArray): DoubleArray {
        val dirs = DoubleArray(points.size)
        if (points.size < 3) return dirs

        for (i in points.indices) {
            // Use adaptive neighbor for tight turns (same logic as radius refinement)
            val effectiveNeighbor = if (radii[i] < RADIUS_REFINE_THRESHOLD && radii[i] < MAX_RADIUS_CAP) {
                (radii[i] * 0.8).coerceIn(MIN_NEIGHBOR_M, neighborM)
            } else {
                neighborM
            }

            val targetBefore = points[i].cumulativeDistance - effectiveNeighbor
            val beforeIdx = findClosestByDistance(points, targetBefore, 0, i)
            val targetAfter = points[i].cumulativeDistance + effectiveNeighbor
            val afterIdx = findClosestByDistance(points, targetAfter, i, points.lastIndex)

            if (beforeIdx == i || afterIdx == i) continue

            // Cross product in local tangent plane (approximate)
            val ax = points[i].lon - points[beforeIdx].lon
            val ay = points[i].lat - points[beforeIdx].lat
            val bx = points[afterIdx].lon - points[i].lon
            val by = points[afterIdx].lat - points[i].lat

            dirs[i] = ax * by - ay * bx // positive = left, negative = right
        }

        return dirs
    }

    private fun findClosestByDistance(
        points: List<InterpPoint>,
        targetDist: Double,
        searchStart: Int,
        searchEnd: Int,
    ): Int {
        var bestIdx = searchStart
        var bestDiff = abs(points[searchStart].cumulativeDistance - targetDist)

        for (i in searchStart..searchEnd) {
            val diff = abs(points[i].cumulativeDistance - targetDist)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }

        return bestIdx
    }

    /**
     * Circumscribed circle radius through 3 points (in meters).
     * R = (a * b * c) / (4 * area)
     */
    private fun circumscribedRadius(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        lat3: Double, lon3: Double,
    ): Double {
        val a = haversine(lat1, lon1, lat2, lon2)
        val b = haversine(lat2, lon2, lat3, lon3)
        val c = haversine(lat3, lon3, lat1, lon1)

        // Area via cross product (Heron's formula is less stable for flat triangles)
        // Using the local tangent plane approximation for area
        val cosLat = cos(Math.toRadians(lat2))
        val x1 = (lon1 - lon2) * cosLat * 111_319.5
        val y1 = (lat1 - lat2) * 111_319.5
        val x3 = (lon3 - lon2) * cosLat * 111_319.5
        val y3 = (lat3 - lat2) * 111_319.5

        val crossProduct = abs(x1 * y3 - x3 * y1)
        if (crossProduct < 0.01) return MAX_RADIUS_CAP // Nearly collinear

        val radius = (a * b * c) / (2.0 * crossProduct)
        return radius.coerceAtLeast(1.0) // Minimum 1m radius to avoid degenerate values
    }

    // ── Turn Segmentation ───────────────────────────────────────────────

    private fun segmentTurns(
        points: List<InterpPoint>,
        radii: DoubleArray,
        directions: DoubleArray,
    ): List<TurnRegion> {
        val regions = mutableListOf<TurnRegion>()
        var i = 0

        while (i < points.size) {
            if (radii[i] < STRAIGHT_RADIUS_THRESHOLD) {
                // Start of a turn region
                val regionStart = i
                var minRadius = radii[i]
                var minIdx = i

                // Extend while still in a turn
                while (i < points.size && radii[i] < STRAIGHT_RADIUS_THRESHOLD) {
                    if (radii[i] < minRadius) {
                        minRadius = radii[i]
                        minIdx = i
                    }
                    i++
                }
                val regionEnd = i - 1

                // Determine dominant direction from the apex region
                val isLeft = directions[minIdx] > 0

                // Compute entry/exit half average radii for tightens/opens
                val mid = (regionStart + regionEnd) / 2
                val entryAvg = if (mid > regionStart) {
                    radii.slice(regionStart..mid).average()
                } else radii[regionStart]
                val exitAvg = if (regionEnd > mid) {
                    radii.slice(mid..regionEnd).average()
                } else radii[regionEnd]

                val arcLength = points[regionEnd].cumulativeDistance - points[regionStart].cumulativeDistance

                regions.add(
                    TurnRegion(
                        startIdx = regionStart,
                        endIdx = regionEnd,
                        apexIdx = minIdx,
                        apexRadiusM = minRadius,
                        isLeft = isLeft,
                        entryAvgRadius = entryAvg,
                        exitAvgRadius = exitAvg,
                        arcLengthM = arcLength,
                    )
                )
            } else {
                i++
            }
        }

        // Merge adjacent turn regions that are very close (< TURN_MERGE_GAP_M)
        return mergeCloseRegions(regions, points)
    }

    private fun mergeCloseRegions(
        regions: List<TurnRegion>,
        points: List<InterpPoint>,
    ): List<TurnRegion> {
        if (regions.size < 2) return regions

        val merged = mutableListOf(regions.first())

        for (k in 1 until regions.size) {
            val prev = merged.last()
            val curr = regions[k]
            val gap = points[curr.startIdx].cumulativeDistance - points[prev.endIdx].cumulativeDistance

            if (gap < TURN_MERGE_GAP_M && prev.isLeft != curr.isLeft) {
                // Merge: extend the previous region
                val newApex = if (curr.apexRadiusM < prev.apexRadiusM) curr else prev
                val newArc = points[curr.endIdx].cumulativeDistance - points[prev.startIdx].cumulativeDistance
                val newMid = (prev.startIdx + curr.endIdx) / 2
                merged[merged.lastIndex] = TurnRegion(
                    startIdx = prev.startIdx,
                    endIdx = curr.endIdx,
                    apexIdx = newApex.apexIdx,
                    apexRadiusM = newApex.apexRadiusM,
                    isLeft = prev.isLeft,
                    entryAvgRadius = prev.entryAvgRadius,
                    exitAvgRadius = curr.exitAvgRadius,
                    arcLengthM = newArc,
                )
            } else {
                merged.add(curr)
            }
        }

        return merged
    }

    // ── Severity Classification ─────────────────────────────────────────

    private fun radiusToGrade(radiusM: Double): Int {
        for (band in GRADE_BANDS) {
            if (radiusM >= band.minRadius && radiusM < band.maxRadius) {
                return band.grade
            }
        }
        return 6 // Default to least severe turn grade
    }

    private fun radiusToHalfStep(radiusM: Double, grade: Int): SeverityHalf {
        val band = GRADE_BANDS.find { it.grade == grade } ?: return SeverityHalf.NONE
        val range = band.maxRadius - band.minRadius
        if (range <= 0) return SeverityHalf.NONE

        val position = (radiusM - band.minRadius) / range
        return when {
            position < 0.33 -> SeverityHalf.MINUS // tighter end of band
            position > 0.67 -> SeverityHalf.PLUS  // faster end of band
            else -> SeverityHalf.NONE
        }
    }

    /** Reference speed (m/s) for the midpoint of each severity grade. */
    private fun referenceSpeedForGrade(grade: Int): Double {
        return when (grade) {
            1 -> 6.9   // ~25 km/h
            2 -> 9.7   // ~35 km/h
            3 -> 12.5  // ~45 km/h
            4 -> 16.7  // ~60 km/h
            5 -> 22.2  // ~80 km/h
            6 -> 30.6  // ~110 km/h
            else -> 36.1 // ~130 km/h
        }
    }

    private fun classifyTurnType(isLeft: Boolean, severity: Int, radiusM: Double): NoteType {
        return when {
            severity <= 1 -> if (isLeft) NoteType.HAIRPIN_LEFT else NoteType.HAIRPIN_RIGHT
            radiusM in 7.0..12.0 -> if (isLeft) NoteType.SQUARE_LEFT else NoteType.SQUARE_RIGHT
            else -> if (isLeft) NoteType.LEFT else NoteType.RIGHT
        }
    }

    // ── Modifier Detection ──────────────────────────────────────────────

    private fun detectModifier(turn: TurnRegion): NoteModifier {
        // Duration-based modifiers take priority
        return when {
            turn.arcLengthM < 30.0 -> NoteModifier.SHORT
            turn.arcLengthM > 200.0 -> NoteModifier.VERY_LONG
            turn.arcLengthM > 100.0 -> NoteModifier.LONG
            // Radius change: compare entry vs exit half
            turn.exitAvgRadius < turn.entryAvgRadius * 0.7 -> NoteModifier.TIGHTENS
            turn.entryAvgRadius < turn.exitAvgRadius * 0.7 -> NoteModifier.OPENS
            else -> NoteModifier.NONE
        }
    }

    // ── Conjunctions ────────────────────────────────────────────────────

    private fun applyConjunctions(notes: List<PaceNoteEntity>): List<PaceNoteEntity> {
        if (notes.size < 2) return notes

        val result = mutableListOf<PaceNoteEntity>()
        for (i in notes.indices) {
            val note = notes[i]
            // Only apply conjunctions between turn notes (not elevation events)
            if (i > 0 && isTurnNote(note) && isTurnNote(notes[i - 1])) {
                val gap = note.distanceFromStart - notes[i - 1].distanceFromStart
                val conjunction = when {
                    gap < INTO_THRESHOLD_M -> Conjunction.INTO
                    gap < AND_THRESHOLD_M -> Conjunction.AND
                    else -> Conjunction.DISTANCE
                }
                // Apply conjunction to the previous note (it leads into this one)
                if (result.isNotEmpty()) {
                    result[result.lastIndex] = result.last().copy(conjunction = conjunction)
                }
            }
            result.add(note)
        }
        return result
    }

    private fun isTurnNote(note: PaceNoteEntity): Boolean {
        return note.noteType in listOf(
            NoteType.LEFT, NoteType.RIGHT,
            NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT,
            NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT,
        )
    }

    // ── Elevation Events ────────────────────────────────────────────────

    private fun detectElevationEvents(
        trackId: String,
        points: List<InterpPoint>,
        sensitivity: Sensitivity,
        output: MutableList<PaceNoteEntity>,
    ) {
        val elevationPoints = points.mapIndexedNotNull { idx, p ->
            p.elevation?.let { Triple(idx, p.cumulativeDistance, it) }
        }
        if (elevationPoints.size < 5) return

        var prevGradient: Double? = null
        var prevElevation: Double? = null

        for (i in 1 until elevationPoints.size) {
            val (idx, dist, ele) = elevationPoints[i]
            val (prevIdx, prevDist, prevEle) = elevationPoints[i - 1]

            val segmentDist = dist - prevDist
            if (segmentDist < 1.0) continue

            val gradient = (ele - prevEle) / segmentDist

            if (prevGradient != null) {
                val isReversal = (prevGradient > 0.02 && gradient < -0.02) ||
                    (prevGradient < -0.02 && gradient > 0.02)

                if (isReversal) {
                    val change = abs(ele - (prevElevation ?: ele))
                    if (change >= sensitivity.minElevationChangeM) {
                        val isCrest = prevGradient > 0
                        val noteType = when {
                            isCrest && change < 3.0 -> NoteType.SMALL_CREST
                            isCrest && change > 8.0 -> NoteType.BIG_CREST
                            isCrest -> NoteType.CREST
                            change < 3.0 -> NoteType.SMALL_DIP
                            change > 8.0 -> NoteType.BIG_DIP
                            else -> NoteType.DIP
                        }

                        // Compute elevation segment region (~30m each side)
                        val elevLookback = 10 // ~30m at 3m interpolation
                        val elevStart = (prevIdx - elevLookback).coerceAtLeast(0)
                        val elevEnd = (prevIdx + elevLookback).coerceAtMost(points.size - 1)

                        output.add(
                            PaceNoteEntity(
                                trackId = trackId,
                                pointIndex = points[prevIdx].originalIndex,
                                distanceFromStart = prevDist,
                                noteType = noteType,
                                severity = 0,
                                modifier = NoteModifier.NONE,
                                callText = "",
                                segmentStartIndex = points[elevStart].originalIndex,
                                segmentEndIndex = points[elevEnd].originalIndex,
                            )
                        )
                    }
                }
            }

            prevGradient = gradient
            prevElevation = ele
        }
    }

    // ── Straights ───────────────────────────────────────────────────────

    private fun insertStraights(
        trackId: String,
        notes: List<PaceNoteEntity>,
        sensitivity: Sensitivity,
    ): List<PaceNoteEntity> {
        if (notes.isEmpty()) return notes
        val result = mutableListOf<PaceNoteEntity>()

        // Gap before first note
        if (notes.first().distanceFromStart > sensitivity.minStraightM) {
            val endIdx = notes.first().segmentStartIndex ?: notes.first().pointIndex
            result.add(
                PaceNoteEntity(
                    trackId = trackId,
                    pointIndex = endIdx / 2, // midpoint for icon placement
                    distanceFromStart = 0.0,
                    noteType = NoteType.STRAIGHT,
                    severity = 0,
                    modifier = NoteModifier.NONE,
                    callText = "",
                    segmentStartIndex = 0,
                    segmentEndIndex = endIdx,
                )
            )
        }

        for (i in notes.indices) {
            result.add(notes[i])

            if (i < notes.size - 1) {
                val gap = notes[i + 1].distanceFromStart - notes[i].distanceFromStart
                if (gap > sensitivity.minStraightM) {
                    val midDist = notes[i].distanceFromStart + gap / 2
                    val segStart = notes[i].segmentEndIndex ?: notes[i].pointIndex
                    val segEnd = notes[i + 1].segmentStartIndex ?: notes[i + 1].pointIndex
                    result.add(
                        PaceNoteEntity(
                            trackId = trackId,
                            pointIndex = (segStart + segEnd) / 2,
                            distanceFromStart = midDist,
                            noteType = NoteType.STRAIGHT,
                            severity = 0,
                            modifier = NoteModifier.NONE,
                            callText = "",
                            segmentStartIndex = segStart,
                            segmentEndIndex = segEnd,
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
                    .coerceIn(0.0, 400.0)
            } else {
                0.0
            }
            note.copy(callDistanceM = callDist)
        }
    }

    // ── Text Assembly ───────────────────────────────────────────────────

    private fun assembleCallText(note: PaceNoteEntity): String {
        val parts = mutableListOf<String>()

        // Main note type and severity
        when (note.noteType) {
            NoteType.LEFT -> parts.add("Left ${formatSeverity(note)}")
            NoteType.RIGHT -> parts.add("Right ${formatSeverity(note)}")
            NoteType.HAIRPIN_LEFT -> parts.add("Hairpin Left")
            NoteType.HAIRPIN_RIGHT -> parts.add("Hairpin Right")
            NoteType.SQUARE_LEFT -> parts.add("Square Left")
            NoteType.SQUARE_RIGHT -> parts.add("Square Right")
            NoteType.CREST -> parts.add("Crest")
            NoteType.DIP -> parts.add("Dip")
            NoteType.SMALL_CREST -> parts.add("Small Crest")
            NoteType.SMALL_DIP -> parts.add("Small Dip")
            NoteType.BIG_CREST -> parts.add("Big Crest")
            NoteType.BIG_DIP -> parts.add("Big Dip")
            NoteType.STRAIGHT -> parts.add("Straight")
        }

        // Modifier
        when (note.modifier) {
            NoteModifier.TIGHTENS -> parts.add("tightens")
            NoteModifier.OPENS -> parts.add("opens")
            NoteModifier.LONG -> parts.add("long")
            NoteModifier.VERY_LONG -> parts.add("very long")
            NoteModifier.SHORT -> parts.add("short")
            NoteModifier.INTO -> parts.add("into")
            NoteModifier.OVER -> parts.add("over")
            NoteModifier.DONT_CUT -> parts.add("don't cut")
            NoteModifier.KEEP_IN -> parts.add("keep in")
            NoteModifier.NONE -> {}
        }

        // Conjunction / distance to next note
        when (note.conjunction) {
            Conjunction.INTO -> parts.add("into")
            Conjunction.AND -> parts.add("and")
            Conjunction.DISTANCE -> {
                if (note.callDistanceM > 0) {
                    val rounded = roundCallDistance(note.callDistanceM)
                    if (rounded >= 30) {
                        parts.add("${rounded}")
                    }
                }
            }
        }

        return parts.joinToString(" ")
    }

    /** Format severity with optional +/- half-step. */
    private fun formatSeverity(note: PaceNoteEntity): String {
        val base = note.severity.toString()
        return when (note.severityHalf) {
            SeverityHalf.PLUS -> "$base plus"
            SeverityHalf.MINUS -> "$base minus"
            SeverityHalf.NONE -> base
        }
    }

    /**
     * Round call distances per professional convention:
     * - Under 100m: use even numbers (30, 40, 50, 60, 70, 80)
     * - Over 100m: use increments of 50 (100, 150, 200, 250, 300, 350, 400)
     */
    private fun roundCallDistance(distanceM: Double): Int {
        return if (distanceM < 100) {
            ((distanceM / 10).toInt() * 10).coerceAtLeast(30)
        } else {
            ((distanceM / 50).toInt() * 50).coerceAtMost(400)
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────

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
