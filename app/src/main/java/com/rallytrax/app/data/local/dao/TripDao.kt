package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY updatedAt DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun getTripById(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM tracks WHERE tripId = :tripId ORDER BY recordedAt ASC")
    fun getTracksForTrip(tripId: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE tripId = :tripId")
    fun getTrackCountForTrip(tripId: String): Flow<Int>

    @Query("SELECT SUM(distanceMeters) FROM tracks WHERE tripId = :tripId")
    fun getTotalDistanceForTrip(tripId: String): Flow<Double?>

    @Query("SELECT SUM(durationMs) FROM tracks WHERE tripId = :tripId")
    fun getTotalDurationForTrip(tripId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM tracks WHERE tripId = :tripId")
    suspend fun getTrackCountForTripOnce(tripId: String): Int

    @Query("SELECT COALESCE(SUM(distanceMeters), 0.0) FROM tracks WHERE tripId = :tripId")
    suspend fun getTotalDistanceForTripOnce(tripId: String): Double

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM tracks WHERE tripId = :tripId")
    suspend fun getTotalDurationForTripOnce(tripId: String): Long

    @Query("SELECT MIN(recordedAt) FROM tracks WHERE tripId = :tripId")
    suspend fun getFirstRecordedAtForTripOnce(tripId: String): Long?

    @Query("SELECT MAX(recordedAt) FROM tracks WHERE tripId = :tripId")
    suspend fun getLastRecordedAtForTripOnce(tripId: String): Long?

    @Query(
        "SELECT COUNT(DISTINCT DATE(recordedAt / 1000, 'unixepoch', 'localtime')) " +
            "FROM tracks WHERE tripId = :tripId",
    )
    suspend fun getDayCountForTripOnce(tripId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    /**
     * Atomically bump only `updatedAt`. Used after assigning a track to a
     * trip so we don't have to round-trip the whole row through a Flow read,
     * which would race with concurrent edits.
     */
    @Query("UPDATE trips SET updatedAt = :updatedAt WHERE id = :tripId")
    suspend fun touchTrip(tripId: String, updatedAt: Long)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)
}
