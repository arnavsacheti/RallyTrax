package com.rallytrax.app.util

import android.util.Log
import com.rallytrax.app.recording.LatLng
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts coordinates and route info from text shared by Google Maps.
 *
 * Handles these URL patterns:
 *  - https://www.google.com/maps/@47.123,-122.456,15z
 *  - https://www.google.com/maps/place/.../@47.123,-122.456,15z
 *  - https://www.google.com/maps/dir/.../@47.123,-122.456,15z
 *  - https://www.google.com/maps/dir/47.123,-122.456/47.789,-122.012
 *  - https://www.google.com/maps?q=47.123,-122.456
 *  - https://maps.app.goo.gl/xxxxx  (short URL – resolved via redirect)
 *  - https://goo.gl/maps/xxxxx
 *  - Plain text containing "47.123, -122.456" coordinates
 */
object GoogleMapsUrlParser {

    private const val TAG = "GoogleMapsUrlParser"

    // Matches @lat,lng in a Google Maps URL
    private val AT_COORD_REGEX = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")

    // Matches ?q=lat,lng or &q=lat,lng
    private val Q_PARAM_REGEX = Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""")

    // Matches coordinates in /dir/ paths: /dir/lat,lng/lat,lng
    private val DIR_COORD_REGEX = Regex("""/dir/.*?(-?\d+\.\d{3,}),(-?\d+\.\d{3,})""")

    // Matches bare coordinates in text like "47.123, -122.456"
    private val BARE_COORD_REGEX = Regex("""(-?\d{1,3}\.\d{3,}),\s*(-?\d{1,3}\.\d{3,})""")

    // Matches a URL in surrounding text
    private val URL_REGEX = Regex("""https?://\S+""")

    // Matches all coordinate-like segments in a /dir/ URL path
    private val DIR_SEGMENT_REGEX = Regex("""(-?\d{1,3}\.\d{3,}),(-?\d{1,3}\.\d{3,})""")

    // Matches embedded lat/lng in Google Maps data params: !3d<lat>!4d<lng>
    private val DATA_COORD_REGEX = Regex("""!3d(-?\d+\.?\d+).*?!4d(-?\d+\.?\d+)""")

    // Matches place name in /place/Name/ URLs
    private val PLACE_NAME_REGEX = Regex("""/place/([^/@]+)""")

    /**
     * Result of parsing a shared Google Maps link.
     */
    data class ParseResult(
        val waypoints: List<LatLng>,
        val name: String? = null,
    )

    /**
     * Parse coordinates from shared text. May perform a network call for short URLs.
     * Must be called off the main thread.
     * Returns a ParseResult with all waypoints found, or null if nothing could be extracted.
     */
    fun parseRoute(text: String): ParseResult? {
        Log.d(TAG, "Parsing shared text: ${text.take(200)}")

        val url = URL_REGEX.find(text)?.value
        if (url != null) {
            val result = parseUrlFull(url)
            if (result != null) {
                Log.d(TAG, "Extracted ${result.waypoints.size} waypoint(s), name=${result.name}")
                return result
            }
        }

        // Fallback: try extracting bare coordinates from the text itself
        BARE_COORD_REGEX.find(text)?.let { match ->
            val coord = toLatLng(match.groupValues[1], match.groupValues[2])
            if (coord != null) {
                Log.d(TAG, "Extracted bare coordinates: $coord")
                return ParseResult(waypoints = listOf(coord))
            }
        }

        Log.w(TAG, "Could not extract coordinates from text")
        return null
    }

    /** Legacy single-coordinate API kept for compatibility. */
    fun parse(text: String): LatLng? = parseRoute(text)?.waypoints?.firstOrNull()

    private fun parseUrlFull(url: String): ParseResult? {
        // Try extracting from the URL directly
        extractFull(url)?.let { return it }

        // If it's a short URL, resolve redirects with retry
        if (url.contains("goo.gl/") || url.contains("maps.app.goo.gl/")) {
            repeat(3) { attempt ->
                val resolved = resolveRedirects(url, maxHops = 5)
                if (resolved != null && resolved != url) {
                    Log.d(TAG, "Resolved short URL to: ${resolved.take(200)}")
                    extractFull(resolved)?.let { return it }
                }
                if (attempt < 2) {
                    Log.d(TAG, "Retry ${attempt + 1} for short URL resolution")
                    Thread.sleep(500L * (attempt + 1))
                }
            }
        }

        return null
    }

    private fun extractFull(url: String): ParseResult? {
        val waypoints = mutableListOf<LatLng>()
        var name: String? = null

        // Extract place name if present
        PLACE_NAME_REGEX.find(url)?.let { match ->
            name = java.net.URLDecoder.decode(match.groupValues[1], "UTF-8").replace('+', ' ')
        }

        // For /dir/ URLs, extract all coordinate segments from the path
        if (url.contains("/dir/")) {
            val pathPart = url.substringBefore("?").substringBefore("#")
            val dirPath = pathPart.substringAfter("/dir/")
            val segments = dirPath.split("/")
            for (segment in segments) {
                DIR_SEGMENT_REGEX.find(segment)?.let { match ->
                    toLatLng(match.groupValues[1], match.groupValues[2])?.let { waypoints.add(it) }
                }
            }
            // Also check for embedded coords in data params (e.g. !3d37.615!4d-122.389)
            DATA_COORD_REGEX.findAll(url).forEach { match ->
                toLatLng(match.groupValues[1], match.groupValues[2])?.let { coord ->
                    if (waypoints.none { it.latitude == coord.latitude && it.longitude == coord.longitude }) {
                        waypoints.add(coord)
                    }
                }
            }
        }

        // If no /dir/ waypoints, try other patterns for single coordinate
        if (waypoints.isEmpty()) {
            AT_COORD_REGEX.find(url)?.let { match ->
                toLatLng(match.groupValues[1], match.groupValues[2])?.let { waypoints.add(it) }
            }
        }
        if (waypoints.isEmpty()) {
            Q_PARAM_REGEX.find(url)?.let { match ->
                toLatLng(match.groupValues[1], match.groupValues[2])?.let { waypoints.add(it) }
            }
        }
        if (waypoints.isEmpty()) {
            BARE_COORD_REGEX.find(url)?.let { match ->
                toLatLng(match.groupValues[1], match.groupValues[2])?.let { waypoints.add(it) }
            }
        }

        return if (waypoints.isNotEmpty()) ParseResult(waypoints = waypoints, name = name) else null
    }

    private fun toLatLng(lat: String, lng: String): LatLng? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lng.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return LatLng(latitude, longitude)
    }

    /**
     * Follow redirects up to [maxHops] times to resolve short URLs.
     * Google's short URLs (maps.app.goo.gl) often redirect multiple times.
     */
    private fun resolveRedirects(shortUrl: String, maxHops: Int = 5): String? {
        var currentUrl = shortUrl
        repeat(maxHops) { hop ->
            try {
                val connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.connect()
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                connection.disconnect()

                if (code in 301..308 && location != null) {
                    Log.d(TAG, "Redirect hop $hop: $code -> ${location.take(100)}")
                    currentUrl = if (location.startsWith("/")) {
                        // Relative redirect — construct absolute URL
                        val base = URL(currentUrl)
                        "${base.protocol}://${base.host}$location"
                    } else {
                        location
                    }
                } else {
                    // No more redirects — this is the final URL
                    return currentUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Redirect resolution failed at hop $hop: ${e.message}")
                return if (currentUrl != shortUrl) currentUrl else null
            }
        }
        return currentUrl
    }
}
