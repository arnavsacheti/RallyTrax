package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.DriverProfileEntity

@Dao
interface DriverProfileDao {

    @Query("SELECT * FROM driver_profile_entries")
    suspend fun getAll(): List<DriverProfileEntity>

    @Query("SELECT * FROM driver_profile_entries WHERE radiusBucketM = :bucket LIMIT 1")
    suspend fun getByRadiusBucket(bucket: Int): DriverProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DriverProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DriverProfileEntity>)

    @Query("DELETE FROM driver_profile_entries")
    suspend fun clearAll()
}
