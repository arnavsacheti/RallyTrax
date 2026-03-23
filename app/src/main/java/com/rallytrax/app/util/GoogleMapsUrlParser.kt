package com.rallytrax.app.util

import android.util.Log
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

    /**
     * Parse coordinates from shared text. May perform a network call for short URLs.
     * Must be called off the main thread.
     */
    fun parse(text: String): LatLng? {
        Log.d(TAG, "Parsing shared text: ${text.take(200)}")

        val url = URL_REGEX.find(text)?.value
        if (url != null) {
            val result = parseUrl(url)
            if (result != null) {
                Log.d(TAG, "Extracted coordinates: $result")
                return result
            }
        }

        // Fallback: try extracting bare coordinates from the text itself
        BARE_COORD_REGEX.find(text)?.let { match ->
            val result = toLatLng(match.groupValues[1], match.groupValues[2])
            if (result != null) {
                Log.d(TAG, "Extracted bare coordinates: $result")
                return result
            }
        }

        Log.w(TAG, "Could not extract coordinates from text")
        return null
    }

    private fun parseUrl(url: String): LatLng? {
        // Try extracting coordinates directly from the URL
        extractCoords(url)?.let { return it }

        // If it's a short URL, resolve redirects (up to 5 hops) and try again
        if (url.contains("goo.gl/") || url.contains("maps.app.goo.gl/")) {
            val resolved = resolveRedirects(url, maxHops = 5)
            if (resolved != null && resolved != url) {
                Log.d(TAG, "Resolved short URL to: ${resolved.take(200)}")
                extractCoords(resolved)?.let { return it }
            }
        }

        return null
    }

    private fun extractCoords(url: String): LatLng? {
        // @lat,lng pattern (most common in place/view URLs)
        AT_COORD_REGEX.find(url)?.let { match ->
            return toLatLng(match.groupValues[1], match.groupValues[2])
        }
        // ?q=lat,lng pattern
        Q_PARAM_REGEX.find(url)?.let { match ->
            return toLatLng(match.groupValues[1], match.groupValues[2])
        }
        // /dir/lat,lng/lat,lng pattern (directions — extract first coordinate)
        DIR_COORD_REGEX.find(url)?.let { match ->
            return toLatLng(match.groupValues[1], match.groupValues[2])
        }
        // Bare coordinates anywhere in the URL
        BARE_COORD_REGEX.find(url)?.let { match ->
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
