package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val year: Int,
    val make: String,
    val model: String,
    val trim: String? = null,
    val vin: String? = null,
    val photoUri: String? = null,
    val engineDisplacementL: Double? = null,
    val cylinders: Int? = null,
    val horsePower: Int? = null,
    val drivetrain: String? = null,
    val transmissionType: String? = null,
    val transmissionSpeeds: Int? = null,
    val curbWeightKg: Double? = null,
    val fuelType: String = "Gasoline",
    val tankSizeGal: Double? = null,
    val epaCityMpg: Double? = null,
    val epaHwyMpg: Double? = null,
    val epaCombinedMpg: Double? = null,
    val tireSize: String? = null,
    val modsList: String? = null,
    val odometerKm: Double = 0.0,
    val vehicleType: String = "CAR",
    val oilType: String? = null,
    val engineConfiguration: String? = null,
    val wheelDiameter: Double? = null,
    val isActive: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
