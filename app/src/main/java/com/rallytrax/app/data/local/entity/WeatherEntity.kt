package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rallytrax.app.data.api.WeatherCondition
import java.util.UUID

@Entity(
    tableName = "weather_records",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class WeatherEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val fetchedAt: Long,
    val conditionCode: Int,
    val conditionGroup: String,
    val description: String,
    val temperatureC: Double,
    val humidity: Int,
    val windSpeedMps: Double,
    val windDirectionDeg: Double,
    val visibility: Int,
    val precipitationMmH: Double?,
) {
    fun toWeatherCondition(): WeatherCondition = WeatherCondition(
        conditionCode = conditionCode,
        conditionGroup = conditionGroup,
        description = description,
        temperatureC = temperatureC,
        feelsLikeC = temperatureC,
        humidity = humidity,
        windSpeedMps = windSpeedMps,
        windDirectionDeg = windDirectionDeg,
        gustSpeedMps = null,
        visibility = visibility,
        cloudCoverPercent = 0,
        precipitationMmH = precipitationMmH,
        timestamp = fetchedAt,
    )

    /** Weather conditions that are likely to affect driving performance. */
    val hasPerformanceImpact: Boolean
        get() = conditionGroup in IMPACT_CONDITION_GROUPS || temperatureC < 3.0

    companion object {
        private val IMPACT_CONDITION_GROUPS = setOf("Rain", "Drizzle", "Thunderstorm", "Snow")
    }
}
