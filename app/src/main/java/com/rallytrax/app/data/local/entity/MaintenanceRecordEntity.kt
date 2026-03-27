package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "maintenance_records",
    indices = [Index("vehicleId")],
)
data class MaintenanceRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val category: String,
    val serviceType: String,
    val date: Long = System.currentTimeMillis(),
    val odometerKm: Double? = null,
    val costParts: Double? = null,
    val costLabor: Double? = null,
    val costTotal: Double = 0.0,
    val provider: String? = null,
    val isDiy: Boolean = false,
    val notes: String? = null,
    val receiptPhotoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
