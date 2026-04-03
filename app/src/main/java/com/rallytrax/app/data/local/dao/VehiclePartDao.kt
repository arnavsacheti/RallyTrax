package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.VehiclePartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiclePartDao {
    @Query("SELECT * FROM vehicle_parts WHERE vehicleId = :vehicleId AND isActive = 1 ORDER BY category, partName")
    fun getActivePartsForVehicle(vehicleId: String): Flow<List<VehiclePartEntity>>

    @Query("SELECT * FROM vehicle_parts WHERE vehicleId = :vehicleId ORDER BY isActive DESC, category, partName")
    fun getAllPartsForVehicle(vehicleId: String): Flow<List<VehiclePartEntity>>

    @Query("SELECT * FROM vehicle_parts WHERE vehicleId = :vehicleId AND category = :category AND isActive = 1")
    suspend fun getActivePartsByCategory(vehicleId: String, category: String): List<VehiclePartEntity>

    @Query("SELECT * FROM vehicle_parts WHERE id = :partId")
    suspend fun getPartById(partId: String): VehiclePartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: VehiclePartEntity)

    @Update
    suspend fun updatePart(part: VehiclePartEntity)

    @Query("DELETE FROM vehicle_parts WHERE id = :partId")
    suspend fun deletePart(partId: String)

    @Query("UPDATE vehicle_parts SET isActive = 0, updatedAt = :now WHERE id = :partId")
    suspend fun retirePart(partId: String, now: Long = System.currentTimeMillis())
}
