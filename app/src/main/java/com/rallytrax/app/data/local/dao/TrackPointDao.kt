package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    fun getPointsForTrack(trackId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY `index` ASC")
    suspend fun getPointsForTrackOnce(trackId: String): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsForTrack(trackId: String)
}
