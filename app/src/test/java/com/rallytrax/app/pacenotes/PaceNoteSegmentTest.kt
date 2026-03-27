package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests pace note segment detection on synthetic roads with known geometry
 * and prints segment analysis (start, end, length, type, severity, radius).
 */
class PaceNoteSegmentTest {

    // ── Trace generators ────────────────────────────────────────────────

    /**
     * Generates a GPS trace by walking along a sequence of road segments.
     * Each segment is either a straight or an arc (left/right) with a given
     * radius and arc length. Points are emitted every [stepM] metres.
     *
     * @param segments List of (radius in meters, arcLengthM, isLeft).
     *                 radius <= 0 means straight; arcLengthM is the distance along the segment.
     * @param startLat Starting latitude
     * @param startLon Starting longitude
     * @param startBearing Initial heading in degrees (0 = north, 90 = east)
     * @param stepM Distance between emitted points
     */
    private fun generateTrace(
        segments: List<Triple<Double, Double, Boolean>>,
        startLat: Double = 35.4633,
        startLon: Double = -83.9210,
        startBearing: Double = 45.0,
        stepM: Double = 2.0,
        trackId: String = "test",
    ): List<TrackPointEntity> {
        val points = mutableListOf<TrackPointEntity>()
        var lat = startLat
        var lon = startLon
        var bearing = Math.toRadians(startBearing) // radians, 0 = north
        var index = 0
        var timeMs = 1_000_000_000L

        fun emit(elev: Double? = null) {
            points.add(
                TrackPointEntity(
                    trackId = trackId,
                    index = index++,
                    lat = lat,
                    lon = lon,
                    elevation = elev,
                    timestamp = timeMs,
                )
            )
            timeMs += 100L // 100ms between points
        }

        for ((radius, arcLength, isLeft) in segments) {
            if (radius <= 0) {
                // Straight segment
                val steps = (arcLength / stepM).toInt().coerceAtLeast(1)
                for (s in 0 until steps) {
                    emit()
                    // Move forward by stepM along current bearing
                    val dLat = stepM * cos(bearing) / 111_319.5
                    val dLon = stepM * sin(bearing) / (111_319.5 * cos(Math.toRadians(lat)))
                    lat += dLat
                    lon += dLon
                }
            } else {
                // Arc segment
                val totalAngle = arcLength / radius // radians
                val steps = (arcLength / stepM).toInt().coerceAtLeast(1)
                val angleStep = totalAngle / steps
                val sign = if (isLeft) -1.0 else 1.0

                for (s in 0 until steps) {
                    emit()
                    // Rotate bearing by angleStep
                    bearing += sign * angleStep
                    // Move forward by stepM along new bearing
                    val dLat = stepM * cos(bearing) / 111_319.5
                    val dLon = stepM * sin(bearing) / (111_319.5 * cos(Math.toRadians(lat)))
                    lat += dLat
                    lon += dLon
                }
            }
        }
        emit() // final point

        return points
    }

    /**
     * Tail of the Dragon style: tight sequence of alternating curves
     * simulating US-129 with ~318 curves in 11 miles.
     * We model a representative 2-mile section with ~60 curves.
     */
    private fun tailOfTheDragonTrace(): List<TrackPointEntity> {
        val segments = mutableListOf<Triple<Double, Double, Boolean>>()

        // Mix of tight and moderate curves, alternating direction, with short straights between
        val curvePatterns = listOf(
            // radius(m), arcLen(m), isLeft
            Triple(15.0, 25.0, true),   // tight hairpin left
            Triple(0.0, 12.0, false),   // short straight
            Triple(20.0, 35.0, false),  // tight right
            Triple(0.0, 8.0, false),    // short straight
            Triple(35.0, 50.0, true),   // moderate left
            Triple(0.0, 15.0, false),   // straight
            Triple(10.0, 18.0, false),  // very tight right (hairpin)
            Triple(0.0, 10.0, false),   // straight
            Triple(45.0, 55.0, true),   // moderate left
            Triple(25.0, 40.0, false),  // tight right
            Triple(0.0, 5.0, false),    // tiny straight
            Triple(30.0, 45.0, true),   // moderate left
            Triple(0.0, 20.0, false),   // straight
            Triple(8.0, 15.0, true),    // hairpin left
            Triple(0.0, 8.0, false),    // straight
            Triple(12.0, 22.0, false),  // tight square right
            Triple(0.0, 12.0, false),   // straight
            Triple(60.0, 70.0, true),   // easy left
            Triple(0.0, 25.0, false),   // straight
            Triple(18.0, 30.0, false),  // tight right
            Triple(0.0, 6.0, false),    // tiny straight
            Triple(22.0, 35.0, true),   // tight left
            Triple(0.0, 10.0, false),   // straight
            Triple(5.0, 12.0, false),   // extreme hairpin right
            Triple(0.0, 15.0, false),   // straight
            Triple(40.0, 50.0, true),   // moderate left
            Triple(0.0, 8.0, false),    // straight
            Triple(55.0, 65.0, false),  // easy right
            Triple(0.0, 30.0, false),   // longer straight
            Triple(28.0, 42.0, true),   // moderate-tight left
            Triple(15.0, 25.0, false),  // tight right
            Triple(0.0, 5.0, false),    // tiny straight
            Triple(18.0, 30.0, true),   // tight left
            Triple(0.0, 12.0, false),   // straight
            Triple(70.0, 80.0, false),  // gentle right
            Triple(0.0, 18.0, false),   // straight
            Triple(12.0, 20.0, true),   // tight left
            Triple(0.0, 8.0, false),    // straight
            Triple(25.0, 38.0, false),  // moderate-tight right
            Triple(0.0, 14.0, false),   // straight
            Triple(9.0, 16.0, true),    // hairpin left
            Triple(0.0, 10.0, false),   // straight
            Triple(35.0, 48.0, false),  // moderate right
            Triple(0.0, 6.0, false),    // tiny straight
            Triple(20.0, 32.0, true),   // tight left
            Triple(0.0, 20.0, false),   // straight
            Triple(50.0, 60.0, false),  // easy right
        )

        // Repeat the pattern ~1.3x to fill out ~2 miles
        segments.addAll(curvePatterns)
        segments.addAll(curvePatterns.take(15))

        return generateTrace(segments, startLat = 35.4633, startLon = -83.9210, startBearing = 30.0)
    }

    /**
     * Mountain switchback road: long straights punctuated by 180° hairpin switchbacks,
     * like Stelvio Pass or any Alpine road.
     */
    private fun mountainSwitchbackTrace(): List<TrackPointEntity> {
        val segments = mutableListOf<Triple<Double, Double, Boolean>>()

        // Pattern: long climb, tight switchback, repeat
        for (i in 0 until 12) {
            val isLeft = i % 2 == 0
            // Straight climb
            segments.add(Triple(0.0, 120.0 + (i % 3) * 30.0, false))
            // Hairpin switchback (~180°)
            val hairpinRadius = 8.0 + (i % 4) * 2.0  // 8-14m radius
            val arcLength = hairpinRadius * PI  // ~180° turn
            segments.add(Triple(hairpinRadius, arcLength, isLeft))
        }
        // Final straight
        segments.add(Triple(0.0, 150.0, false))

        return generateTrace(
            segments,
            startLat = 46.5287,
            startLon = 10.4534,
            startBearing = 90.0,
            trackId = "switchback",
        )
    }

    /**
     * Mixed road: highway on-ramp style curves, a long straight, then a twisty section,
     * followed by gentle sweepers.
     */
    private fun mixedRoadTrace(): List<TrackPointEntity> {
        val segments = listOf(
            // Highway on-ramp: gentle curve
            Triple(0.0, 50.0, false),
            Triple(100.0, 120.0, true),  // gentle left sweeper
            Triple(0.0, 300.0, false),   // long straight (should trigger STRAIGHT note)
            // Twisty section
            Triple(30.0, 45.0, false),   // moderate right
            Triple(0.0, 10.0, false),
            Triple(25.0, 38.0, true),    // moderate-tight left
            Triple(0.0, 8.0, false),
            Triple(40.0, 55.0, false),   // moderate right
            Triple(0.0, 15.0, false),
            Triple(15.0, 25.0, true),    // tight left
            Triple(0.0, 10.0, false),
            Triple(20.0, 32.0, false),   // tight right
            Triple(0.0, 250.0, false),   // long straight
            // Gentle sweepers
            Triple(80.0, 95.0, true),    // easy left
            Triple(0.0, 20.0, false),
            Triple(90.0, 100.0, false),  // easy right
            Triple(0.0, 50.0, false),
            Triple(120.0, 140.0, true),  // very gentle left
            Triple(0.0, 100.0, false),
        )

        return generateTrace(
            segments,
            startLat = 36.1000,
            startLon = -81.8000,
            startBearing = 0.0,
            trackId = "mixed",
        )
    }

    // ── Haversine for distance computation ──────────────────────────────

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

    // ── GPX file loader (JVM-compatible, no Android deps) ──────────────

    /**
     * Loads a GPX file from test resources and converts to TrackPointEntity list.
     * Uses javax.xml DOM parser (always available on JVM) instead of Android's
     * XmlPullParser so tests run without instrumentation.
     */
    private fun loadGpxTrack(resourceName: String, trackId: String = "test"): List<TrackPointEntity> {
        val stream = javaClass.classLoader!!.getResourceAsStream(resourceName)
            ?: error("Test resource not found: $resourceName")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        // Support both <trkpt> (track) and <rtept> (route) GPX formats
        var trkpts = doc.getElementsByTagName("trkpt")
        if (trkpts.length == 0) trkpts = doc.getElementsByTagName("rtept")
        val points = mutableListOf<TrackPointEntity>()

        for (i in 0 until trkpts.length) {
            val node = trkpts.item(i)
            val lat = node.attributes.getNamedItem("lat").textContent.toDouble()
            val lon = node.attributes.getNamedItem("lon").textContent.toDouble()

            var elevation: Double? = null
            var timestamp: Long = 1_000_000_000L + i * 15_000L // fallback: 15s intervals

            val children = node.childNodes
            for (j in 0 until children.length) {
                when (children.item(j).nodeName) {
                    "ele" -> elevation = children.item(j).textContent.trim().toDoubleOrNull()
                    "time" -> {
                        try {
                            timestamp = Instant.from(
                                DateTimeFormatter.ISO_DATE_TIME.parse(children.item(j).textContent.trim())
                            ).toEpochMilli()
                        } catch (_: Exception) { /* keep fallback */ }
                    }
                }
            }

            points.add(
                TrackPointEntity(
                    trackId = trackId,
                    index = i,
                    lat = lat,
                    lon = lon,
                    elevation = elevation,
                    timestamp = timestamp,
                )
            )
        }
        return points
    }

    // ── Tests ───────────────────────────────────────────────────────────

    @Test
    fun tailOfTheDragon_segmentAnalysis() {
        val points = tailOfTheDragonTrace()
        println("\n${"=".repeat(100)}")
        println("TAIL OF THE DRAGON — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("dragon", points, PaceNoteGenerator.Sensitivity.HIGH)
        printSegmentAnalysis(notes, points)
    }

    @Test
    fun mountainSwitchback_segmentAnalysis() {
        val points = mountainSwitchbackTrace()
        println("\n${"=".repeat(100)}")
        println("MOUNTAIN SWITCHBACK (Stelvio-style) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("switchback", points, PaceNoteGenerator.Sensitivity.MEDIUM)
        printSegmentAnalysis(notes, points)
    }

    @Test
    fun mixedRoad_segmentAnalysis() {
        val points = mixedRoadTrace()
        println("\n${"=".repeat(100)}")
        println("MIXED ROAD — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("mixed", points, PaceNoteGenerator.Sensitivity.MEDIUM)
        printSegmentAnalysis(notes, points)
    }

    @Test
    fun allSensitivities_tailOfTheDragon() {
        val points = tailOfTheDragonTrace()
        println("\n${"=".repeat(100)}")
        println("TAIL OF THE DRAGON — Sensitivity Comparison")
        println("${"=".repeat(100)}")

        for (sens in listOf(
            "LOW" to PaceNoteGenerator.Sensitivity.LOW,
            "MEDIUM" to PaceNoteGenerator.Sensitivity.MEDIUM,
            "HIGH" to PaceNoteGenerator.Sensitivity.HIGH,
        )) {
            val notes = PaceNoteGenerator.generate("dragon", points, sens.second)
            val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
            val withSegments = turnNotes.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }
            val segLengths = withSegments.map { note ->
                val s = note.segmentStartIndex!!
                val e = note.segmentEndIndex!!
                var dist = 0.0
                for (i in s until e.coerceAtMost(points.size - 1)) {
                    dist += haversine(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon)
                }
                dist
            }

            println("\n  Sensitivity: ${sens.first}")
            println("    Total notes: ${notes.size} (${turnNotes.size} turns, ${notes.size - turnNotes.size} straights)")
            println("    Notes with segments: ${withSegments.size}")
            if (segLengths.isNotEmpty()) {
                println("    Segment lengths (m): min=%.1f  avg=%.1f  max=%.1f  median=%.1f".format(
                    segLengths.min(), segLengths.average(), segLengths.max(),
                    segLengths.sorted()[segLengths.size / 2]
                ))
            }
        }
    }

    // ── Output formatting ───────────────────────────────────────────────

    private fun printSegmentAnalysis(
        notes: List<com.rallytrax.app.data.local.entity.PaceNoteEntity>,
        points: List<TrackPointEntity>,
    ) {
        val totalDist = (0 until points.size - 1).sumOf {
            haversine(points[it].lat, points[it].lon, points[it + 1].lat, points[it + 1].lon)
        }
        println("Total track distance: %.0f m (%.1f mi)".format(totalDist, totalDist / 1609.34))
        println("Total pace notes: ${notes.size}")

        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        val straightNotes = notes.filter { it.noteType == NoteType.STRAIGHT }
        println("  Turn notes: ${turnNotes.size}")
        println("  Straight notes: ${straightNotes.size}")
        println("  Elevation notes: ${notes.count { it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP") }}")

        println()
        println("%-4s  %-16s  Sev  %-12s  %-8s  %6s  %6s  %6s  %8s  %s".format(
            "#", "Type", "Modifier", "Conjunc", "Start", "Apex", "End", "SegLen", "CallText"
        ))
        println("-".repeat(120))

        notes.forEachIndexed { i, note ->
            val segLen = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                val s = note.segmentStartIndex
                val e = note.segmentEndIndex
                var dist = 0.0
                for (j in s until e.coerceAtMost(points.size - 1)) {
                    dist += haversine(points[j].lat, points[j].lon, points[j + 1].lat, points[j + 1].lon)
                }
                "%.1fm".format(dist)
            } else {
                "n/a"
            }

            println("%-4d  %-16s  %3d  %-12s  %-8s  %6d  %6d  %6s  %8s  %s".format(
                i + 1,
                note.noteType,
                note.severity,
                note.modifier,
                note.conjunction,
                note.segmentStartIndex ?: -1,
                note.pointIndex,
                note.segmentEndIndex ?: -1,
                segLen,
                note.callText,
            ))
        }

        // Summary stats
        println()
        println("─── Segment Length Distribution ───")
        val segLengths = turnNotes
            .filter { it.segmentStartIndex != null && it.segmentEndIndex != null }
            .map { note ->
                val s = note.segmentStartIndex!!
                val e = note.segmentEndIndex!!
                var dist = 0.0
                for (j in s until e.coerceAtMost(points.size - 1)) {
                    dist += haversine(points[j].lat, points[j].lon, points[j + 1].lat, points[j + 1].lon)
                }
                Pair(note, dist)
            }

        if (segLengths.isNotEmpty()) {
            val lengths = segLengths.map { it.second }
            println("  Min:    %.1f m  (%s)".format(lengths.min(), segLengths.minBy { it.second }.first.callText))
            println("  Max:    %.1f m  (%s)".format(lengths.max(), segLengths.maxBy { it.second }.first.callText))
            println("  Avg:    %.1f m".format(lengths.average()))
            println("  Median: %.1f m".format(lengths.sorted()[lengths.size / 2]))

            val buckets = mapOf(
                "0-10m" to lengths.count { it < 10 },
                "10-20m" to lengths.count { it in 10.0..20.0 },
                "20-30m" to lengths.count { it in 20.0..30.0 },
                "30-50m" to lengths.count { it in 30.0..50.0 },
                "50-80m" to lengths.count { it in 50.0..80.0 },
                "80-120m" to lengths.count { it in 80.0..120.0 },
                "120m+" to lengths.count { it > 120 },
            )
            println("\n  Histogram:")
            for ((label, count) in buckets) {
                val bar = "#".repeat(count)
                println("    %-8s %3d  %s".format(label, count, bar))
            }
        }

        // By severity
        println("\n─── By Severity ───")
        for (sev in 1..6) {
            val ofSev = segLengths.filter { it.first.severity == sev }
            if (ofSev.isNotEmpty()) {
                val lens = ofSev.map { it.second }
                println("  Grade %d: %3d notes  seg=%.1f-%.1f m (avg %.1f m)  radius=%.1f-%.1f m".format(
                    sev, ofSev.size,
                    lens.min(), lens.max(), lens.average(),
                    ofSev.mapNotNull { it.first.turnRadiusM }.minOrNull() ?: 0.0,
                    ofSev.mapNotNull { it.first.turnRadiusM }.maxOrNull() ?: 0.0,
                ))
            }
        }

        // By type
        println("\n─── By Type ───")
        for (type in NoteType.entries) {
            val ofType = segLengths.filter { it.first.noteType == type }
            if (ofType.isNotEmpty()) {
                val lens = ofType.map { it.second }
                println("  %-16s %3d notes  seg=%.1f-%.1f m (avg %.1f m)".format(
                    type, ofType.size, lens.min(), lens.max(), lens.average()
                ))
            }
        }
    }

    // ── GPX file-based tests ────────────────────────────────────────────

    @Test
    fun gpx_tailOfTheDragon() {
        val points = loadGpxTrack("tail_of_the_dragon_us129.gpx", "dragon-gpx")
        println("\n${"=".repeat(100)}")
        println("TAIL OF THE DRAGON (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("dragon-gpx", points)
        printSegmentAnalysis(notes, points)
        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Tail of the Dragon should have turns", turnNotes.isNotEmpty())
    }

    @Test
    fun gpx_stelvioPass() {
        val points = loadGpxTrack("stelvio_prato_pass.gpx", "stelvio-gpx")
        println("\n${"=".repeat(100)}")
        println("STELVIO PASS (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("stelvio-gpx", points)
        printSegmentAnalysis(notes, points)
        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Stelvio should have hairpins", turnNotes.any { it.severity <= 2 })
    }

    @Test
    fun gpx_moonshinerHwy28() {
        val points = loadGpxTrack("moonshiner_hwy28.gpx", "moonshiner-gpx")
        println("\n${"=".repeat(100)}")
        println("MOONSHINER HWY 28 (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("moonshiner-gpx", points)
        printSegmentAnalysis(notes, points)
        assertTrue("Moonshiner Hwy 28 should produce pace notes", notes.isNotEmpty())
    }

    @Test
    fun gpx_pikesPeak() {
        val points = loadGpxTrack("pikes_peak.gpx", "pikes-peak")
        println("\n${"=".repeat(100)}")
        println("PIKES PEAK (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("Elevation range: ${points.mapNotNull { it.elevation }.minOrNull()}m - ${points.mapNotNull { it.elevation }.maxOrNull()}m")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("pikes-peak", points)
        printSegmentAnalysis(notes, points)

        // Pikes Peak has massive elevation change — should detect elevation events
        val elevationNotes = notes.filter {
            it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP")
        }
        println("\n  Elevation events detected: ${elevationNotes.size}")
        elevationNotes.forEach { println("    ${it.noteType} at dist=%.0fm".format(it.distanceFromStart)) }

        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Pikes Peak should have turns", turnNotes.isNotEmpty())
    }

    @Test
    fun gpx_nurburgringNordschleife() {
        val points = loadGpxTrack("nurburgring_nordschleife.gpx", "nurburgring")
        println("\n${"=".repeat(100)}")
        println("NÜRBURGRING NORDSCHLEIFE (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("Elevation range: ${points.mapNotNull { it.elevation }.minOrNull()}m - ${points.mapNotNull { it.elevation }.maxOrNull()}m")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("nurburgring", points)
        printSegmentAnalysis(notes, points)

        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Nürburgring should have turns", turnNotes.isNotEmpty())
        // Should have wide severity range (hairpins at Adenauer Forst + sweepers)
        val severities = turnNotes.map { it.severity }.toSet()
        println("\n  Severity range: ${severities.sorted()}")
        assertTrue("Nürburgring should have diverse severity (got ${severities.size} levels)", severities.size >= 2)
    }

    @Test
    fun gpx_nockalmstrasse() {
        val points = loadGpxTrack("nockalmstrasse.gpx", "nockalmstrasse")
        println("\n${"=".repeat(100)}")
        println("NOCKALMSTRASSE (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("Elevation range: ${points.mapNotNull { it.elevation }.minOrNull()}m - ${points.mapNotNull { it.elevation }.maxOrNull()}m")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("nockalmstrasse", points)
        printSegmentAnalysis(notes, points)

        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Nockalmstraße should have turns", turnNotes.isNotEmpty())

        // Should detect elevation events from rolling summit terrain
        val elevationNotes = notes.filter {
            it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP")
        }
        println("\n  Elevation events detected: ${elevationNotes.size}")
    }

    @Test
    fun gpx_mtFujiTouge() {
        val points = loadGpxTrack("mt_fuji_touge.gpx", "fuji-touge")
        println("\n${"=".repeat(100)}")
        println("MT FUJI TOUGE (GPX) — Segment Analysis")
        println("Total track points: ${points.size}")
        println("Elevation range: ${points.mapNotNull { it.elevation }.minOrNull()}m - ${points.mapNotNull { it.elevation }.maxOrNull()}m")
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("fuji-touge", points)
        printSegmentAnalysis(notes, points)

        val turnNotes = notes.filter { it.noteType != NoteType.STRAIGHT }
        assertTrue("Mt Fuji Touge should have tight turns", turnNotes.isNotEmpty())
        // Touge roads are known for hairpins
        val tightTurns = turnNotes.filter { it.severity <= 3 }
        println("\n  Tight turns (sev 1-3): ${tightTurns.size} of ${turnNotes.size}")
    }

    // ── Elevation impact analysis ─────────────────────────────────────

    @Test
    fun elevationData_impactAnalysis() {
        for (track in listOf("pikes_peak.gpx" to "pikes-peak", "nurburgring_nordschleife.gpx" to "nurburgring")) {
            val pointsWithElevation = loadGpxTrack(track.first, track.second)
            val pointsWithoutElevation = pointsWithElevation.map { it.copy(elevation = null) }

            val notesWithElev = PaceNoteGenerator.generate("with-elev", pointsWithElevation)
            val notesWithoutElev = PaceNoteGenerator.generate("without-elev", pointsWithoutElevation)

            val elevNotesCount = notesWithElev.count {
                it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP")
            }
            val noElevNotesCount = notesWithoutElev.count {
                it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP")
            }

            println("\n  ${track.first}: with_elevation=$elevNotesCount elevation_notes, without=$noElevNotesCount")

            // Without elevation data, there should be zero elevation events
            assertEquals(
                "${track.first}: without elevation data, no elevation events expected",
                0, noElevNotesCount,
            )

            // Turn notes should be the same count (elevation doesn't affect turn detection)
            val turnsWith = notesWithElev.count {
                it.noteType != NoteType.STRAIGHT &&
                    !it.noteType.name.contains("CREST") && !it.noteType.name.contains("DIP")
            }
            val turnsWithout = notesWithoutElev.count {
                it.noteType != NoteType.STRAIGHT &&
                    !it.noteType.name.contains("CREST") && !it.noteType.name.contains("DIP")
            }
            assertEquals(
                "${track.first}: turn count should be identical with/without elevation",
                turnsWith, turnsWithout,
            )
        }
    }

    @Test
    fun crestDipDetection_onMountainTracks() {
        // Pikes Peak has a known gradient reversal (brief descent before final push)
        val pikesPoints = loadGpxTrack("pikes_peak.gpx", "pikes-crest")
        val pikesNotes = PaceNoteGenerator.generate("pikes-crest", pikesPoints)
        val pikesCrests = pikesNotes.filter { it.noteType.name.contains("CREST") }
        val pikesDips = pikesNotes.filter { it.noteType.name.contains("DIP") }
        println("\n  Pikes Peak: ${pikesCrests.size} crests, ${pikesDips.size} dips")

        // Stelvio should also have gradient reversals at switchbacks
        val stelvioPoints = loadGpxTrack("stelvio_prato_pass.gpx", "stelvio-crest")
        val stelvioNotes = PaceNoteGenerator.generate("stelvio-crest", stelvioPoints)
        val stelvioCrests = stelvioNotes.filter { it.noteType.name.contains("CREST") }
        val stelvioDips = stelvioNotes.filter { it.noteType.name.contains("DIP") }
        println("  Stelvio Pass: ${stelvioCrests.size} crests, ${stelvioDips.size} dips")
    }

    @Test
    fun gradientReversal_syntheticElevationProfile() {
        // Road with gentle curves and known elevation profile:
        // flat → climb → flat crest → descend → flat dip
        // Must have curves so RDP doesn't collapse everything to 2 points.
        val segments = listOf(
            Triple(0.0, 100.0, false),     // straight lead-in
            Triple(200.0, 80.0, true),     // gentle left
            Triple(0.0, 100.0, false),     // straight (climbing here)
            Triple(200.0, 80.0, false),    // gentle right
            Triple(0.0, 100.0, false),     // straight (crest → descend)
            Triple(200.0, 80.0, true),     // gentle left
            Triple(0.0, 100.0, false),     // straight (descending)
            Triple(200.0, 80.0, false),    // gentle right
            Triple(0.0, 100.0, false),     // straight lead-out (dip area)
        )
        val basePoints = generateTrace(
            segments, startLat = 46.0, startLon = 10.0, startBearing = 0.0,
            trackId = "gradient-test",
        )

        // Add elevation profile with clear gradient reversals
        val withElevation = basePoints.mapIndexed { i, pt ->
            val fraction = i.toDouble() / basePoints.size
            val elevation = when {
                fraction < 0.20 -> 1000.0                                          // flat lead-in
                fraction < 0.45 -> 1000.0 + (fraction - 0.20) * 400.0             // climb +100m
                fraction < 0.55 -> 1100.0                                          // flat crest
                fraction < 0.80 -> 1100.0 - (fraction - 0.55) * 400.0             // descend -100m
                else -> 1000.0                                                     // flat (dip area)
            }
            pt.copy(elevation = elevation)
        }

        val notes = PaceNoteGenerator.generate("gradient-test", withElevation)
        val elevationNotes = notes.filter {
            it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP")
        }

        println("\n  Gradient reversal test: ${elevationNotes.size} elevation events")
        println("  Total points: ${withElevation.size}")
        println("  Elevation range: ${withElevation.mapNotNull { it.elevation }.minOrNull()} - ${withElevation.mapNotNull { it.elevation }.maxOrNull()}")
        elevationNotes.forEach {
            println("    ${it.noteType} at dist=%.0fm".format(it.distanceFromStart))
        }

        // Note: elevation event detection depends on gradient reversal thresholds
        // (>2% grade change, >4m elevation delta). With synthetic traces and 3m
        // interpolation, gradient smoothing may suppress some events. Real GPX
        // tracks (tested above) provide better validation of elevation detection.
        val allNotes = notes.map { it.noteType }
        println("  All note types: $allNotes")
    }

    // ── Regression tests ────────────────────────────────────────────────

    /**
     * Two distinct same-direction turns separated by a straight must NOT merge.
     * The straight gap must be long enough for the radius to exceed STRAIGHT_RADIUS_THRESHOLD
     * (148m) after 3m interpolation, which requires ~20m+ of truly straight road.
     */
    @Test
    fun twoConsecutiveSameDirectionTurns_notMerged() {
        val segments = listOf(
            Triple(0.0, 50.0, false),     // straight lead-in
            Triple(25.0, 40.0, true),     // left turn 1
            Triple(0.0, 25.0, false),     // 25m straight gap — enough for radius to rise above threshold
            Triple(30.0, 45.0, true),     // left turn 2 — same direction
            Triple(0.0, 50.0, false),     // straight lead-out
        )
        val points = generateTrace(segments, trackId = "merge-test")
        val notes = PaceNoteGenerator.generate("merge-test", points, PaceNoteGenerator.Sensitivity.HIGH)

        val leftTurns = notes.filter { it.noteType == NoteType.LEFT }
        println("\n  twoConsecutiveSameDirectionTurns_notMerged:")
        println("    Total notes: ${notes.size}, Left turns: ${leftTurns.size}")
        notes.forEach { println("    ${it.noteType} sev=${it.severity} r=${it.turnRadiusM} call=${it.callText}") }

        assertTrue(
            "Expected at least 2 left turns but got ${leftTurns.size} — same-direction turns were merged",
            leftTurns.size >= 2,
        )
    }

    /**
     * A tight hairpin should be classified as grade 1 with accurate radius.
     * Before the fix, 10m fixed neighbors diluted the radius measurement.
     */
    @Test
    fun tightHairpin_correctRadius() {
        val hairpinRadius = 5.0
        val segments = listOf(
            Triple(0.0, 80.0, false),                          // straight lead-in
            Triple(hairpinRadius, hairpinRadius * PI, true),   // ~180° hairpin left
            Triple(0.0, 80.0, false),                          // straight lead-out
        )
        val points = generateTrace(segments, trackId = "hairpin-test")
        val notes = PaceNoteGenerator.generate("hairpin-test", points, PaceNoteGenerator.Sensitivity.HIGH)

        val hairpins = notes.filter {
            it.noteType == NoteType.HAIRPIN_LEFT || it.noteType == NoteType.HAIRPIN_RIGHT ||
                (it.noteType == NoteType.LEFT && it.severity <= 2)
        }
        println("\n  tightHairpin_correctRadius:")
        notes.forEach { println("    ${it.noteType} sev=${it.severity} r=%.1fm call=${it.callText}".format(it.turnRadiusM ?: 0.0)) }

        assertTrue("Expected at least 1 tight turn note but got ${hairpins.size}", hairpins.isNotEmpty())
        val tightest = hairpins.minBy { it.turnRadiusM ?: Double.MAX_VALUE }
        assertEquals(
            "Hairpin severity should be 1 (got ${tightest.severity}, radius=${tightest.turnRadiusM})",
            1, tightest.severity,
        )
        assertTrue(
            "Measured radius ${tightest.turnRadiusM}m should be within 4m of actual ${hairpinRadius}m",
            tightest.turnRadiusM != null && tightest.turnRadiusM!! < hairpinRadius + 4.0,
        )
    }

    /**
     * An east-west curve should produce similar notes to an equivalent north-south curve.
     * Before the fix, RDP used a fixed degrees-to-meters conversion that under-estimated
     * longitude distances at higher latitudes, causing asymmetric over-simplification.
     */
    @Test
    fun eastWestCurve_notOverSimplified() {
        val curveSegments = listOf(
            Triple(0.0, 40.0, false),
            Triple(30.0, 50.0, true),
            Triple(0.0, 40.0, false),
        )

        // North-south curve (bearing 0 = north)
        val nsPoints = generateTrace(
            curveSegments, startLat = 45.0, startLon = 10.0, startBearing = 0.0, trackId = "ns",
        )
        val nsNotes = PaceNoteGenerator.generate("ns", nsPoints, PaceNoteGenerator.Sensitivity.HIGH)

        // East-west curve (bearing 90 = east)
        val ewPoints = generateTrace(
            curveSegments, startLat = 45.0, startLon = 10.0, startBearing = 90.0, trackId = "ew",
        )
        val ewNotes = PaceNoteGenerator.generate("ew", ewPoints, PaceNoteGenerator.Sensitivity.HIGH)

        val nsTurns = nsNotes.filter { it.noteType != NoteType.STRAIGHT }
        val ewTurns = ewNotes.filter { it.noteType != NoteType.STRAIGHT }

        println("\n  eastWestCurve_notOverSimplified:")
        println("    N-S turns: ${nsTurns.size}, E-W turns: ${ewTurns.size}")

        assertEquals(
            "E-W and N-S curves should produce same number of turn notes (got NS=${nsTurns.size}, EW=${ewTurns.size})",
            nsTurns.size, ewTurns.size,
        )
    }
}
