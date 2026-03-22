package com.rallytrax.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class FuelEconomyTrim(val vehicleId: Int, val displayName: String)

data class FuelEconomyData(
    val cityMpg: Double? = null,
    val hwyMpg: Double? = null,
    val combinedMpg: Double? = null,
    val fuelType: String? = null,
    val cylinders: Int? = null,
    val displacement: Double? = null,
    val drive: String? = null,
    val transmission: String? = null,
)

@Singleton
class FuelEconomyApiClient @Inject constructor() {

    /**
     * Get available trims/options for a given year/make/model.
     */
    suspend fun getTrims(year: Int, make: String, model: String): List<FuelEconomyTrim> =
        withContext(Dispatchers.IO) {
            try {
                val encodedMake = java.net.URLEncoder.encode(make, "UTF-8")
                val encodedModel = java.net.URLEncoder.encode(model, "UTF-8")
                val url = "$BASE_URL/vehicle/menu/options?year=$year&make=$encodedMake&model=$encodedModel"
                val xml = fetchXml(url)
                parseMenuItems(xml)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch trims: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * Get detailed vehicle data (EPA MPG, etc.) for a specific FuelEconomy.gov vehicle ID.
     */
    suspend fun getVehicleData(vehicleId: Int): FuelEconomyData? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/vehicle/$vehicleId"
                val xml = fetchXml(url)
                parseVehicleData(xml)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch vehicle data: ${e.message}", e)
                null
            }
        }

    private fun parseMenuItems(xml: String): List<FuelEconomyTrim> {
        val trims = mutableListOf<FuelEconomyTrim>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var currentText: String? = null
        var currentValue: Int? = null
        var inMenuItem = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "menuItem") inMenuItem = true
                }
                XmlPullParser.TEXT -> {
                    currentText = parser.text?.trim()
                }
                XmlPullParser.END_TAG -> {
                    if (inMenuItem) {
                        when (parser.name) {
                            "value" -> currentValue = currentText?.toIntOrNull()
                            "text" -> {
                                val text = currentText ?: ""
                                if (currentValue != null && text.isNotBlank()) {
                                    trims.add(FuelEconomyTrim(currentValue!!, text))
                                }
                            }
                            "menuItem" -> {
                                inMenuItem = false
                                currentValue = null
                            }
                        }
                    }
                }
            }
            parser.next()
        }
        return trims
    }

    private fun parseVehicleData(xml: String): FuelEconomyData {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var currentText: String? = null
        var cityMpg: Double? = null
        var hwyMpg: Double? = null
        var combinedMpg: Double? = null
        var fuelType: String? = null
        var cylinders: Int? = null
        var displacement: Double? = null
        var drive: String? = null
        var transmission: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> currentText = parser.text?.trim()
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "city08" -> cityMpg = currentText?.toDoubleOrNull()
                        "highway08" -> hwyMpg = currentText?.toDoubleOrNull()
                        "comb08" -> combinedMpg = currentText?.toDoubleOrNull()
                        "fuelType" -> fuelType = currentText?.takeIf { it.isNotBlank() }
                        "cylinders" -> cylinders = currentText?.toIntOrNull()
                        "displ" -> displacement = currentText?.toDoubleOrNull()
                        "drive" -> drive = currentText?.takeIf { it.isNotBlank() }
                        "trany" -> transmission = currentText?.takeIf { it.isNotBlank() }
                    }
                }
            }
            parser.next()
        }

        return FuelEconomyData(
            cityMpg = cityMpg,
            hwyMpg = hwyMpg,
            combinedMpg = combinedMpg,
            fuelType = fuelType,
            cylinders = cylinders,
            displacement = displacement,
            drive = drive,
            transmission = transmission,
        )
    }

    private fun fetchXml(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/xml")
        return try {
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "FuelEconomyApiClient"
        private const val BASE_URL = "https://www.fueleconomy.gov/ws/rest"
    }
}
