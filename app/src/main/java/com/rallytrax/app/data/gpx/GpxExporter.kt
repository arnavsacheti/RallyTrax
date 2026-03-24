package com.rallytrax.app.data.gpx

import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GpxExporter {

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun export(
        track: TrackEntity,
        points: List<TrackPointEntity>,
        outputStream: OutputStream,
        paceNotes: List<PaceNoteEntity> = emptyList(),
    ) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write(
                """<gpx version="1.1" creator="RallyTrax" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xmlns:rt="http://rallytrax.com/gpx/1" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 """ +
                    """http://www.topografix.com/GPX/1/1/gpx.xsd">"""
            )
            writer.newLine()

            // Metadata
            writer.write("  <metadata>")
            writer.newLine()
            writer.write("    <name>${escapeXml(track.name)}</name>")
            writer.newLine()
            if (!track.description.isNullOrBlank()) {
                writer.write("    <desc>${escapeXml(track.description)}</desc>")
                writer.newLine()
            }
            val timeStr = formatTimestamp(track.recordedAt)
            writer.write("    <time>$timeStr</time>")
            writer.newLine()
            writer.write("  </metadata>")
            writer.newLine()

            // Track
            writer.write("  <trk>")
            writer.newLine()
            writer.write("    <name>${escapeXml(track.name)}</name>")
            writer.newLine()

            // Extensions with track metadata
            writer.write("    <extensions>")
            writer.newLine()
            writer.write("      <rt:durationMs>${track.durationMs}</rt:durationMs>")
            writer.newLine()
            writer.write("      <rt:distanceMeters>${track.distanceMeters}</rt:distanceMeters>")
            writer.newLine()
            writer.write("      <rt:maxSpeedMps>${track.maxSpeedMps}</rt:maxSpeedMps>")
            writer.newLine()
            writer.write("      <rt:avgSpeedMps>${track.avgSpeedMps}</rt:avgSpeedMps>")
            writer.newLine()
            writer.write("      <rt:elevationGainM>${track.elevationGainM}</rt:elevationGainM>")
            writer.newLine()
            if (track.tags.isNotBlank()) {
                writer.write("      <rt:tags>${escapeXml(track.tags)}</rt:tags>")
                writer.newLine()
            }

            // Pace notes in extensions
            if (paceNotes.isNotEmpty()) {
                writer.write("      <rt:paceNotes>")
                writer.newLine()
                for (note in paceNotes) {
                    writer.write("        <rt:paceNote")
                    writer.write(""" pointIndex="${note.pointIndex}"""")
                    writer.write(""" distanceFromStart="${note.distanceFromStart}"""")
                    writer.write(""" noteType="${note.noteType.name}"""")
                    writer.write(""" severity="${note.severity}"""")
                    writer.write(""" modifier="${note.modifier.name}"""")
                    writer.write(""" callDistanceM="${note.callDistanceM}"""")
                    writer.write(""" severityHalf="${note.severityHalf.name}"""")
                    writer.write(""" conjunction="${note.conjunction.name}"""")
                    note.turnRadiusM?.let { writer.write(""" turnRadiusM="$it"""") }
                    writer.write(">")
                    writer.write(escapeXml(note.callText))
                    writer.write("</rt:paceNote>")
                    writer.newLine()
                }
                writer.write("      </rt:paceNotes>")
                writer.newLine()
            }

            writer.write("    </extensions>")
            writer.newLine()

            // Track segment
            writer.write("    <trkseg>")
            writer.newLine()

            for (point in points) {
                writer.write("""      <trkpt lat="${point.lat}" lon="${point.lon}">""")
                writer.newLine()

                point.elevation?.let { ele ->
                    writer.write("        <ele>$ele</ele>")
                    writer.newLine()
                }

                writer.write("        <time>${formatTimestamp(point.timestamp)}</time>")
                writer.newLine()

                // Extensions for speed, bearing, accuracy
                val hasExtensions = point.speed != null || point.bearing != null || point.accuracy != null
                if (hasExtensions) {
                    writer.write("        <extensions>")
                    writer.newLine()
                    point.speed?.let {
                        writer.write("          <rt:speed>$it</rt:speed>")
                        writer.newLine()
                    }
                    point.bearing?.let {
                        writer.write("          <rt:bearing>$it</rt:bearing>")
                        writer.newLine()
                    }
                    point.accuracy?.let {
                        writer.write("          <rt:accuracy>$it</rt:accuracy>")
                        writer.newLine()
                    }
                    writer.write("        </extensions>")
                    writer.newLine()
                }

                writer.write("      </trkpt>")
                writer.newLine()
            }

            writer.write("    </trkseg>")
            writer.newLine()
            writer.write("  </trk>")
            writer.newLine()
            writer.write("</gpx>")
            writer.newLine()
        }
    }

    private fun formatTimestamp(epochMs: Long): String {
        return isoFormatter.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
