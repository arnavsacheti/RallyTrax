package com.rallytrax.app.data.classification

import android.util.Log
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Valhalla's trace_attributes endpoint for map-matching
 * and surface data extraction. Falls back gracefully if Valhalla
 * is unreachable.
 */
@Singleton
class ValhallaSurfaceClient @Inject constructor() {

    /**
     * Send GPS points to Valhalla trace_attributes and extract per-edge
     * surface classifications. Returns a map of point index → surface type.
     */
    suspend fun getTraceAttributes(
        points: List<TrackPointEntity>,
        valhallaUrl: String = DEFAULT_VALHALLA_URL,
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        try {
            if (points.size < 2) return@withContext emptyMap()

            // Sample points (Valhalla has limits, ~2000 points max)
            val sampled = if (points.size > 1500) {
                val step = points.size / 1500
                points.filterIndexed { i, _ -> i % step == 0 }
            } else points

            val shape = JSONArray().apply {
                sampled.forEach { pt ->
                    put(JSONObject().apply {
                        put("lat", pt.lat)
                        put("lon", pt.lon)
                    })
                }
            }

            val request = JSONObject().apply {
                put("shape", shape)
                put("costing", "auto")
                put("shape_match", "map_snap")
                put("filters", JSONObject().apply {
                    put("attributes", JSONArray().apply {
                        put("edge.surface")
                        put("edge.road_class")
                        put("edge.unpaved")
                        put("edge.way_id")
                        put("edge.speed_limit")
                        put("matched.point_index")
                        put("matched.edge_index")
                    })
                    put("action", "include")
                })
            }

            val url = "$valhallaUrl/trace_attributes"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            connection.outputStream.use { os ->
                os.write(request.toString().toByteArray())
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            parseTraceAttributes(response, sampled, points)
        } catch (e: Exception) {
            Log.w(TAG, "Valhalla trace_attributes failed: ${e.message}")
            emptyMap()
        }
    }

    private fun parseTraceAttributes(
        json: String,
        sampledPoints: List<TrackPointEntity>,
        allPoints: List<TrackPointEntity>,
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        try {
            val root = JSONObject(json)
            val edges = root.optJSONArray("edges") ?: return emptyMap()
            val matchedPoints = root.optJSONArray("matched_points") ?: return emptyMap()

            // Build edge index → surface map
            val edgeSurfaces = mutableMapOf<Int, String>()
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)
                val surface = edge.optString("surface", "")
                val unpaved = edge.optBoolean("unpaved", false)
                val roadClass = edge.optString("road_class", "")

                val surfaceType = when {
                    surface.contains("paved_smooth") || surface.contains("paved") ->
                        RouteClassifier.SURFACE_PAVED
                    surface.contains("gravel") || surface.contains("compacted") ->
                        RouteClassifier.SURFACE_GRAVEL
                    surface.contains("dirt") || surface.contains("path") ->
                        RouteClassifier.SURFACE_DIRT
                    surface.contains("cobblestone") || surface.contains("sett") ->
                        RouteClassifier.SURFACE_COBBLESTONE
                    unpaved -> RouteClassifier.SURFACE_GRAVEL
                    roadClass == "motorway" || roadClass == "trunk" || roadClass == "primary" ->
                        RouteClassifier.SURFACE_PAVED // Inferred from road class
                    surface.isNotEmpty() -> RouteClassifier.SURFACE_PAVED
                    else -> "" // No data
                }
                if (surfaceType.isNotEmpty()) {
                    edgeSurfaces[i] = surfaceType
                }
            }

            // Map matched points to edge surfaces, then spread to all points
            for (i in 0 until matchedPoints.length()) {
                val mp = matchedPoints.getJSONObject(i)
                val edgeIndex = mp.optInt("edge_index", -1)
                val surface = edgeSurfaces[edgeIndex] ?: continue

                // Map sampled point index to original point index
                if (i < sampledPoints.size) {
                    val originalIndex = sampledPoints[i].index
                    result[originalIndex] = surface
                }
            }

            // Fill gaps between matched points
            fillGaps(result, allPoints.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Valhalla response: ${e.message}")
        }
        return result
    }

    private fun fillGaps(result: MutableMap<Int, String>, totalPoints: Int) {
        if (result.isEmpty()) return
        val sortedKeys = result.keys.sorted()
        var lastSurface = result[sortedKeys.first()] ?: return

        for (i in 0 until totalPoints) {
            if (result.containsKey(i)) {
                lastSurface = result[i]!!
            } else {
                result[i] = lastSurface
            }
        }
    }

    companion object {
        private const val TAG = "ValhallaSurfaceClient"
        // Default Valhalla URL — user can configure in settings
        const val DEFAULT_VALHALLA_URL = "https://valhalla.rallytrax.app"
    }
}
