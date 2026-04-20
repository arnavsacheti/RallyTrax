package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.TripSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSuggestionDao {

    @Query("SELECT * FROM trip_suggestions WHERE status = 'pending' ORDER BY startTimestamp DESC")
    fun getPendingSuggestions(): Flow<List<TripSuggestionEntity>>

    @Query("SELECT * FROM trip_suggestions WHERE status = 'pending' ORDER BY startTimestamp DESC")
    suspend fun getPendingSuggestionsOnce(): List<TripSuggestionEntity>

    @Query("SELECT * FROM trip_suggestions ORDER BY updatedAt DESC")
    fun getAllSuggestions(): Flow<List<TripSuggestionEntity>>

    @Query("SELECT * FROM trip_suggestions WHERE id = :id")
    suspend fun getSuggestionById(id: String): TripSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: TripSuggestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestions(suggestions: List<TripSuggestionEntity>)

    @Update
    suspend fun updateSuggestion(suggestion: TripSuggestionEntity)

    @Query("UPDATE trip_suggestions SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip_suggestions SET aiGeneratedName = :name, updatedAt = :now WHERE id = :id")
    suspend fun updateAiName(id: String, name: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM trip_suggestions WHERE id = :id")
    suspend fun deleteSuggestion(id: String)

    @Query("DELETE FROM trip_suggestions WHERE status = 'dismissed'")
    suspend fun clearDismissed()

    /** Check if any pending suggestion already contains a specific stint ID. */
    @Query("SELECT COUNT(*) FROM trip_suggestions WHERE status = 'pending' AND stintIds LIKE '%' || :stintId || '%'")
    suspend fun countPendingSuggestionsContainingStint(stintId: String): Int
}
