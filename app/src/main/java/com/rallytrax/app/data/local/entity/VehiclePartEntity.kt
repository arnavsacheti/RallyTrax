package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vehicle_parts", indices = [Index("vehicleId")])
data class VehiclePartEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val category: String,
    val partName: String,
    val brand: String? = null,
    val installDate: Long = System.currentTimeMillis(),
    val installOdometerKm: Double = 0.0,
    val lifeExpectancyKm: Double? = null,
    val lifeExpectancyMonths: Int? = null,
    val costAmount: Double? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
