package com.rallytrax.app.data.gpx

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Auto-detects file format (GPX vs KML) and delegates to the appropriate parser.
 * Works by peeking at the root XML element.
 */
object TrackImporter {

    fun import(inputStream: InputStream): GpxImportResult {
        // Buffer the stream so we can peek at the beginning
        val buffered = BufferedInputStream(inputStream, 4096)
        buffered.mark(4096)

        // Read enough to find the root element
        val header = ByteArray(2048)
        val bytesRead = buffered.read(header)
        buffered.reset()

        if (bytesRead <= 0) {
            throw GpxParseException("Empty file")
        }

        val headerStr = String(header, 0, bytesRead, Charsets.UTF_8).lowercase()

        return when {
            headerStr.contains("<gpx") -> GpxParser.parse(buffered)
            headerStr.contains("<kml") -> KmlParser.parse(buffered)
            else -> {
                // Try GPX as fallback (most common format for track files)
                try {
                    GpxParser.parse(buffered)
                } catch (e: Exception) {
                    throw GpxParseException(
                        "Unrecognized file format. RallyTrax supports GPX and KML files.",
                        e,
                    )
                }
            }
        }
    }
}
