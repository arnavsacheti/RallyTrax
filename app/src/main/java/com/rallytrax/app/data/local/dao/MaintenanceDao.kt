package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    // ── Records ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM maintenance_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getRecordsForVehicle(vehicleId: String): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    suspend fun getRecordsForVehicleOnce(vehicleId: String): List<MaintenanceRecordEntity>

    @Query("SELECT * FROM maintenance_records WHERE vehicleId = :vehicleId AND category = :category ORDER BY date DESC")
    suspend fun getRecordsByCategory(vehicleId: String, category: String): List<MaintenanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MaintenanceRecordEntity)

    @Update
    suspend fun updateRecord(record: MaintenanceRecordEntity)

    @Query("DELETE FROM maintenance_records WHERE id = :recordId")
    suspend fun deleteRecord(recordId: String)

    // ── Schedules ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :vehicleId ORDER BY nextDueDate ASC")
    fun getSchedulesForVehicle(vehicleId: String): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :vehicleId AND status != 'COMPLETED' ORDER BY nextDueDate ASC")
    fun getActiveSchedules(vehicleId: String): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules WHERE status IN ('DUE_SOON', 'OVERDUE')")
    suspend fun getDueSchedules(): List<MaintenanceScheduleEntity>

    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :vehicleId AND status IN ('DUE_SOON', 'OVERDUE')")
    suspend fun getDueSchedulesForVehicle(vehicleId: String): List<MaintenanceScheduleEntity>

    @Query("SELECT * FROM maintenance_schedules WHERE status != 'COMPLETED'")
    suspend fun getAllActiveSchedules(): List<MaintenanceScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: MaintenanceScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: MaintenanceScheduleEntity)

    @Query("DELETE FROM maintenance_schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)
}
