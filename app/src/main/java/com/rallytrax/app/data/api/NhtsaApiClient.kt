package com.rallytrax.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class NhtsaMake(val makeId: Int, val makeName: String)
data class NhtsaModel(val modelId: Int, val modelName: String)

data class NhtsaVinResult(
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val trim: String? = null,
    val engineDisplacementL: Double? = null,
    val cylinders: Int? = null,
    val horsePower: Int? = null,
    val drivetrain: String? = null,
    val transmissionType: String? = null,
    val transmissionSpeeds: Int? = null,
    val curbWeightKg: Double? = null,
    val fuelType: String? = null,
    val errorCode: String? = null,
    val errorText: String? = null,
)

@Singleton
class NhtsaApiClient @Inject constructor() {

    private data class CachedResult<T>(val data: T, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, CachedResult<Any>>()
    private val cacheTtlMs = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun getMakesByYear(year: Int): List<NhtsaMake> {
        val cacheKey = "makes:$year"
        @Suppress("UNCHECKED_CAST")
        getCached<List<NhtsaMake>>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            val url = "$BASE_URL/vehicles/GetMakesForVehicleType/car?format=json"
            val json = fetchJson(url)
            val results = json.getJSONArray("Results")
            val makes = (0 until results.length()).map { i ->
                val obj = results.getJSONObject(i)
                NhtsaMake(
                    makeId = obj.optInt("MakeId"),
                    makeName = obj.optString("MakeName", ""),
                )
            }.filter { it.makeName.isNotBlank() }
                .sortedBy { it.makeName }
            putCache(cacheKey, makes)
            makes
        }
    }

    suspend fun getModelsByMakeAndYear(make: String, year: Int): List<NhtsaModel> {
        val cacheKey = "models:$make:$year"
        @Suppress("UNCHECKED_CAST")
        getCached<List<NhtsaModel>>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            val encodedMake = java.net.URLEncoder.encode(make, "UTF-8")
            val url = "$BASE_URL/vehicles/GetModelsForMakeYear/make/$encodedMake/modelyear/$year?format=json"
            val json = fetchJson(url)
            val results = json.getJSONArray("Results")
            val models = (0 until results.length()).map { i ->
                val obj = results.getJSONObject(i)
                NhtsaModel(
                    modelId = obj.optInt("Model_ID"),
                    modelName = obj.optString("Model_Name", ""),
                )
            }.filter { it.modelName.isNotBlank() }
                .sortedBy { it.modelName }
            putCache(cacheKey, models)
            models
        }
    }

    suspend fun decodeVin(vin: String): NhtsaVinResult = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/vehicles/DecodeVinValues/$vin?format=json"
        val json = fetchJson(url)
        val results = json.getJSONArray("Results")
        if (results.length() == 0) {
            return@withContext NhtsaVinResult(errorText = "No results for VIN")
        }
        val r = results.getJSONObject(0)
        NhtsaVinResult(
            year = r.optString("ModelYear").toIntOrNull(),
            make = r.optString("Make").takeIf { it.isNotBlank() },
            model = r.optString("Model").takeIf { it.isNotBlank() },
            trim = r.optString("Trim").takeIf { it.isNotBlank() },
            engineDisplacementL = r.optString("DisplacementL").toDoubleOrNull(),
            cylinders = r.optString("EngineCylinders").toIntOrNull(),
            horsePower = r.optString("EngineHP").toDoubleOrNull()?.toInt(),
            drivetrain = r.optString("DriveType").takeIf { it.isNotBlank() },
            transmissionType = r.optString("TransmissionStyle").takeIf { it.isNotBlank() },
            transmissionSpeeds = r.optString("TransmissionSpeeds").toIntOrNull(),
            curbWeightKg = r.optString("GVWR").toDoubleOrNull()?.let { it * 0.453592 }, // lbs to kg
            fuelType = r.optString("FuelTypePrimary").takeIf { it.isNotBlank() },
            errorCode = r.optString("ErrorCode").takeIf { it != "0" },
            errorText = r.optString("ErrorText").takeIf { it.isNotBlank() && it != "0 - VIN decoded clean" },
        )
    }

    private fun fetchJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val cached = cache[key] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > cacheTtlMs) {
            cache.remove(key)
            return null
        }
        return cached.data as? T
    }

    private fun putCache(key: String, data: Any) {
        cache[key] = CachedResult(data, System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "NhtsaApiClient"
        private const val BASE_URL = "https://vpic.nhtsa.dot.gov/api"
    }
}
