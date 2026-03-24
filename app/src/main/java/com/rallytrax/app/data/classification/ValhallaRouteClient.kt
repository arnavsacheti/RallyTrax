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

    companion object {
        private const val TAG = "ValhallaRouteClient"
        private const val FALLBACK_VALHALLA_URL = "https://valhalla1.openstreetmap.de"
    }
}
