package com.rallytrax.app.data.gpx

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class GpxImportResult(
    val track: TrackEntity,
    val points: List<TrackPointEntity>,
)

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object GpxParser {

    fun parse(inputStream: InputStream): GpxImportResult {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            val trackId = UUID.randomUUID().toString()
            var trackName: String? = null
            var trackDesc: String? = null
            var trackTime: Long? = null
            var tags = ""

            // Extension values
            var extDurationMs: Long? = null
            var extDistanceMeters: Double? = null
            var extMaxSpeedMps: Double? = null
            var extAvgSpeedMps: Double? = null
            var extElevationGainM: Double? = null

            val points = mutableListOf<TrackPointEntity>()
            var pointIndex = 0

            // Current trkpt state
            var currentLat: Double? = null
            var currentLon: Double? = null
            var currentEle: Double? = null
            var currentTime: Long? = null
            var currentSpeed: Double? = null
            var currentBearing: Double? = null
            var currentAccuracy: Float? = null

            var inTrk = false
            var inTrkSeg = false
            var inTrkPt = false
            var inMetadata = false
            var inExtensions = false
            var inTrkExtensions = false
            var currentTag: String? = null
            var textContent = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        textContent.clear()

                        when {
                            tag == "metadata" -> inMetadata = true
                            tag == "trk" -> inTrk = true
                            tag == "trkseg" -> inTrkSeg = true
                            tag == "trkpt" && inTrkSeg -> {
                                inTrkPt = true
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                currentEle = null
                                currentTime = null
                                currentSpeed = null
                                currentBearing = null
                                currentAccuracy = null
                            }
                            tag == "extensions" && inTrkPt -> inExtensions = true
                            tag == "extensions" && inTrk && !inTrkPt -> inTrkExtensions = true
                        }
                        currentTag = tag
                    }

                    XmlPullParser.TEXT -> {
                        textContent.append(parser.text?.trim() ?: "")
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        val text = textContent.toString().trim()

                        when {
                            tag == "metadata" -> inMetadata = false
                            tag == "trk" -> inTrk = false
                            tag == "trkseg" -> inTrkSeg = false
                            tag == "extensions" && inTrkPt -> inExtensions = false
                            tag == "extensions" && inTrk && !inTrkPt -> inTrkExtensions = false

                            // Metadata / track-level name
                            tag == "name" && (inMetadata || (inTrk && !inTrkPt)) && trackName == null -> {
                                trackName = text
                            }
                            tag == "desc" && (inMetadata || (inTrk && !inTrkPt)) -> {
                                trackDesc = text
                            }
                            tag == "time" && inMetadata && !inTrk -> {
                                trackTime = parseIsoTime(text)
                            }

                            // Track extensions
                            inTrkExtensions -> {
                                when {
                                    tag.endsWith("durationMs") -> extDurationMs = text.toLongOrNull()
                                    tag.endsWith("distanceMeters") -> extDistanceMeters = text.toDoubleOrNull()
                                    tag.endsWith("maxSpeedMps") -> extMaxSpeedMps = text.toDoubleOrNull()
                                    tag.endsWith("avgSpeedMps") -> extAvgSpeedMps = text.toDoubleOrNull()
                                    tag.endsWith("elevationGainM") -> extElevationGainM = text.toDoubleOrNull()
                                    tag.endsWith("tags") -> tags = text
                                }
                            }

                            // Point-level elements
                            tag == "ele" && inTrkPt -> currentEle = text.toDoubleOrNull()
                            tag == "time" && inTrkPt -> currentTime = parseIsoTime(text)

                            // Point extensions
                            inExtensions && inTrkPt -> {
                                when {
                                    tag.endsWith("speed") -> currentSpeed = text.toDoubleOrNull()
                                    tag.endsWith("bearing") -> currentBearing = text.toDoubleOrNull()
                                    tag.endsWith("accuracy") -> currentAccuracy = text.toFloatOrNull()
                                }
                            }

                            // End of trkpt
                            tag == "trkpt" && inTrkPt -> {
                                val lat = currentLat
                                val lon = currentLon
                                if (lat != null && lon != null) {
                                    points.add(
                                        TrackPointEntity(
                                            trackId = trackId,
                                            index = pointIndex++,
                                            lat = lat,
                                            lon = lon,
                                            elevation = currentEle,
                                            timestamp = currentTime ?: System.currentTimeMillis(),
                                            speed = currentSpeed,
                                            bearing = currentBearing,
                                            accuracy = currentAccuracy,
                                        )
                                    )
                                }
                                inTrkPt = false
                            }
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            if (points.isEmpty()) {
                throw GpxParseException("GPX file contains no track points")
            }

            // Calculate stats from points if not provided in extensions
            val firstTimestamp = points.first().timestamp
            val lastTimestamp = points.last().timestamp
            val calculatedDuration = lastTimestamp - firstTimestamp

            var totalDistance = 0.0
            var maxSpeed = 0.0
            var elevationGain = 0.0
            var speedSum = 0.0
            var speedCount = 0
            var prevElevation: Double? = null
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            for (i in points.indices) {
                val p = points[i]
                minLat = minOf(minLat, p.lat)
                maxLat = maxOf(maxLat, p.lat)
                minLon = minOf(minLon, p.lon)
                maxLon = maxOf(maxLon, p.lon)

                if (i > 0) {
                    totalDistance += haversine(
                        points[i - 1].lat, points[i - 1].lon,
                        p.lat, p.lon,
                    )
                }

                p.speed?.let { spd ->
                    maxSpeed = max(maxSpeed, spd)
                    speedSum += spd
                    speedCount++
                }

                p.elevation?.let { ele ->
                    prevElevation?.let { prev ->
                        val delta = ele - prev
                        if (delta > 2.0) elevationGain += delta
                    }
                    prevElevation = ele
                }
            }

            val avgSpeed = if (speedCount > 0) speedSum / speedCount else 0.0

            val track = TrackEntity(
                id = trackId,
                name = trackName ?: "Imported Track",
                description = trackDesc,
                recordedAt = trackTime ?: firstTimestamp,
                durationMs = extDurationMs ?: calculatedDuration,
                distanceMeters = extDistanceMeters ?: totalDistance,
                maxSpeedMps = extMaxSpeedMps ?: maxSpeed,
                avgSpeedMps = extAvgSpeedMps ?: avgSpeed,
                elevationGainM = extElevationGainM ?: elevationGain,
                boundingBoxNorthLat = maxLat,
                boundingBoxSouthLat = minLat,
                boundingBoxEastLon = maxLon,
                boundingBoxWestLon = minLon,
                tags = tags,
            )

            return GpxImportResult(track, points)
        } catch (e: GpxParseException) {
            throw e
        } catch (e: Exception) {
            throw GpxParseException("Failed to parse GPX file: ${e.message}", e)
        }
    }

    private fun parseIsoTime(text: String): Long? {
        return try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(text)).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
