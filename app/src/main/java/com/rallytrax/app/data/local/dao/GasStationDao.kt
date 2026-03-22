package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.GasStationEntity

@Dao
interface GasStationDao {

    @Query(
        """SELECT * FROM gas_stations
           WHERE lat BETWEEN :minLat AND :maxLat
           AND lon BETWEEN :minLon AND :maxLon"""
    )
    suspend fun getStationsNear(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<GasStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<GasStationEntity>)

    @Query("DELETE FROM gas_stations WHERE fetchedAt < :olderThan")
    suspend fun deleteOldStations(olderThan: Long)

    @Query("SELECT COUNT(*) FROM gas_stations")
    suspend fun getStationCount(): Int
}
