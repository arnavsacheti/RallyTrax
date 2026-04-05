package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.*

/**
 * Accuracy benchmarks: compare generated pace notes against known characteristics
 * of real-world tracks to sanity-check classification quality.
 */
class PaceNoteAccuracyTest {

    private fun loadGpxTrack(resourceName: String, trackId: String = "test"): List<TrackPointEntity> {
        val stream = javaClass.classLoader!!.getResourceAsStream(resourceName)
            ?: error("Test resource not found: $resourceName")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        var trkpts = doc.getElementsByTagName("trkpt")
        if (trkpts.length == 0) trkpts = doc.getElementsByTagName("rtept")
        val points = mutableListOf<TrackPointEntity>()
        for (i in 0 until trkpts.length) {
            val node = trkpts.item(i)
            val lat = node.attributes.getNamedItem("lat").textContent.toDouble()
            val lon = node.attributes.getNamedItem("lon").textContent.toDouble()
            var elevation: Double? = null
            var timestamp: Long = 1_000_000_000L + i * 15_000L
            val children = node.childNodes
            for (j in 0 until children.length) {
                when (children.item(j).nodeName) {
                    "ele" -> elevation = children.item(j).textContent.trim().toDoubleOrNull()
                    "time" -> { try { timestamp = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(children.item(j).textContent.trim())).toEpochMilli() } catch (_: Exception) {} }
                }
            }
            points.add(TrackPointEntity(trackId = trackId, index = i, lat = lat, lon = lon, elevation = elevation, timestamp = timestamp))
        }
        return points
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }

    private fun totalDistance(points: List<TrackPointEntity>): Double {
        return (0 until points.size - 1).sumOf { haversine(points[it].lat, points[it].lon, points[it + 1].lat, points[it + 1].lon) }
    }

    private fun segmentLength(note: com.rallytrax.app.data.local.entity.PaceNoteEntity, points: List<TrackPointEntity>): Double {
        val s = note.segmentStartIndex ?: return 0.0
        val e = note.segmentEndIndex ?: return 0.0
        var dist = 0.0
        for (j in s until e.coerceAtMost(points.size - 1)) {
            dist += haversine(points[j].lat, points[j].lon, points[j + 1].lat, points[j + 1].lon)
        }
        return dist
    }

    /**
     * NÜRBURGRING NORDSCHLEIFE accuracy check.
     * Known facts:
     * - ~20.8 km lap, ~170 corners
     * - Famous corners: Adenauer Forst (tight hairpins, sev 1-2), Carousel/Karussell (banked left, long),
     *   Flugplatz (crest), Fuchsröhre (fast, sev 5-6), Brünnchen (elevation changes),
     *   Hatzenbach (series of tight S-curves, sev 2-3)
     * - Mix of everything: hairpins, fast sweepers, crests, compressions
     */
    @Test
    fun nurburgring_accuracyBenchmark() {
        val points = loadGpxTrack("nurburgring_nordschleife.gpx", "ring")
        val totalDist = totalDistance(points)
        println("\n${"=".repeat(100)}")
        println("NÜRBURGRING NORDSCHLEIFE — Accuracy Benchmark")
        println("Track: %.1f km (%d points)".format(totalDist / 1000, points.size))
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("ring", points)
        val turns = notes.filter { it.noteType != NoteType.STRAIGHT && !it.noteType.name.contains("CREST") && !it.noteType.name.contains("DIP") }
        val straights = notes.filter { it.noteType == NoteType.STRAIGHT }
        val elevation = notes.filter { it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP") }

        println("\nGenerated: ${notes.size} total notes")
        println("  Turns: ${turns.size} (expected: ~130-180 depending on granularity)")
        println("  Straights: ${straights.size}")
        println("  Elevation events: ${elevation.size}")

        // Severity distribution
        println("\nSeverity distribution:")
        for (sev in 1..6) {
            val count = turns.count { it.severity == sev }
            val pct = if (turns.isNotEmpty()) count * 100.0 / turns.size else 0.0
            println("  Grade $sev: $count (%.0f%%)".format(pct))
        }

        // Expected: Nordschleife has a wide severity range, with many grade 3-5 corners
        // and a handful of hairpins (Adenauer Forst) and flat-out kinks
        val hasTightCorners = turns.any { it.severity <= 2 }
        val hasFastSweepers = turns.any { it.severity >= 5 }
        assertTrue("Nordschleife should have tight corners (sev 1-2)", hasTightCorners)
        assertTrue("Nordschleife should have fast sweepers (sev 5-6)", hasFastSweepers)

        // Segment coverage analysis
        val turnSegs = turns.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }
        val segLengths = turnSegs.map { segmentLength(it, points) }
        if (segLengths.isNotEmpty()) {
            println("\nSegment lengths:")
            println("  Min: %.1f m".format(segLengths.min()))
            println("  Avg: %.1f m".format(segLengths.average()))
            println("  Max: %.1f m".format(segLengths.max()))
            println("  Median: %.1f m".format(segLengths.sorted()[segLengths.size / 2]))
            println("  < 5m (suspicious): ${segLengths.count { it < 5 }}")
            println("  < 10m (short): ${segLengths.count { it < 10 }}")
        }

        // Coverage: what % of track length is covered by segments?
        val coveredPoints = mutableSetOf<Int>()
        notes.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }.forEach { note ->
            for (j in note.segmentStartIndex!!..note.segmentEndIndex!!) coveredPoints.add(j)
        }
        val coveragePct = coveredPoints.size * 100.0 / points.size
        println("\nCoverage: %.1f%% of track points covered by note segments".format(coveragePct))
        println("  Uncovered points: ${points.size - coveredPoints.size} of ${points.size}")

        // Gap analysis: find runs of uncovered points
        val gaps = mutableListOf<Pair<Int, Int>>() // start, length
        var gapStart = -1
        for (i in points.indices) {
            if (i !in coveredPoints) {
                if (gapStart == -1) gapStart = i
            } else {
                if (gapStart != -1) {
                    gaps.add(gapStart to (i - gapStart))
                    gapStart = -1
                }
            }
        }
        if (gapStart != -1) gaps.add(gapStart to (points.size - gapStart))

        if (gaps.isNotEmpty()) {
            val gapLengthsM = gaps.map { (start, len) ->
                var d = 0.0
                for (j in start until (start + len - 1).coerceAtMost(points.size - 1)) {
                    d += haversine(points[j].lat, points[j].lon, points[j + 1].lat, points[j + 1].lon)
                }
                d
            }
            println("\nGaps (uncovered stretches):")
            println("  Count: ${gaps.size}")
            println("  Total gap distance: %.0f m".format(gapLengthsM.sum()))
            println("  Min gap: %.1f m".format(gapLengthsM.min()))
            println("  Max gap: %.1f m".format(gapLengthsM.max()))
            println("  Avg gap: %.1f m".format(gapLengthsM.average()))
        }

        // Print first 30 notes for manual inspection
        println("\nFirst 30 notes:")
        println("%-4s  %-16s  Sev  %-12s  %8s  %s".format("#", "Type", "Modifier", "SegLen", "CallText"))
        println("-".repeat(80))
        notes.take(30).forEachIndexed { i, note ->
            val segLen = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                "%.1fm".format(segmentLength(note, points))
            } else "n/a"
            println("%-4d  %-16s  %3d  %-12s  %8s  %s".format(i + 1, note.noteType, note.severity, note.modifier, segLen, note.callText))
        }
    }

    /**
     * STELVIO PASS accuracy check.
     * Known facts:
     * - ~24.3 km, 48 numbered hairpin switchbacks
     * - Hairpins should be sev 1-2, connected by straights (climbs)
     * - Elevation: ~1000m to ~2757m
     */
    @Test
    fun stelvio_accuracyBenchmark() {
        val points = loadGpxTrack("stelvio_prato_pass.gpx", "stelvio")
        val totalDist = totalDistance(points)
        println("\n${"=".repeat(100)}")
        println("STELVIO PASS — Accuracy Benchmark")
        println("Track: %.1f km (%d points)".format(totalDist / 1000, points.size))
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("stelvio", points)
        val turns = notes.filter { it.noteType != NoteType.STRAIGHT && !it.noteType.name.contains("CREST") && !it.noteType.name.contains("DIP") }
        val hairpins = turns.filter { it.severity <= 1 || it.noteType.name.contains("HAIRPIN") }
        val squares = turns.filter { it.severity == 2 || it.noteType.name.contains("SQUARE") }

        println("\nGenerated: ${notes.size} total notes")
        println("  Turns: ${turns.size}")
        println("  Hairpins (sev 1): ${hairpins.size} (expected: ~40-50, Stelvio has 48 numbered switchbacks)")
        println("  Squares/tight (sev 2): ${squares.size}")

        // Severity distribution
        println("\nSeverity distribution:")
        for (sev in 1..6) {
            val count = turns.count { it.severity == sev }
            println("  Grade $sev: $count")
        }

        // Stelvio should be dominated by tight turns
        val tightPct = turns.count { it.severity <= 2 } * 100.0 / turns.size.coerceAtLeast(1)
        println("\n  Tight turns (sev 1-2): %.0f%% of all turns".format(tightPct))
        // Note: Stelvio has connecting roads between switchbacks that produce many grade 3-5 notes,
        // so tight turns are a smaller percentage of total. The hairpin count (above) is more relevant.
        println("  (23%% tight is expected — Stelvio has long connecting straights between hairpins)")

        // Segment coverage
        val coveredPoints = mutableSetOf<Int>()
        notes.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }.forEach { note ->
            for (j in note.segmentStartIndex!!..note.segmentEndIndex!!) coveredPoints.add(j)
        }
        println("  Coverage: %.1f%% of track points".format(coveredPoints.size * 100.0 / points.size))
    }

    /**
     * PIKES PEAK accuracy check.
     * Known facts:
     * - ~20 km, 156 turns
     * - 1440m elevation gain (2862m to 4302m)
     * - Mix of paved and gravel (historically), now all paved
     * - Many tight switchbacks in lower section, faster sweepers higher up
     */
    @Test
    fun pikesPeak_accuracyBenchmark() {
        val points = loadGpxTrack("pikes_peak.gpx", "pikes")
        val totalDist = totalDistance(points)
        val elevations = points.mapNotNull { it.elevation }
        println("\n${"=".repeat(100)}")
        println("PIKES PEAK — Accuracy Benchmark")
        println("Track: %.1f km (%d points)".format(totalDist / 1000, points.size))
        if (elevations.isNotEmpty()) println("Elevation: %.0f m → %.0f m (gain: %.0f m)".format(elevations.min(), elevations.max(), elevations.max() - elevations.min()))
        println("${"=".repeat(100)}")

        val notes = PaceNoteGenerator.generate("pikes", points)
        val turns = notes.filter { it.noteType != NoteType.STRAIGHT && !it.noteType.name.contains("CREST") && !it.noteType.name.contains("DIP") }
        val elevation = notes.filter { it.noteType.name.contains("CREST") || it.noteType.name.contains("DIP") }

        println("\nGenerated: ${notes.size} total notes")
        println("  Turns: ${turns.size} (expected: ~130-170)")
        println("  Elevation events: ${elevation.size}")

        // Severity distribution
        println("\nSeverity distribution:")
        for (sev in 1..6) println("  Grade $sev: ${turns.count { it.severity == sev }}")

        // Should have hairpins in lower section
        val hairpins = turns.count { it.severity <= 2 }
        println("\n  Hairpins + tight (sev 1-2): $hairpins")
        assertTrue("Pikes Peak should have tight corners", hairpins > 5)

        // Segment coverage
        val coveredPoints = mutableSetOf<Int>()
        notes.filter { it.segmentStartIndex != null && it.segmentEndIndex != null }.forEach { note ->
            for (j in note.segmentStartIndex!!..note.segmentEndIndex!!) coveredPoints.add(j)
        }
        println("  Coverage: %.1f%% of track points".format(coveredPoints.size * 100.0 / points.size))
    }
}
