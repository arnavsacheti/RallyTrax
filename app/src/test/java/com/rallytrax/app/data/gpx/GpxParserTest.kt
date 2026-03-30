package com.rallytrax.app.data.gpx

import com.rallytrax.app.data.local.entity.Conjunction
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.SeverityHalf
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxParserTest {

    private fun parseGpx(xml: String): GpxImportResult =
        GpxParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `parse valid GPX with 3 track points`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk>
                <trkseg>
                  <trkpt lat="47.6062" lon="-122.3321">
                    <ele>56.0</ele>
                    <time>2025-06-15T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="47.6072" lon="-122.3331">
                    <ele>58.5</ele>
                    <time>2025-06-15T10:00:10Z</time>
                  </trkpt>
                  <trkpt lat="47.6082" lon="-122.3341">
                    <ele>61.0</ele>
                    <time>2025-06-15T10:00:20Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parseGpx(gpx)

        assertEquals(3, result.points.size)

        val p0 = result.points[0]
        assertEquals(47.6062, p0.lat, 0.0001)
        assertEquals(-122.3321, p0.lon, 0.0001)
        assertEquals(56.0, p0.elevation!!, 0.0001)
        assertEquals(1749981600000L, p0.timestamp) // 2025-06-15T10:00:00Z

        val p1 = result.points[1]
        assertEquals(47.6072, p1.lat, 0.0001)
        assertEquals(-122.3331, p1.lon, 0.0001)
        assertEquals(58.5, p1.elevation!!, 0.0001)
        assertEquals(1749981610000L, p1.timestamp) // 2025-06-15T10:00:10Z

        val p2 = result.points[2]
        assertEquals(47.6082, p2.lat, 0.0001)
        assertEquals(-122.3341, p2.lon, 0.0001)
        assertEquals(61.0, p2.elevation!!, 0.0001)
        assertEquals(1749981620000L, p2.timestamp) // 2025-06-15T10:00:20Z

        // Verify point indices are sequential
        assertEquals(0, result.points[0].index)
        assertEquals(1, result.points[1].index)
        assertEquals(2, result.points[2].index)
    }

    @Test
    fun `parse GPX with name and description metadata`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <metadata>
                <name>Mountain Pass Rally</name>
                <desc>A scenic drive through the mountains</desc>
              </metadata>
              <trk>
                <trkseg>
                  <trkpt lat="46.0" lon="7.0">
                    <time>2025-06-15T12:00:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parseGpx(gpx)

        assertEquals("Mountain Pass Rally", result.track.name)
        assertEquals("A scenic drive through the mountains", result.track.description)
    }

    @Test
    fun `parse GPX with RallyTrax extensions containing pace notes`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="RallyTrax"
                 xmlns:rallytrax="http://rallytrax.com/gpx/1">
              <trk>
                <trkseg>
                  <trkpt lat="47.0" lon="-122.0">
                    <time>2025-06-15T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="47.001" lon="-122.001">
                    <time>2025-06-15T10:00:05Z</time>
                  </trkpt>
                </trkseg>
                <extensions>
                  <rallytrax:paceNotes>
                    <rallytrax:paceNote
                      pointIndex="0"
                      distanceFromStart="0.0"
                      noteType="LEFT"
                      severity="3"
                      modifier="TIGHTENS"
                      callDistanceM="50.0"
                      severityHalf="MINUS"
                      conjunction="INTO"
                      turnRadiusM="25.0">Left 3 minus tightens</rallytrax:paceNote>
                  </rallytrax:paceNotes>
                </extensions>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parseGpx(gpx)

        assertEquals(1, result.paceNotes.size)
        val note = result.paceNotes[0]
        assertEquals(0, note.pointIndex)
        assertEquals(0.0, note.distanceFromStart, 0.0001)
        assertEquals(NoteType.LEFT, note.noteType)
        assertEquals(3, note.severity)
        assertEquals(NoteModifier.TIGHTENS, note.modifier)
        assertEquals(50.0, note.callDistanceM, 0.0001)
        assertEquals(SeverityHalf.MINUS, note.severityHalf)
        assertEquals(Conjunction.INTO, note.conjunction)
        assertEquals(25.0, note.turnRadiusM!!, 0.0001)
        assertEquals("Left 3 minus tightens", note.callText)
    }

    @Test
    fun `bounding box computed correctly from known points`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk>
                <trkseg>
                  <trkpt lat="40.0" lon="-105.0">
                    <time>2025-06-15T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="42.0" lon="-103.0">
                    <time>2025-06-15T10:00:10Z</time>
                  </trkpt>
                  <trkpt lat="41.0" lon="-104.0">
                    <time>2025-06-15T10:00:20Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parseGpx(gpx)
        val track = result.track

        assertEquals(42.0, track.boundingBoxNorthLat, 0.0001)
        assertEquals(40.0, track.boundingBoxSouthLat, 0.0001)
        assertEquals(-103.0, track.boundingBoxEastLon, 0.0001)
        assertEquals(-105.0, track.boundingBoxWestLon, 0.0001)
    }

    @Test
    fun `distance calculation produces reasonable value`() {
        // Two points approximately 111 km apart (1 degree of latitude)
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk>
                <trkseg>
                  <trkpt lat="0.0" lon="0.0">
                    <time>2025-06-15T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="1.0" lon="0.0">
                    <time>2025-06-15T10:10:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parseGpx(gpx)

        // 1 degree of latitude is approximately 111,195 meters
        val distance = result.track.distanceMeters
        assertTrue("Distance should be around 111km, was ${distance}m", distance > 110000 && distance < 112000)
    }

    @Test(expected = GpxParseException::class)
    fun `malformed XML throws GpxParseException`() {
        val malformed = "this is not xml at all <<<"
        parseGpx(malformed)
    }

    @Test(expected = GpxParseException::class)
    fun `empty track points list throws GpxParseException`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk>
                <trkseg>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        parseGpx(gpx)
    }
}
