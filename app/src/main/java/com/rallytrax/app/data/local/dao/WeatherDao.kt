package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.WeatherEntity

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_records WHERE trackId = :trackId ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getWeatherForTrack(trackId: String): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weather: WeatherEntity)
}
