package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE isArchived = 0 ORDER BY isActive DESC, updatedAt DESC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE isActive = 1 AND isArchived = 0 LIMIT 1")
    suspend fun getActiveVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE isActive = 1 AND isArchived = 0 LIMIT 1")
    fun observeActiveVehicle(): Flow<VehicleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE vehicles SET isArchived = 1, isActive = 0, updatedAt = :now WHERE id = :vehicleId")
    suspend fun archiveVehicle(vehicleId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE vehicles SET isArchived = 0, updatedAt = :now WHERE id = :vehicleId")
    suspend fun unarchiveVehicle(vehicleId: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC")
    suspend fun getAllVehiclesOnce(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicles WHERE isArchived = 0")
    suspend fun getVehicleCount(): Int

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteVehicle(vehicleId: String)
}
