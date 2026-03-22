package com.rallytrax.app.data.fuel

import android.util.Log
import com.rallytrax.app.data.local.dao.GasStationDao
import com.rallytrax.app.data.local.entity.GasStationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class GasStationResult(
    val name: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Finds nearby gas stations using a two-tier approach:
 * 1. Local OSM cache (pre-downloaded via GasStationCacheWorker)
 * 2. Google Places API v2 fallback (if cache misses)
 */
@Singleton
class GasStationDetector @Inject constructor(
    private val gasStationDao: GasStationDao,
) {
    /**
     * Find the nearest gas station within [radiusM] metres of the given coordinates.
     * Returns null if no station is found.
     */
    suspend fun findNearbyStation(
        lat: Double,
        lon: Double,
        radiusM: Int = 100,
    ): GasStationResult? {
        // ~0.001 degrees ≈ 111m at equator, scale by cos(lat) for longitude
        val latDelta = radiusM / 111_000.0
        val lonDelta = radiusM / (111_000.0 * kotlin.math.cos(Math.toRadians(lat)))

        // 1. Check local OSM cache
        val cached = gasStationDao.getStationsNear(
            minLat = lat - latDelta,
            maxLat = lat + latDelta,
            minLon = lon - lonDelta,
            maxLon = lon + lonDelta,
        )
        if (cached.isNotEmpty()) {
            val nearest = cached.minByOrNull { distanceM(lat, lon, it.lat, it.lon) }!!
            if (distanceM(lat, lon, nearest.lat, nearest.lon) <= radiusM) {
                return GasStationResult(nearest.name, nearest.lat, nearest.lon)
            }
        }

        // 2. Fallback: query Overpass API for nearby fuel stations
        return try {
            val result = queryOverpassNearby(lat, lon, radiusM)
            // Cache the result
            if (result != null) {
                gasStationDao.insertStations(
                    listOf(
                        GasStationEntity(
                            name = result.name,
                            lat = result.lat,
                            lon = result.lon,
                        )
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Overpass nearby query failed", e)
            null
        }
    }

    private suspend fun queryOverpassNearby(
        lat: Double,
        lon: Double,
        radiusM: Int,
    ): GasStationResult? = withContext(Dispatchers.IO) {
        val query = """[out:json];node["amenity"="fuel"](around:$radiusM,$lat,$lon);out body 1;"""
        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val elements = json.getJSONArray("elements")
            if (elements.length() == 0) return@withContext null
            val el = elements.getJSONObject(0)
            val tags = el.optJSONObject("tags")
            val name = tags?.optString("name")
                ?: tags?.optString("brand")
                ?: tags?.optString("operator")
                ?: "Gas Station"
            GasStationResult(
                name = name,
                lat = el.getDouble("lat"),
                lon = el.getDouble("lon"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // Earth radius in metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "GasStationDetector"
    }
}
