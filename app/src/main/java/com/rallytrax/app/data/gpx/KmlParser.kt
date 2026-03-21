package com.rallytrax.app.data.gpx

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parses KML files (Google Maps, Google Earth exports) into RallyTrax tracks.
 * Supports:
 * - <LineString><coordinates> (standard KML path)
 * - <gx:Track><gx:coord> (Google Earth extended track format)
 * - <Placemark><name> for track naming
 */
object KmlParser {

    fun parse(inputStream: InputStream): GpxImportResult {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            val trackId = UUID.randomUUID().toString()
            var trackName: String? = null
            var trackDesc: String? = null
            val allPoints = mutableListOf<TrackPointEntity>()

            var insidePlacemark = false
            var insideLineString = false
            var insideGxTrack = false
            var currentTag: String? = null
            var placemarkName: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        currentTag = tag
                        when (tag) {
                            "Placemark" -> {
                                insidePlacemark = true
                                placemarkName = null
                            }
                            "LineString" -> insideLineString = true
                            "Track" -> insideGxTrack = true // gx:Track
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isEmpty()) {
                            eventType = parser.next()
                            continue
                        }

                        when {
                            currentTag == "name" && insidePlacemark -> {
                                placemarkName = text
                                if (trackName == null) trackName = text
                            }
                            currentTag == "description" && insidePlacemark -> {
                                if (trackDesc == null) trackDesc = text
                            }
                            currentTag == "name" && !insidePlacemark -> {
                                if (trackName == null) trackName = text
                            }
                            currentTag == "coordinates" && insideLineString -> {
                                // KML coordinates: "lon,lat,ele lon,lat,ele ..."
                                val coords = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
                                for (coordStr in coords) {
                                    val parts = coordStr.split(",")
                                    if (parts.size >= 2) {
                                        val lon = parts[0].toDoubleOrNull() ?: continue
                                        val lat = parts[1].toDoubleOrNull() ?: continue
                                        val ele = parts.getOrNull(2)?.toDoubleOrNull()
                                        allPoints.add(
                                            TrackPointEntity(
                                                trackId = trackId,
                                                index = allPoints.size,
                                                lat = lat,
                                                lon = lon,
                                                elevation = ele,
                                                timestamp = System.currentTimeMillis() + allPoints.size * 1000L,
                                            )
                                        )
                                    }
                                }
                            }
                            currentTag == "coord" && insideGxTrack -> {
                                // gx:coord: "lon lat ele"
                                val parts = text.split("\\s+".toRegex())
                                if (parts.size >= 2) {
                                    val lon = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    val ele = parts.getOrNull(2)?.toDoubleOrNull()
                                    if (lon != null && lat != null) {
                                        allPoints.add(
                                            TrackPointEntity(
                                                trackId = trackId,
                                                index = allPoints.size,
                                                lat = lat,
                                                lon = lon,
                                                elevation = ele,
                                                timestamp = System.currentTimeMillis() + allPoints.size * 1000L,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "Placemark" -> insidePlacemark = false
                            "LineString" -> insideLineString = false
                            "Track" -> insideGxTrack = false
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            if (allPoints.isEmpty()) {
                throw GpxParseException("No coordinates found in KML file")
            }

            // Compute stats
            var totalDistance = 0.0
            var maxSpeed = 0.0
            var elevationGain = 0.0
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            for (i in allPoints.indices) {
                val pt = allPoints[i]
                minLat = minOf(minLat, pt.lat)
                maxLat = maxOf(maxLat, pt.lat)
                minLon = minOf(minLon, pt.lon)
                maxLon = maxOf(maxLon, pt.lon)

                if (i > 0) {
                    val prev = allPoints[i - 1]
                    totalDistance += haversine(prev.lat, prev.lon, pt.lat, pt.lon)

                    val prevEle = prev.elevation
                    val curEle = pt.elevation
                    if (prevEle != null && curEle != null && curEle > prevEle) {
                        elevationGain += curEle - prevEle
                    }
                }
            }

            val durationMs = if (allPoints.size > 1) {
                allPoints.last().timestamp - allPoints.first().timestamp
            } else 0L
            val avgSpeed = if (durationMs > 0) totalDistance / (durationMs / 1000.0) else 0.0

            val track = TrackEntity(
                id = trackId,
                name = trackName ?: "Imported KML Route",
                description = trackDesc,
                recordedAt = System.currentTimeMillis(),
                durationMs = durationMs,
                distanceMeters = totalDistance,
                maxSpeedMps = maxSpeed,
                avgSpeedMps = avgSpeed,
                elevationGainM = elevationGain,
                boundingBoxNorthLat = if (maxLat != -Double.MAX_VALUE) maxLat else 0.0,
                boundingBoxSouthLat = if (minLat != Double.MAX_VALUE) minLat else 0.0,
                boundingBoxEastLon = if (maxLon != -Double.MAX_VALUE) maxLon else 0.0,
                boundingBoxWestLon = if (minLon != Double.MAX_VALUE) minLon else 0.0,
            )

            return GpxImportResult(track = track, points = allPoints)
        } catch (e: GpxParseException) {
            throw e
        } catch (e: Exception) {
            throw GpxParseException("Failed to parse KML: ${e.message}", e)
        }
    }

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
