package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.WeatherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_records WHERE trackId = :trackId ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getWeatherForTrack(trackId: String): WeatherEntity?

    @Query("SELECT * FROM weather_records ORDER BY fetchedAt DESC")
    fun getAllWeather(): Flow<List<WeatherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weather: WeatherEntity)
}
