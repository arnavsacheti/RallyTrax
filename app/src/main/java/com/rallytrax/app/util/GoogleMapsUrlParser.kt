package com.rallytrax.app.util

import com.rallytrax.app.recording.LatLng
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts a lat/lng coordinate from text shared by Google Maps.
 *
 * Handles these URL patterns:
 *  - https://www.google.com/maps/@47.123,-122.456,15z
 *  - https://www.google.com/maps/place/.../@47.123,-122.456,15z
 *  - https://www.google.com/maps/dir/.../@47.123,-122.456,15z
 *  - https://www.google.com/maps?q=47.123,-122.456
 *  - https://maps.app.goo.gl/xxxxx  (short URL – resolved via redirect)
 *  - https://goo.gl/maps/xxxxx
 */
object GoogleMapsUrlParser {

    // Matches @lat,lng in a Google Maps URL
    private val AT_COORD_REGEX = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")

    // Matches ?q=lat,lng or &q=lat,lng
    private val Q_PARAM_REGEX = Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""")

    // Matches a URL in surrounding text
    private val URL_REGEX = Regex("""https?://\S+""")

    /**
     * Parse coordinates from shared text. May perform a network call for short URLs.
     * Must be called off the main thread.
     */
    fun parse(text: String): LatLng? {
        val url = URL_REGEX.find(text)?.value ?: return null
        return parseUrl(url)
    }

    private fun parseUrl(url: String): LatLng? {
        // Try extracting coordinates directly from the URL
        extractCoords(url)?.let { return it }

        // If it's a short URL, resolve the redirect and try again
        if (url.contains("goo.gl/") || url.contains("maps.app.goo.gl/")) {
            val resolved = resolveRedirect(url)
            if (resolved != null && resolved != url) {
                extractCoords(resolved)?.let { return it }
            }
        }

        return null
    }

    private fun extractCoords(url: String): LatLng? {
        AT_COORD_REGEX.find(url)?.let { match ->
            return toLatLng(match.groupValues[1], match.groupValues[2])
        }
        Q_PARAM_REGEX.find(url)?.let { match ->
            return toLatLng(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    private fun toLatLng(lat: String, lng: String): LatLng? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lng.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return LatLng(latitude, longitude)
    }

    private fun resolveRedirect(shortUrl: String): String? {
        return try {
            val connection = URL(shortUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.connect()
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            location
        } catch (_: Exception) {
            null
        }
    }
}
