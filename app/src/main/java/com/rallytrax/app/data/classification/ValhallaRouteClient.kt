package com.rallytrax.app.data.classification

import android.util.Log
import com.rallytrax.app.recording.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Valhalla's /route endpoint. Given a list of waypoints,
 * fetches the actual driving route geometry (decoded from the response shape).
 */
@Singleton
class ValhallaRouteClient @Inject constructor() {

    data class RouteResult(
        val points: List<LatLng>,
        val distanceMeters: Double,
        val durationSeconds: Double,
    )

    /**
     * Fetch a driving route between [waypoints] via Valhalla.
     * Tries the configured Valhalla URL first, then falls back to the public OSM instance.
     * Returns the full route geometry as a list of LatLng, or null on failure.
     */
    suspend fun fetchRoute(
        waypoints: List<LatLng>,
        valhallaUrl: String = ValhallaSurfaceClient.DEFAULT_VALHALLA_URL,
    ): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null

        // Try primary Valhalla, then fallback to public instance
        val urls = listOfNotNull(
            valhallaUrl,
            FALLBACK_VALHALLA_URL.takeIf { it != valhallaUrl },
        )
        for (baseUrl in urls) {
            val result = fetchRouteFrom(waypoints, baseUrl)
            if (result != null) return@withContext result
        }
        null
    }

    private fun fetchRouteFrom(
        waypoints: List<LatLng>,
        valhallaUrl: String,
    ): RouteResult? {
        try {
            val locations = JSONArray().apply {
                waypoints.forEach { wp ->
                    put(JSONObject().apply {
                        put("lat", wp.latitude)
                        put("lon", wp.longitude)
                    })
                }
            }

            val request = JSONObject().apply {
                put("locations", locations)
                put("costing", "auto")
                put("directions_options", JSONObject().apply {
                    put("units", "kilometers")
                })
            }

            val url = "$valhallaUrl/route"
            Log.d(TAG, "Fetching route from $valhallaUrl: ${waypoints.size} waypoints")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            connection.outputStream.use { os ->
                os.write(request.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "no body"
                } catch (_: Exception) { "unreadable" }
                Log.w(TAG, "Valhalla route returned $responseCode from $valhallaUrl: ${errorBody.take(200)}")
                connection.disconnect()
                return null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            return parseRouteResponse(response)
        } catch (e: Exception) {
            Log.w(TAG, "Valhalla route failed from $valhallaUrl: ${e.message}")
            return null
        }
    }

    private fun parseRouteResponse(json: String): RouteResult? {
        try {
            val root = JSONObject(json)
            val trip = root.getJSONObject("trip")
            val legs = trip.getJSONArray("legs")

            val allPoints = mutableListOf<LatLng>()
            var totalDistance = 0.0
            var totalDuration = 0.0

            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                val shape = leg.getString("shape")
                val decoded = decodePolyline6(shape)
                // Avoid duplicating the junction point between legs
                if (allPoints.isNotEmpty() && decoded.isNotEmpty()) {
                    allPoints.addAll(decoded.drop(1))
                } else {
                    allPoints.addAll(decoded)
                }

                val summary = leg.getJSONObject("summary")
                totalDistance += summary.getDouble("length") * 1000.0 // km → m
                totalDuration += summary.getDouble("time")
            }

            Log.d(TAG, "Route decoded: ${allPoints.size} points, ${totalDistance.toInt()}m")
            return if (allPoints.size >= 2) {
                RouteResult(
                    points = allPoints,
                    distanceMeters = totalDistance,
                    durationSeconds = totalDuration,
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Valhalla route response: ${e.message}")
            return null
        }
    }

    /**
     * Decode a Google-style encoded polyline with 6-digit precision
     * (Valhalla uses 1e6, not 1e5 like Google Maps).
     */
    private fun decodePolyline6(encoded: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var b: Int
            var dlat = 0
            do {
                b = encoded[index++].code - 63
                dlat = dlat or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (dlat and 1 != 0) (dlat shr 1).inv() else dlat shr 1

            shift = 0
            var dlng = 0
            do {
                b = encoded[index++].code - 63
                dlng = dlng or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (dlng and 1 != 0) (dlng shr 1).inv() else dlng shr 1

            result.add(LatLng(lat / 1e6, lng / 1e6))
        }

        return result
    }

    /**
     * Fetch elevation data for a list of points using Valhalla's /height endpoint.
     * Returns a new list of LatLng with elevation populated, or the original list on failure.
     */
    suspend fun fetchHeight(
        points: List<LatLng>,
        valhallaUrl: String = ValhallaSurfaceClient.DEFAULT_VALHALLA_URL,
    ): List<LatLng> = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext points

        val urls = listOfNotNull(
            valhallaUrl,
            FALLBACK_VALHALLA_URL.takeIf { it != valhallaUrl },
        )
        for (baseUrl in urls) {
            val result = fetchHeightFrom(points, baseUrl)
            if (result != null) return@withContext result
        }
        points
    }

    private fun fetchHeightFrom(points: List<LatLng>, valhallaUrl: String): List<LatLng>? {
        try {
            // Valhalla /height has a limit; sample if needed and interpolate
            val maxPoints = 1500
            val sampled = if (points.size > maxPoints) {
                val step = points.size.toDouble() / maxPoints
                (0 until maxPoints).map { i -> points[(i * step).toInt()] }
            } else {
                points
            }

            val shape = JSONArray().apply {
                sampled.forEach { pt ->
                    put(JSONObject().apply {
                        put("lat", pt.latitude)
                        put("lon", pt.longitude)
                    })
                }
            }

            val request = JSONObject().apply {
                put("shape", shape)
                put("range", false)
            }

            val url = "$valhallaUrl/height"
            Log.d(TAG, "Fetching height from $valhallaUrl: ${sampled.size} points")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            connection.outputStream.use { os ->
                os.write(request.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "Valhalla height returned $responseCode from $valhallaUrl")
                connection.disconnect()
                return null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val root = JSONObject(response)
            val heights = root.getJSONArray("height")

            if (sampled.size == points.size) {
                // No sampling was needed — direct 1:1 mapping
                return points.mapIndexed { i, pt ->
                    val h = heights.optDouble(i, Double.NaN)
                    if (!h.isNaN()) pt.copy(elevation = h) else pt
                }
            } else {
                // Interpolate elevation back to original points
                val step = points.size.toDouble() / sampled.size
                return points.mapIndexed { i, pt ->
                    val sampledIdx = (i / step).toInt().coerceAtMost(sampled.size - 1)
                    val h = heights.optDouble(sampledIdx, Double.NaN)
                    if (!h.isNaN()) pt.copy(elevation = h) else pt
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Valhalla height failed from $valhallaUrl: ${e.message}")
            return null
        }
    }

    companion object {
        private const val TAG = "ValhallaRouteClient"
        private const val FALLBACK_VALHALLA_URL = "https://valhalla1.openstreetmap.de"
    }
}
