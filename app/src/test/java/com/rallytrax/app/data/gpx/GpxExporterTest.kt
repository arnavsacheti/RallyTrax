package com.rallytrax.app.data.gpx

import com.rallytrax.app.data.local.entity.Conjunction
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.SeverityHalf
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class GpxExporterTest {

    private val trackId = "test-track-id"
    private val baseTimestamp = 1_700_000_000_000L // 2023-11-14T22:13:20Z

    private fun makeTrack(
        name: String = "Test Track",
        description: String? = null,
        tags: String = "",
    ) = TrackEntity(
        id = trackId,
        name = name,
        description = description,
        recordedAt = baseTimestamp,
        durationMs = 60_000L,
        distanceMeters = 1500.0,
        maxSpeedMps = 30.0,
        avgSpeedMps = 15.0,
        elevationGainM = 50.0,
        tags = tags,
    )

    private val sampleLats = doubleArrayOf(37.7749, 37.7759, 37.7769, 37.7779, 37.7789)
    private val sampleLons = doubleArrayOf(-122.4194, -122.4184, -122.4174, -122.4164, -122.4154)

    private fun makePoints(count: Int = 3): List<TrackPointEntity> =
        (0 until count).map { i ->
            TrackPointEntity(
                trackId = trackId,
                index = i,
                lat = sampleLats[i],
                lon = sampleLons[i],
                elevation = 10.0 + i,
                timestamp = baseTimestamp + i * 1000L,
                speed = 10.0 + i,
                bearing = 90.0 + i,
                accuracy = 5.0f,
            )
        }

    private fun makePaceNote(
        callText: String = "Left 3",
        noteType: NoteType = NoteType.LEFT,
        severity: Int = 3,
        turnRadiusM: Double? = 25.0,
    ) = PaceNoteEntity(
        trackId = trackId,
        pointIndex = 0,
        distanceFromStart = 100.0,
        noteType = noteType,
        severity = severity,
        modifier = NoteModifier.TIGHTENS,
        callText = callText,
        callDistanceM = 50.0,
        severityHalf = SeverityHalf.PLUS,
        conjunction = Conjunction.INTO,
        turnRadiusM = turnRadiusM,
    )

    private fun export(
        track: TrackEntity = makeTrack(),
        points: List<TrackPointEntity> = makePoints(),
        paceNotes: List<PaceNoteEntity> = emptyList(),
    ): String {
        val out = ByteArrayOutputStream()
        GpxExporter.export(track, points, out, paceNotes)
        return out.toString("UTF-8")
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    // ── XML header ─────────────────────────────────────────────────────

    @Test
    fun export_containsXmlHeader() {
        val xml = export()
        assertTrue(
            "Output should start with XML declaration",
            xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
        )
    }

    // ── GPX root element ───────────────────────────────────────────────

    @Test
    fun export_containsGpxRootElement() {
        val xml = export()
        assertTrue("Output should contain <gpx element", xml.contains("<gpx "))
        assertTrue("Output should close </gpx>", xml.contains("</gpx>"))
    }

    // ── Track name ─────────────────────────────────────────────────────

    @Test
    fun export_trackNameAppearsInOutput() {
        val xml = export(track = makeTrack(name = "Mountain Rally"))
        assertTrue(
            "Track name should appear in metadata",
            xml.contains("<name>Mountain Rally</name>"),
        )
    }

    // ── Track points with lat/lon ──────────────────────────────────────

    @Test
    fun export_trackPointsIncludeLatLon() {
        val xml = export()
        val doc = parseXml(xml)
        val trkpts = doc.getElementsByTagNameNS("*", "trkpt")
        assertEquals("Should have 3 track points", 3, trkpts.length)

        val first = trkpts.item(0) as Element
        assertEquals("37.7749", first.getAttribute("lat"))
        assertEquals("-122.4194", first.getAttribute("lon"))

        val second = trkpts.item(1) as Element
        assertEquals(sampleLats[1].toString(), second.getAttribute("lat"))
        assertEquals(sampleLons[1].toString(), second.getAttribute("lon"))
    }

    @Test
    fun export_trackPointsIncludeElevationSpeedBearing() {
        val xml = export()
        assertTrue("Should contain elevation", xml.contains("<ele>10.0</ele>"))
        assertTrue("Should contain speed extension", xml.contains("<rt:speed>10.0</rt:speed>"))
        assertTrue("Should contain bearing extension", xml.contains("<rt:bearing>90.0</rt:bearing>"))
        assertTrue("Should contain accuracy extension", xml.contains("<rt:accuracy>5.0</rt:accuracy>"))
    }

    // ── Pace notes in extensions ───────────────────────────────────────

    @Test
    fun export_paceNotesAppearInExtensions() {
        val note = makePaceNote()
        val xml = export(paceNotes = listOf(note))

        assertTrue("Should contain paceNotes block", xml.contains("<rt:paceNotes>"))
        assertTrue("Should contain paceNote element", xml.contains("<rt:paceNote"))
        assertTrue("Should contain noteType attribute", xml.contains("noteType=\"LEFT\""))
        assertTrue("Should contain severity attribute", xml.contains("severity=\"3\""))
        assertTrue("Should contain modifier attribute", xml.contains("modifier=\"TIGHTENS\""))
        assertTrue("Should contain severityHalf attribute", xml.contains("severityHalf=\"PLUS\""))
        assertTrue("Should contain conjunction attribute", xml.contains("conjunction=\"INTO\""))
        assertTrue("Should contain turnRadiusM attribute", xml.contains("turnRadiusM=\"25.0\""))
        assertTrue("Should contain call text", xml.contains("Left 3</rt:paceNote>"))
    }

    @Test
    fun export_noPaceNotes_omitsPaceNotesBlock() {
        val xml = export(paceNotes = emptyList())
        assertFalse("Should not contain paceNotes block", xml.contains("<rt:paceNotes>"))
    }

    // ── XML escaping ───────────────────────────────────────────────────

    @Test
    fun export_specialCharactersAreXmlEscaped() {
        val xml = export(track = makeTrack(name = "Tom & Jerry <Race>"))
        assertTrue(
            "Ampersand should be escaped",
            xml.contains("Tom &amp; Jerry &lt;Race&gt;"),
        )
        // Verify it is still well-formed XML
        val doc = parseXml(xml)
        val names = doc.getElementsByTagNameNS("*", "name")
        // metadata <name> and <trk><name> both have the name
        assertEquals("Tom & Jerry <Race>", names.item(0).textContent)
    }

    @Test
    fun export_descriptionWithSpecialCharsIsEscaped() {
        val xml = export(track = makeTrack(name = "Track", description = "A \"quoted\" & <tagged> route"))
        assertTrue("Should contain escaped description", xml.contains("<desc>A &quot;quoted&quot; &amp; &lt;tagged&gt; route</desc>"))
    }

    // ── Empty track points ─────────────────────────────────────────────

    @Test
    fun export_emptyTrackPoints_producesValidXml() {
        val xml = export(points = emptyList())
        // Should still be parseable XML
        val doc = parseXml(xml)
        val gpxElements = doc.getElementsByTagNameNS("*", "gpx")
        assertEquals("Should have gpx root", 1, gpxElements.length)

        val trkpts = doc.getElementsByTagNameNS("*", "trkpt")
        assertEquals("Should have zero track points", 0, trkpts.length)

        // trkseg should still exist (empty)
        val trksegs = doc.getElementsByTagNameNS("*", "trkseg")
        assertEquals("Should have trkseg element", 1, trksegs.length)
    }

    // ── Round-trip test ────────────────────────────────────────────────

    @Test
    fun export_roundTrip_dataPreservedInXml() {
        val track = makeTrack(name = "Round Trip Track", tags = "rally,gravel")
        val points = makePoints(5)
        val paceNote = makePaceNote(callText = "Right 4 tightens", noteType = NoteType.RIGHT, severity = 4)
        val xml = export(track, points, listOf(paceNote))
        val doc = parseXml(xml)

        // Verify track name
        val names = doc.getElementsByTagNameNS("*", "name")
        assertEquals("Round Trip Track", names.item(0).textContent)

        // Verify track extensions
        val durationEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "durationMs")
        assertEquals("60000", durationEls.item(0).textContent)

        val distanceEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "distanceMeters")
        assertEquals("1500.0", distanceEls.item(0).textContent)

        val maxSpeedEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "maxSpeedMps")
        assertEquals("30.0", maxSpeedEls.item(0).textContent)

        val avgSpeedEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "avgSpeedMps")
        assertEquals("15.0", avgSpeedEls.item(0).textContent)

        val elevGainEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "elevationGainM")
        assertEquals("50.0", elevGainEls.item(0).textContent)

        val tagsEls = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "tags")
        assertEquals("rally,gravel", tagsEls.item(0).textContent)

        // Verify point count
        val trkpts = doc.getElementsByTagNameNS("*", "trkpt")
        assertEquals(5, trkpts.length)

        // Verify first point coords
        val first = trkpts.item(0) as Element
        assertEquals("37.7749", first.getAttribute("lat"))
        assertEquals("-122.4194", first.getAttribute("lon"))

        // Verify last point coords
        val last = trkpts.item(4) as Element
        assertEquals(sampleLats[4].toString(), last.getAttribute("lat"))
        assertEquals(sampleLons[4].toString(), last.getAttribute("lon"))

        // Verify pace note
        val paceNotes = doc.getElementsByTagNameNS("http://rallytrax.com/gpx/1", "paceNote")
        assertEquals(1, paceNotes.length)
        val pn = paceNotes.item(0) as Element
        assertEquals("RIGHT", pn.getAttribute("noteType"))
        assertEquals("4", pn.getAttribute("severity"))
        assertEquals("TIGHTENS", pn.getAttribute("modifier"))
        assertEquals("Right 4 tightens", pn.textContent)
    }

    // ── Track metadata extensions ──────────────────────────────────────

    @Test
    fun export_trackExtensionsArePresent() {
        val xml = export()
        assertTrue("Should contain durationMs", xml.contains("<rt:durationMs>60000</rt:durationMs>"))
        assertTrue("Should contain distanceMeters", xml.contains("<rt:distanceMeters>1500.0</rt:distanceMeters>"))
        assertTrue("Should contain maxSpeedMps", xml.contains("<rt:maxSpeedMps>30.0</rt:maxSpeedMps>"))
        assertTrue("Should contain avgSpeedMps", xml.contains("<rt:avgSpeedMps>15.0</rt:avgSpeedMps>"))
        assertTrue("Should contain elevationGainM", xml.contains("<rt:elevationGainM>50.0</rt:elevationGainM>"))
    }

    @Test
    fun export_blankTags_omitsTagsElement() {
        val xml = export(track = makeTrack(tags = ""))
        assertFalse("Should not contain tags element for blank tags", xml.contains("<rt:tags>"))
    }

    @Test
    fun export_nonBlankTags_includesTagsElement() {
        val xml = export(track = makeTrack(tags = "rally,gravel"))
        assertTrue("Should contain tags", xml.contains("<rt:tags>rally,gravel</rt:tags>"))
    }

    @Test
    fun export_pointWithoutOptionalFields_omitsExtensions() {
        val point = TrackPointEntity(
            trackId = trackId,
            index = 0,
            lat = 37.0,
            lon = -122.0,
            timestamp = baseTimestamp,
        )
        val xml = export(points = listOf(point))
        val doc = parseXml(xml)
        val trkpt = doc.getElementsByTagNameNS("*", "trkpt").item(0) as Element
        // Should not have <extensions> child when speed/bearing/accuracy are all null
        val extensions = trkpt.getElementsByTagNameNS("*", "extensions")
        assertEquals("Point without optional fields should have no extensions", 0, extensions.length)
    }
}
