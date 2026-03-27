package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "fuel_logs",
    indices = [Index("vehicleId")],
)
data class FuelLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val trackId: String? = null,
    val date: Long = System.currentTimeMillis(),
    val odometerKm: Double,
    val volumeL: Double,
    val isFullTank: Boolean = true,
    val pricePerUnit: Double? = null,
    val totalCost: Double? = null,
    val fuelGrade: String? = null,
    val stationName: String? = null,
    val stationLat: Double? = null,
    val stationLon: Double? = null,
    val computedMpg: Double? = null,
    val isMissed: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
