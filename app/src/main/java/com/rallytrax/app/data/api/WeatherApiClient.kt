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

data class WeatherCondition(
    val conditionCode: Int,
    val conditionGroup: String,
    val description: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windSpeedMps: Double,
    val windDirectionDeg: Double,
    val gustSpeedMps: Double?,
    val visibility: Int,
    val cloudCoverPercent: Int,
    val precipitationMmH: Double?,
    val timestamp: Long,
)

data class WeatherForecast(
    val current: WeatherCondition,
    val hourly: List<WeatherCondition>,
)

/**
 * Weather API client using OpenWeatherMap (primary, requires API key) with
 * Open-Meteo as a free fallback (no key required).
 */
@Singleton
class WeatherApiClient @Inject constructor() {

    private data class CachedResult<T>(val data: T, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, CachedResult<Any>>()
    private val cacheTtlMs = 30 * 60 * 1000L // 30 minutes

    suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        apiKey: String? = null,
    ): WeatherCondition? {
        val cacheKey = "current:%.3f:%.3f".format(lat, lon)
        @Suppress("UNCHECKED_CAST")
        getCached<WeatherCondition>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            val result = if (!apiKey.isNullOrBlank()) {
                try {
                    fetchCurrentFromOwm(lat, lon, apiKey)
                } catch (e: Exception) {
                    Log.w(TAG, "OWM current failed, falling back to Open-Meteo", e)
                    fetchCurrentFromOpenMeteo(lat, lon)
                }
            } else {
                fetchCurrentFromOpenMeteo(lat, lon)
            }
            result?.let { putCache(cacheKey, it) }
            result
        }
    }

    suspend fun getForecast(
        lat: Double,
        lon: Double,
        hours: Int = 4,
        apiKey: String? = null,
    ): WeatherForecast? {
        val cacheKey = "forecast:%.3f:%.3f:%d".format(lat, lon, hours)
        @Suppress("UNCHECKED_CAST")
        getCached<WeatherForecast>(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            val result = if (!apiKey.isNullOrBlank()) {
                try {
                    fetchForecastFromOwm(lat, lon, apiKey, hours)
                } catch (e: Exception) {
                    Log.w(TAG, "OWM forecast failed, falling back to Open-Meteo", e)
                    fetchForecastFromOpenMeteo(lat, lon, hours)
                }
            } else {
                fetchForecastFromOpenMeteo(lat, lon, hours)
            }
            result?.let { putCache(cacheKey, it) }
            result
        }
    }

    // ── OpenWeatherMap ──────────────────────────────────────────────────

    private fun fetchCurrentFromOwm(lat: Double, lon: Double, apiKey: String): WeatherCondition? {
        val url = "$OWM_BASE/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric"
        val json = fetchJson(url) ?: return null
        return parseOwmCurrent(json)
    }

    private fun fetchForecastFromOwm(
        lat: Double,
        lon: Double,
        apiKey: String,
        hours: Int,
    ): WeatherForecast? {
        val cnt = ((hours + 2) / 3).coerceAtLeast(1)
        val url = "$OWM_BASE/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=metric&cnt=$cnt"
        val json = fetchJson(url) ?: return null

        val current = fetchCurrentFromOwm(lat, lon, apiKey) ?: return null
        val list = json.optJSONArray("list") ?: return WeatherForecast(current, emptyList())
        val hourly = (0 until list.length()).mapNotNull { parseOwmCurrent(list.getJSONObject(it)) }
        return WeatherForecast(current, hourly)
    }

    private fun parseOwmCurrent(json: JSONObject): WeatherCondition? {
        val weather = json.optJSONArray("weather")?.optJSONObject(0) ?: return null
        val main = json.optJSONObject("main") ?: return null
        val wind = json.optJSONObject("wind")
        val rain = json.optJSONObject("rain")
        val snow = json.optJSONObject("snow")
        val clouds = json.optJSONObject("clouds")

        return WeatherCondition(
            conditionCode = weather.optInt("id"),
            conditionGroup = weather.optString("main", "Unknown"),
            description = weather.optString("description", ""),
            temperatureC = main.optDouble("temp", 0.0),
            feelsLikeC = main.optDouble("feels_like", 0.0),
            humidity = main.optInt("humidity", 0),
            windSpeedMps = wind?.optDouble("speed", 0.0) ?: 0.0,
            windDirectionDeg = wind?.optDouble("deg", 0.0) ?: 0.0,
            gustSpeedMps = wind?.optDouble("gust")?.takeIf { !it.isNaN() },
            visibility = json.optInt("visibility", 10_000),
            cloudCoverPercent = clouds?.optInt("all", 0) ?: 0,
            precipitationMmH = rain?.optDouble("1h") ?: snow?.optDouble("1h"),
            timestamp = json.optLong("dt", System.currentTimeMillis() / 1000) * 1000,
        )
    }

    // ── Open-Meteo ──────────────────────────────────────────────────────

    private fun fetchCurrentFromOpenMeteo(lat: Double, lon: Double): WeatherCondition? {
        val url = "$OPEN_METEO_BASE?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,precipitation"
        val json = fetchJson(url) ?: return null
        val current = json.optJSONObject("current") ?: return null
        return parseOpenMeteoCurrent(current)
    }

    private fun fetchForecastFromOpenMeteo(lat: Double, lon: Double, hours: Int): WeatherForecast? {
        val url = "$OPEN_METEO_BASE?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,precipitation" +
            "&hourly=temperature_2m,relative_humidity_2m,weather_code," +
            "wind_speed_10m,wind_direction_10m,precipitation&forecast_hours=$hours"

        val json = fetchJson(url) ?: return null
        val currentJson = json.optJSONObject("current") ?: return null
        val current = parseOpenMeteoCurrent(currentJson) ?: return null

        val hourlyJson = json.optJSONObject("hourly")
        val hourlyList = if (hourlyJson != null) parseOpenMeteoHourly(hourlyJson) else emptyList()
        return WeatherForecast(current, hourlyList)
    }

    private fun parseOpenMeteoCurrent(json: JSONObject): WeatherCondition? {
        val wmoCode = json.optInt("weather_code", 0)
        val group = wmoToGroup(wmoCode)
        val windKmh = json.optDouble("wind_speed_10m", 0.0)

        return WeatherCondition(
            conditionCode = wmoCode,
            conditionGroup = group,
            description = wmoToDescription(wmoCode),
            temperatureC = json.optDouble("temperature_2m", 0.0),
            feelsLikeC = json.optDouble("temperature_2m", 0.0), // Open-Meteo doesn't provide feels-like in free tier
            humidity = json.optInt("relative_humidity_2m", 0),
            windSpeedMps = windKmh / 3.6,
            windDirectionDeg = json.optDouble("wind_direction_10m", 0.0),
            gustSpeedMps = json.optDouble("wind_gusts_10m").takeIf { !it.isNaN() }?.let { it / 3.6 },
            visibility = 10_000, // Not available in Open-Meteo free tier
            cloudCoverPercent = json.optInt("cloud_cover", 0),
            precipitationMmH = json.optDouble("precipitation").takeIf { !it.isNaN() },
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun parseOpenMeteoHourly(json: JSONObject): List<WeatherCondition> {
        val times = json.optJSONArray("time") ?: return emptyList()
        val temps = json.optJSONArray("temperature_2m")
        val humidity = json.optJSONArray("relative_humidity_2m")
        val codes = json.optJSONArray("weather_code")
        val wind = json.optJSONArray("wind_speed_10m")
        val windDir = json.optJSONArray("wind_direction_10m")
        val precip = json.optJSONArray("precipitation")

        return (0 until times.length()).map { i ->
            val wmoCode = codes?.optInt(i, 0) ?: 0
            val windKmh = wind?.optDouble(i, 0.0) ?: 0.0
            WeatherCondition(
                conditionCode = wmoCode,
                conditionGroup = wmoToGroup(wmoCode),
                description = wmoToDescription(wmoCode),
                temperatureC = temps?.optDouble(i, 0.0) ?: 0.0,
                feelsLikeC = temps?.optDouble(i, 0.0) ?: 0.0,
                humidity = humidity?.optInt(i, 0) ?: 0,
                windSpeedMps = windKmh / 3.6,
                windDirectionDeg = windDir?.optDouble(i, 0.0) ?: 0.0,
                gustSpeedMps = null,
                visibility = 10_000,
                cloudCoverPercent = 0,
                precipitationMmH = precip?.optDouble(i)?.takeIf { !it.isNaN() },
                timestamp = System.currentTimeMillis() + i * 3_600_000L,
            )
        }
    }

    // ── WMO code mapping ────────────────────────────────────────────────

    private fun wmoToGroup(code: Int): String = when (code) {
        0 -> "Clear"
        in 1..3 -> "Clouds"
        in 45..48 -> "Fog"
        in 51..55 -> "Drizzle"
        in 56..57 -> "Drizzle" // freezing drizzle
        in 61..65 -> "Rain"
        in 66..67 -> "Rain" // freezing rain
        in 71..77 -> "Snow"
        in 80..82 -> "Rain"
        in 85..86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    private fun wmoToDescription(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45 -> "fog"
        48 -> "depositing rime fog"
        51 -> "light drizzle"
        53 -> "moderate drizzle"
        55 -> "dense drizzle"
        61 -> "slight rain"
        63 -> "moderate rain"
        65 -> "heavy rain"
        71 -> "slight snow"
        73 -> "moderate snow"
        75 -> "heavy snow"
        77 -> "snow grains"
        80 -> "light rain showers"
        81 -> "moderate rain showers"
        82 -> "violent rain showers"
        85 -> "light snow showers"
        86 -> "heavy snow showers"
        95 -> "thunderstorm"
        96 -> "thunderstorm with slight hail"
        99 -> "thunderstorm with heavy hail"
        else -> "unknown"
    }

    // ── HTTP / Cache helpers ────────────────────────────────────────────

    private fun fetchJson(url: String): JSONObject? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            try {
                val response = connection.inputStream.bufferedReader().readText()
                JSONObject(response)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchJson failed: $url", e)
            null
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
        private const val TAG = "WeatherApiClient"
        private const val OWM_BASE = "https://api.openweathermap.org/data/2.5"
        private const val OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast"
    }
}
