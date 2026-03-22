package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.FuelLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLogDao {

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getLogsForVehicle(vehicleId: String): Flow<List<FuelLogEntity>>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY odometerKm ASC")
    suspend fun getLogsForVehicleOnce(vehicleId: String): List<FuelLogEntity>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY odometerKm DESC LIMIT 1")
    suspend fun getLatestLog(vehicleId: String): FuelLogEntity?

    @Query("SELECT * FROM fuel_logs WHERE trackId = :trackId")
    suspend fun getLogsByTrackId(trackId: String): List<FuelLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FuelLogEntity)

    @Update
    suspend fun updateLog(log: FuelLogEntity)

    @Query("DELETE FROM fuel_logs WHERE id = :logId")
    suspend fun deleteLog(logId: String)
}
