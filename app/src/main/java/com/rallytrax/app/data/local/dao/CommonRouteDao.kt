package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.CommonRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommonRouteDao {

    @Query("SELECT * FROM common_routes ORDER BY driveCount DESC")
    fun getAllCommonRoutes(): Flow<List<CommonRouteEntity>>

    @Query("SELECT * FROM common_routes ORDER BY driveCount DESC")
    suspend fun getAllCommonRoutesOnce(): List<CommonRouteEntity>

    @Query("SELECT * FROM common_routes WHERE id = :id")
    suspend fun getCommonRouteById(id: String): CommonRouteEntity?

    @Query("SELECT * FROM common_routes WHERE driveCount >= :minDrives ORDER BY driveCount DESC")
    fun getCommonRoutesWithMinDrives(minDrives: Int): Flow<List<CommonRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommonRoute(route: CommonRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommonRoutes(routes: List<CommonRouteEntity>)

    @Update
    suspend fun updateCommonRoute(route: CommonRouteEntity)

    @Query("DELETE FROM common_routes")
    suspend fun deleteAll()

    @Query("DELETE FROM common_routes WHERE id = :id")
    suspend fun deleteCommonRoute(id: String)
}
