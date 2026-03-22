package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "maintenance_schedules")
data class MaintenanceScheduleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val serviceType: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val lastServiceDate: Long? = null,
    val lastServiceOdometerKm: Double? = null,
    val nextDueDate: Long? = null,
    val nextDueOdometerKm: Double? = null,
    val status: String = STATUS_UPCOMING,
    val notifyDaysBefore: Int = 30,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_UPCOMING = "UPCOMING"
        const val STATUS_DUE_SOON = "DUE_SOON"
        const val STATUS_OVERDUE = "OVERDUE"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
