package com.rallytrax.app.data.fuel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.rallytrax.app.data.local.dao.GasStationDao
import com.rallytrax.app.data.local.entity.GasStationEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Periodically fetches gas stations from OSM Overpass API for the user's
 * current region (50km radius) and caches them in Room.
 * Runs on first launch + every 30 days via WorkManager.
 */
@HiltWorker
class GasStationCacheWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gasStationDao: GasStationDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get last known location
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.d(TAG, "No location permission, skipping gas station cache")
                return Result.success()
            }

            val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = try {
                @Suppress("MissingPermission")
                fusedClient.lastLocation.await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not get last location", e)
                return Result.retry()
            }

            if (location == null) {
                Log.d(TAG, "No last known location available")
                return Result.success()
            }

            // Fetch gas stations from Overpass API (50km radius)
            val stations = fetchStationsFromOverpass(location.latitude, location.longitude, RADIUS_M)
            Log.d(TAG, "Fetched ${stations.size} gas stations from Overpass")

            // Clean up old data and insert new
            val sixtyDaysAgo = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
            gasStationDao.deleteOldStations(sixtyDaysAgo)
            gasStationDao.insertStations(stations)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Gas station cache worker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun fetchStationsFromOverpass(
        lat: Double,
        lon: Double,
        radiusM: Int,
    ): List<GasStationEntity> = withContext(Dispatchers.IO) {
        val query = """[out:json];node["amenity"="fuel"](around:$radiusM,$lat,$lon);out body;"""
        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val elements = json.getJSONArray("elements")
            val now = System.currentTimeMillis()
            (0 until elements.length()).mapNotNull { i ->
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name")
                    .ifBlank { tags.optString("brand") }
                    .ifBlank { tags.optString("operator") }
                    .ifBlank { "Gas Station" }
                GasStationEntity(
                    name = name,
                    lat = el.getDouble("lat"),
                    lon = el.getDouble("lon"),
                    brand = tags.optString("brand").takeIf { it.isNotBlank() },
                    fetchedAt = now,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "GasStationCacheWorker"
        private const val RADIUS_M = 50_000 // 50 km
        const val WORK_NAME = "gas_station_cache"
    }
}
