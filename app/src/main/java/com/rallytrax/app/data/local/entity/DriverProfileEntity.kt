package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "driver_profile_entries")
data class DriverProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val radiusBucketM: Int, // rounded radius bucket (e.g., 10, 20, 30...)
    val avgSpeedMps: Double, // driver's running average speed through this radius
    val sampleCount: Int, // number of corners contributing to this average
    val lastUpdated: Long, // epoch millis
)
