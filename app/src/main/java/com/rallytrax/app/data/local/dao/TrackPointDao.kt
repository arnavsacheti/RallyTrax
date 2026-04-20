package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

data class LatLonProjection(val lat: Double, val lon: Double)

data class LatLonSpeedProjection(val lat: Double, val lon: Double, val speed: Double?)

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    fun getPointsForTrack(trackId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    suspend fun getPointsForTrackOnce(trackId: String): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    suspend fun getPointCountForTrack(trackId: String): Int

    @Query("SELECT lat, lon FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    suspend fun getLatLonForTrack(trackId: String): List<LatLonProjection>

    @Query("SELECT lat, lon, speed FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    suspend fun getLatLonSpeedForTrack(trackId: String): List<LatLonSpeedProjection>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId AND `index` BETWEEN :startIndex AND :endIndex ORDER BY `index` ASC")
    suspend fun getPointsInRange(trackId: String, startIndex: Int, endIndex: Int): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsForTrack(trackId: String)
}
