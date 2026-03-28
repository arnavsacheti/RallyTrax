package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
)
