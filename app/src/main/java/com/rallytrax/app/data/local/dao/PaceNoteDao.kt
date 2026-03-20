package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaceNoteDao {

    @Query("SELECT * FROM pace_notes WHERE trackId = :trackId ORDER BY distanceFromStart ASC")
    fun getNotesForTrack(trackId: String): Flow<List<PaceNoteEntity>>

    @Query("SELECT * FROM pace_notes WHERE trackId = :trackId ORDER BY distanceFromStart ASC")
    suspend fun getNotesForTrackOnce(trackId: String): List<PaceNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<PaceNoteEntity>)

    @Query("DELETE FROM pace_notes WHERE trackId = :trackId")
    suspend fun deleteNotesForTrack(trackId: String)

    @Query("SELECT COUNT(*) FROM pace_notes WHERE trackId = :trackId")
    suspend fun getNoteCount(trackId: String): Int
}
