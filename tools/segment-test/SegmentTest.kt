import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.pacenotes.PaceNoteGenerator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun main() {
    println("=" .repeat(110))
    println("PACE NOTE SEGMENT ANALYSIS")
    println("=".repeat(110))

    println("\n\n>>> TAIL OF THE DRAGON (US-129 style — tight alternating curves)")
    runAnalysis("dragon", tailOfTheDragonTrace(), PaceNoteGenerator.Sensitivity.HIGH)

    println("\n\n>>> MOUNTAIN SWITCHBACK (Stelvio Pass style — hairpins + straights)")
    runAnalysis("switchback", mountainSwitchbackTrace(), PaceNoteGenerator.Sensitivity.MEDIUM)

    println("\n\n>>> MIXED ROAD (highway sweepers + twisty section)")
    runAnalysis("mixed", mixedRoadTrace(), PaceNoteGenerator.Sensitivity.MEDIUM)

    println("\n\n>>> SENSITIVITY COMPARISON — Tail of the Dragon")
    sensitivityComparison()
}

// ── Trace generators ────────────────────────────────────────────────────

/**
 * Generates GPS trace from road geometry: list of (radius, arcLength, isLeft).
 * radius <= 0 = straight. Points emitted every [stepM] metres.
 */
fun generateTrace(
    segments: List<Triple<Double, Double, Boolean>>,
    startLat: Double, startLon: Double,
    startBearing: Double, stepM: Double = 2.0,
    trackId: String = "test",
): List<TrackPointEntity> {
    val points = mutableListOf<TrackPointEntity>()
    var lat = startLat; var lon = startLon
    var bearing = Math.toRadians(startBearing)
    var idx = 0; var t = 1_000_000_000L

    fun emit() {
        points += TrackPointEntity(trackId = trackId, index = idx++, lat = lat, lon = lon, timestamp = t)
        t += 100L
    }

    for ((radius, arcLength, isLeft) in segments) {
        if (radius <= 0) {
            val steps = (arcLength / stepM).toInt().coerceAtLeast(1)
            repeat(steps) {
                emit()
                lat += stepM * cos(bearing) / 111_319.5
                lon += stepM * sin(bearing) / (111_319.5 * cos(Math.toRadians(lat)))
            }
        } else {
            val totalAngle = arcLength / radius
            val steps = (arcLength / stepM).toInt().coerceAtLeast(1)
            val angleStep = totalAngle / steps
            val sign = if (isLeft) -1.0 else 1.0
            repeat(steps) {
                emit()
                bearing += sign * angleStep
                lat += stepM * cos(bearing) / 111_319.5
                lon += stepM * sin(bearing) / (111_319.5 * cos(Math.toRadians(lat)))
            }
        }
    }
    emit()
    return points
}

fun tailOfTheDragonTrace(): List<TrackPointEntity> {
    val segs = mutableListOf(
        // radius(m), arcLen(m), isLeft — mix of tight & moderate, alternating
        Triple(15.0, 25.0, true),   // tight hairpin L
        Triple(0.0, 12.0, false),
        Triple(20.0, 35.0, false),  // tight R
        Triple(0.0, 8.0, false),
        Triple(35.0, 50.0, true),   // moderate L
        Triple(0.0, 15.0, false),
        Triple(10.0, 18.0, false),  // hairpin R
        Triple(0.0, 10.0, false),
        Triple(45.0, 55.0, true),   // moderate L
        Triple(25.0, 40.0, false),  // tight R
        Triple(0.0, 5.0, false),
        Triple(30.0, 45.0, true),   // moderate L
        Triple(0.0, 20.0, false),
        Triple(8.0, 15.0, true),    // hairpin L
        Triple(0.0, 8.0, false),
        Triple(12.0, 22.0, false),  // square R
        Triple(0.0, 12.0, false),
        Triple(60.0, 70.0, true),   // easy L
        Triple(0.0, 25.0, false),
        Triple(18.0, 30.0, false),  // tight R
        Triple(0.0, 6.0, false),
        Triple(22.0, 35.0, true),   // tight L
        Triple(0.0, 10.0, false),
        Triple(5.0, 12.0, false),   // extreme hairpin R
        Triple(0.0, 15.0, false),
        Triple(40.0, 50.0, true),   // moderate L
        Triple(0.0, 8.0, false),
        Triple(55.0, 65.0, false),  // easy R
        Triple(0.0, 30.0, false),
        Triple(28.0, 42.0, true),   // moderate-tight L
        Triple(15.0, 25.0, false),  // tight R
        Triple(0.0, 5.0, false),
        Triple(18.0, 30.0, true),   // tight L
        Triple(0.0, 12.0, false),
        Triple(70.0, 80.0, false),  // gentle R
        Triple(0.0, 18.0, false),
        Triple(12.0, 20.0, true),   // tight L
        Triple(0.0, 8.0, false),
        Triple(25.0, 38.0, false),  // moderate-tight R
        Triple(0.0, 14.0, false),
        Triple(9.0, 16.0, true),    // hairpin L
        Triple(0.0, 10.0, false),
        Triple(35.0, 48.0, false),  // moderate R
        Triple(0.0, 6.0, false),
        Triple(20.0, 32.0, true),   // tight L
        Triple(0.0, 20.0, false),
        Triple(50.0, 60.0, false),  // easy R
    )
    // Repeat ~1.3x for ~2 miles
    val extra = segs.toList().take(15)
    segs.addAll(extra)
    return generateTrace(segs, 35.4633, -83.9210, 30.0)
}

fun mountainSwitchbackTrace(): List<TrackPointEntity> {
    val segs = mutableListOf<Triple<Double, Double, Boolean>>()
    for (i in 0 until 12) {
        segs += Triple(0.0, 120.0 + (i % 3) * 30.0, false)
        val r = 8.0 + (i % 4) * 2.0
        segs += Triple(r, r * PI, i % 2 == 0)
    }
    segs += Triple(0.0, 150.0, false)
    return generateTrace(segs, 46.5287, 10.4534, 90.0, trackId = "switchback")
}

fun mixedRoadTrace(): List<TrackPointEntity> {
    val segs = listOf(
        Triple(0.0, 50.0, false),
        Triple(100.0, 120.0, true),  // gentle L sweeper
        Triple(0.0, 300.0, false),   // long straight
        Triple(30.0, 45.0, false),   // moderate R
        Triple(0.0, 10.0, false),
        Triple(25.0, 38.0, true),    // moderate-tight L
        Triple(0.0, 8.0, false),
        Triple(40.0, 55.0, false),   // moderate R
        Triple(0.0, 15.0, false),
        Triple(15.0, 25.0, true),    // tight L
        Triple(0.0, 10.0, false),
        Triple(20.0, 32.0, false),   // tight R
        Triple(0.0, 250.0, false),   // long straight
        Triple(80.0, 95.0, true),    // easy L
        Triple(0.0, 20.0, false),
        Triple(90.0, 100.0, false),  // easy R
        Triple(0.0, 50.0, false),
        Triple(120.0, 140.0, true),  // very gentle L
        Triple(0.0, 100.0, false),
    )
    return generateTrace(segs, 36.1, -81.8, 0.0, trackId = "mixed")
}

// ── Haversine ───────────────────────────────────────────────────────────

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun segLen(note: PaceNoteEntity, pts: List<TrackPointEntity>): Double {
    val s = note.segmentStartIndex ?: return -1.0
    val e = note.segmentEndIndex ?: return -1.0
    var d = 0.0
    for (j in s until e.coerceAtMost(pts.size - 1)) {
        d += haversine(pts[j].lat, pts[j].lon, pts[j + 1].lat, pts[j + 1].lon)
    }
    return d
}

// ── Analysis ────────────────────────────────────────────────────────────

fun runAnalysis(name: String, points: List<TrackPointEntity>, sens: PaceNoteGenerator.Sensitivity) {
    val totalDist = (0 until points.size - 1).sumOf {
        haversine(points[it].lat, points[it].lon, points[it + 1].lat, points[it + 1].lon)
    }
    println("Track points: ${points.size}  |  Distance: %.0f m (%.2f mi)".format(totalDist, totalDist / 1609.34))

    val notes = PaceNoteGenerator.generate(name, points, sens)
    val turns = notes.filter { it.noteType != NoteType.STRAIGHT }
    val straights = notes.filter { it.noteType == NoteType.STRAIGHT }

    println("Notes: ${notes.size} total  |  ${turns.size} turns  |  ${straights.size} straights")
    println()

    // Table header
    val hdr = "%-4s  %-16s  Sev  %-12s  %-8s  %6s  %6s  %6s  %8s  %s"
    println(hdr.format("#", "Type", "Modifier", "Conjunc", "SegSt", "Apex", "SegEnd", "SegLen", "CallText"))
    println("-".repeat(115))

    notes.forEachIndexed { i, n ->
        val sl = segLen(n, points)
        val slStr = if (sl >= 0) "%.1fm".format(sl) else "n/a"
        println(hdr.format(
            (i + 1).toString(), n.noteType, n.severity,
            n.modifier, n.conjunction,
            n.segmentStartIndex?.toString() ?: "-",
            n.pointIndex.toString(),
            n.segmentEndIndex?.toString() ?: "-",
            slStr, n.callText
        ))
    }

    // Stats
    val segLens = turns.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }
        .map { it to segLen(it, points) }.filter { it.second > 0 }

    if (segLens.isNotEmpty()) {
        val lens = segLens.map { it.second }.sorted()
        println("\n--- Segment Length Stats ---")
        println("  Min: %6.1f m  |  Max: %6.1f m  |  Avg: %6.1f m  |  Median: %6.1f m".format(
            lens.min(), lens.max(), lens.average(), lens[lens.size / 2]
        ))

        val buckets = listOf(0.0 to 10.0, 10.0 to 20.0, 20.0 to 30.0, 30.0 to 50.0,
            50.0 to 80.0, 80.0 to 120.0, 120.0 to 999.0)
        val labels = listOf("0-10m", "10-20m", "20-30m", "30-50m", "50-80m", "80-120m", "120m+")
        println("\n  Histogram:")
        buckets.forEachIndexed { idx, (lo, hi) ->
            val c = lens.count { it >= lo && it < hi }
            println("    %-8s %3d  %s".format(labels[idx], c, "#".repeat(c)))
        }

        println("\n--- By Severity ---")
        for (sev in 1..6) {
            val ofSev = segLens.filter { it.first.severity == sev }
            if (ofSev.isNotEmpty()) {
                val l = ofSev.map { it.second }
                val radii = ofSev.mapNotNull { it.first.turnRadiusM }
                println("  Grade %d: %3d notes  seg=%5.1f-%5.1f m (avg %5.1f)  radius=%5.1f-%5.1f m".format(
                    sev, ofSev.size, l.min(), l.max(), l.average(),
                    radii.minOrNull() ?: 0.0, radii.maxOrNull() ?: 0.0
                ))
            }
        }

        println("\n--- By Type ---")
        for (type in NoteType.values()) {
            val ofType = segLens.filter { it.first.noteType == type }
            if (ofType.isNotEmpty()) {
                val l = ofType.map { it.second }
                println("  %-16s %3d notes  seg=%5.1f-%5.1f m (avg %5.1f)".format(
                    type, ofType.size, l.min(), l.max(), l.average()
                ))
            }
        }
    }
}

fun sensitivityComparison() {
    val points = tailOfTheDragonTrace()
    for ((label, sens) in listOf(
        "LOW" to PaceNoteGenerator.Sensitivity.LOW,
        "MEDIUM" to PaceNoteGenerator.Sensitivity.MEDIUM,
        "HIGH" to PaceNoteGenerator.Sensitivity.HIGH,
    )) {
        val notes = PaceNoteGenerator.generate("dragon", points, sens)
        val turns = notes.filter { it.noteType != NoteType.STRAIGHT }
        val segLens = turns.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }
            .map { segLen(it, points) }.filter { it > 0 }.sorted()

        println("\n  %-6s: %3d notes (%3d turns)".format(label, notes.size, turns.size))
        if (segLens.isNotEmpty()) {
            println("         seg: min=%5.1f  avg=%5.1f  max=%5.1f  median=%5.1f m".format(
                segLens.min(), segLens.average(), segLens.max(), segLens[segLens.size / 2]
            ))
        }
    }
}
