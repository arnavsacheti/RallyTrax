package com.rallytrax.app.data.export

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvExporterTest {

    private val expectedHeader =
        "Name,Date,Distance (m),Duration (s),Avg Speed (m/s),Max Speed (m/s),Elevation Gain (m),Difficulty,Surface,Vehicle,Tags"

    private fun export(
        tracks: List<TrackEntity> = emptyList(),
        vehicles: List<VehicleEntity> = emptyList(),
    ): String {
        val out = ByteArrayOutputStream()
        CsvExporter.export(tracks, vehicles, out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun lines(csv: String): List<String> = csv.trimEnd('\n', '\r').lines()

    /**
     * Parse a CSV line respecting quoted fields (handles commas and escaped quotes inside quotes).
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var i = 0
        while (i <= line.length) {
            if (i == line.length) {
                fields.add("")
                break
            }
            if (line[i] == '"') {
                // Quoted field
                val sb = StringBuilder()
                i++ // skip opening quote
                while (i < line.length) {
                    if (line[i] == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            i++ // skip closing quote
                            break
                        }
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                fields.add(sb.toString())
                // skip comma separator
                if (i < line.length && line[i] == ',') i++
            } else {
                // Unquoted field
                val next = line.indexOf(',', i)
                if (next == -1) {
                    fields.add(line.substring(i))
                    break
                } else {
                    fields.add(line.substring(i, next))
                    i = next + 1
                }
            }
        }
        return fields
    }

    // -- Helper factories --

    private fun track(
        name: String = "Test Track",
        recordedAt: Long = 0L,
        durationMs: Long = 60_000L,
        distanceMeters: Double = 1234.5,
        avgSpeedMps: Double = 20.575,
        maxSpeedMps: Double = 33.33,
        elevationGainM: Double = 100.0,
        difficultyRating: String? = null,
        primarySurface: String? = null,
        vehicleId: String? = null,
        tags: String = "",
    ) = TrackEntity(
        id = "t1",
        name = name,
        recordedAt = recordedAt,
        durationMs = durationMs,
        distanceMeters = distanceMeters,
        avgSpeedMps = avgSpeedMps,
        maxSpeedMps = maxSpeedMps,
        elevationGainM = elevationGainM,
        difficultyRating = difficultyRating,
        primarySurface = primarySurface,
        vehicleId = vehicleId,
        tags = tags,
    )

    private fun vehicle(
        id: String = "v1",
        name: String = "My Car",
        year: Int = 2024,
        make: String = "Toyota",
        model: String = "GR86",
    ) = VehicleEntity(id = id, name = name, year = year, make = make, model = model)

    // -- Tests --

    @Test
    fun `header row matches expected columns`() {
        val csv = export()
        val header = lines(csv).first()
        assertEquals(expectedHeader, header)
    }

    @Test
    fun `empty track list produces header only`() {
        val csv = export()
        val rows = lines(csv)
        assertEquals(1, rows.size)
        assertEquals(expectedHeader, rows[0])
    }

    @Test
    fun `data row has correct number of columns`() {
        val csv = export(tracks = listOf(track()))
        val rows = lines(csv)
        assertEquals(2, rows.size)
        val headerCols = parseCsvLine(rows[0]).size
        val dataCols = parseCsvLine(rows[1]).size
        assertEquals(headerCols, dataCols)
    }

    @Test
    fun `field with comma is quoted`() {
        val csv = export(tracks = listOf(track(name = "Stage 1, Leg 2")))
        val dataLine = lines(csv)[1]
        assertTrue(
            "Expected quoted field for name containing comma",
            dataLine.startsWith("\"Stage 1, Leg 2\""),
        )
    }

    @Test
    fun `field with double quote is double-quoted`() {
        val csv = export(tracks = listOf(track(name = "The \"Beast\" Run")))
        val dataLine = lines(csv)[1]
        assertTrue(
            "Expected escaped double quotes in CSV",
            dataLine.startsWith("\"The \"\"Beast\"\" Run\""),
        )
    }

    @Test
    fun `field with newline is quoted`() {
        val csv = export(tracks = listOf(track(tags = "fast\nfun")))
        assertTrue("Expected quoted field for newline in tags", csv.contains("\"fast\nfun\""))
    }

    @Test
    fun `vehicle name resolution uses year make model`() {
        val v = vehicle(id = "v1", year = 2024, make = "Toyota", model = "GR86")
        val t = track(vehicleId = "v1")
        val csv = export(tracks = listOf(t), vehicles = listOf(v))
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("2024 Toyota GR86", cols[9])
    }

    @Test
    fun `missing vehicle id produces empty vehicle column`() {
        val csv = export(tracks = listOf(track(vehicleId = null)))
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("", cols[9])
    }

    @Test
    fun `unknown vehicle id produces empty vehicle column`() {
        val csv = export(
            tracks = listOf(track(vehicleId = "no-such-id")),
            vehicles = listOf(vehicle(id = "v1")),
        )
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("", cols[9])
    }

    @Test
    fun `multiple tracks produce multiple data rows`() {
        val tracks = listOf(
            track(name = "Track A"),
            track(name = "Track B"),
            track(name = "Track C"),
        )
        val csv = export(tracks = tracks)
        val rows = lines(csv)
        assertEquals(4, rows.size) // 1 header + 3 data
    }

    @Test
    fun `numeric precision preserved for distance`() {
        val csv = export(tracks = listOf(track(distanceMeters = 1234.5)))
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("1234.5", cols[2])
    }

    @Test
    fun `numeric precision preserved for speeds`() {
        val csv = export(tracks = listOf(track(avgSpeedMps = 20.575, maxSpeedMps = 33.33)))
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("20.575", cols[4])
        assertEquals("33.33", cols[5])
    }

    @Test
    fun `duration converted from millis to seconds`() {
        val csv = export(tracks = listOf(track(durationMs = 90_000L)))
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("90", cols[3])
    }

    @Test
    fun `difficulty and surface appear in correct columns`() {
        val csv = export(
            tracks = listOf(track(difficultyRating = "Hard", primarySurface = "Gravel")),
        )
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("Hard", cols[7])
        assertEquals("Gravel", cols[8])
    }

    @Test
    fun `null difficulty and surface produce empty strings`() {
        val csv = export(
            tracks = listOf(track(difficultyRating = null, primarySurface = null)),
        )
        val dataLine = lines(csv)[1]
        val cols = parseCsvLine(dataLine)
        assertEquals("", cols[7])
        assertEquals("", cols[8])
    }
}
