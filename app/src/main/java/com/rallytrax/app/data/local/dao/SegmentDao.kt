package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.local.entity.SegmentRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    // ── Segment CRUD ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegment(segment: SegmentEntity)

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Query("DELETE FROM segments WHERE id = :segmentId")
    suspend fun deleteSegment(segmentId: String)

    @Query("SELECT * FROM segments WHERE id = :segmentId")
    suspend fun getSegmentById(segmentId: String): SegmentEntity?

    @Query("SELECT * FROM segments ORDER BY isFavorite DESC, name ASC")
    fun getAllSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments ORDER BY isFavorite DESC, name ASC")
    suspend fun getAllSegmentsOnce(): List<SegmentEntity>

    /** Pre-filter: segments whose bounding box overlaps the given bounds. */
    @Query("""
        SELECT * FROM segments
        WHERE boundingBoxSouthLat <= :northLat
          AND boundingBoxNorthLat >= :southLat
          AND boundingBoxWestLon <= :eastLon
          AND boundingBoxEastLon >= :westLon
    """)
    suspend fun getSegmentsOverlappingBounds(
        northLat: Double,
        southLat: Double,
        eastLon: Double,
        westLon: Double,
    ): List<SegmentEntity>

    // ── Segment Runs ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: SegmentRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuns(runs: List<SegmentRunEntity>)

    @Query("SELECT * FROM segment_runs WHERE segmentId = :segmentId ORDER BY timestamp DESC")
    fun getRunsForSegment(segmentId: String): Flow<List<SegmentRunEntity>>

    @Query("SELECT * FROM segment_runs WHERE segmentId = :segmentId ORDER BY timestamp DESC")
    suspend fun getRunsForSegmentOnce(segmentId: String): List<SegmentRunEntity>

    @Query("SELECT * FROM segment_runs WHERE trackId = :trackId")
    suspend fun getRunsForTrack(trackId: String): List<SegmentRunEntity>

    // ── Aggregate Stats ─────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM segment_runs WHERE segmentId = :segmentId")
    suspend fun getRunCount(segmentId: String): Int

    @Query("SELECT MIN(durationMs) FROM segment_runs WHERE segmentId = :segmentId AND durationMs > 0")
    suspend fun getBestTimeMs(segmentId: String): Long?

    @Query("SELECT AVG(durationMs) FROM segment_runs WHERE segmentId = :segmentId AND durationMs > 0")
    suspend fun getAverageTimeMs(segmentId: String): Double?

    // ── Cleanup ─────────────────────────────────────────────────────────

    @Query("DELETE FROM segment_runs WHERE segmentId = :segmentId")
    suspend fun deleteRunsForSegment(segmentId: String)

    @Query("DELETE FROM segment_runs WHERE trackId = :trackId")
    suspend fun deleteRunsForTrack(trackId: String)
}
