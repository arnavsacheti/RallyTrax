package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gas_stations")
data class GasStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val brand: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)
