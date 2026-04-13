package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rallytrax.app.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY recordedAt DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY recordedAt DESC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    suspend fun deleteTracks(trackIds: List<String>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    // --- Route / Stint category queries ---

    @Query("SELECT * FROM tracks WHERE trackCategory = 'route' ORDER BY recordedAt DESC")
    fun getRoutes(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE trackCategory = 'stint' ORDER BY recordedAt DESC")
    fun getStints(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE trackCategory = 'stint' ORDER BY recordedAt DESC")
    suspend fun getStintsOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE trackCategory = 'route' AND name LIKE '%' || :query || '%' ORDER BY recordedAt DESC")
    fun searchRoutes(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE trackCategory = 'stint' AND name LIKE '%' || :query || '%' ORDER BY recordedAt DESC")
    fun searchStints(query: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE trackCategory = 'stint'")
    fun observeStintCount(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE name LIKE '%' || :query || '%' ORDER BY recordedAt DESC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE vehicleId = :vehicleId ORDER BY recordedAt DESC")
    fun getTracksByVehicleId(vehicleId: String): Flow<List<TrackEntity>>

    @Query("SELECT COALESCE(SUM(distanceMeters), 0.0) FROM tracks WHERE vehicleId = :vehicleId")
    suspend fun getTotalDistanceForVehicle(vehicleId: String): Double

    @Query("SELECT COUNT(*) FROM tracks WHERE vehicleId = :vehicleId")
    suspend fun getTrackCountForVehicle(vehicleId: String): Int

    // --- Aggregation queries for four-phase activity lifecycle ---

    @Query("SELECT * FROM tracks WHERE name = :routeName ORDER BY recordedAt DESC")
    suspend fun getTracksForRoute(routeName: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE recordedAt BETWEEN :startMs AND :endMs ORDER BY recordedAt DESC")
    suspend fun getTracksBetween(startMs: Long, endMs: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE recordedAt BETWEEN :startMs AND :endMs ORDER BY recordedAt ASC")
    fun observeTracksBetween(startMs: Long, endMs: Long): Flow<List<TrackEntity>>

    @Query("SELECT MIN(durationMs) FROM tracks WHERE name = :routeName AND durationMs > 0")
    suspend fun getPersonalBestForRoute(routeName: String): Long?

    @Query("SELECT COALESCE(SUM(distanceMeters), 0.0) FROM tracks")
    suspend fun getTotalDistanceAllTime(): Double

    @Query("SELECT COUNT(DISTINCT date(recordedAt / 1000, 'unixepoch', 'localtime')) FROM tracks WHERE recordedAt >= :sinceMs")
    suspend fun getActiveDaysSince(sinceMs: Long): Int

    /** Find tracks whose bounding box overlaps the given bounds (for segment detection). */
    @Query("""
        SELECT * FROM tracks
        WHERE id != :excludeTrackId
          AND boundingBoxSouthLat <= :northLat
          AND boundingBoxNorthLat >= :southLat
          AND boundingBoxWestLon <= :eastLon
          AND boundingBoxEastLon >= :westLon
    """)
    suspend fun getTracksOverlappingBounds(
        excludeTrackId: String,
        northLat: Double,
        southLat: Double,
        eastLon: Double,
        westLon: Double,
    ): List<TrackEntity>

    // --- Cross-sensor analytics ---

    @Query("""
        UPDATE tracks SET
            peakCorneringG = :peakCorneringG,
            avgCorneringG = :avgCorneringG,
            smoothnessScore = :smoothnessScore,
            roadRoughnessIndex = :roadRoughnessIndex,
            brakingEfficiencyScore = :brakingEfficiencyScore,
            elevationAdjustedAvgSpeedMps = :elevationAdjustedAvgSpeedMps
        WHERE id = :trackId
    """)
    suspend fun updateInsights(
        trackId: String,
        peakCorneringG: Double?,
        avgCorneringG: Double?,
        smoothnessScore: Int?,
        roadRoughnessIndex: Double?,
        brakingEfficiencyScore: Int?,
        elevationAdjustedAvgSpeedMps: Double?,
    )

    @Query("SELECT AVG(smoothnessScore) FROM tracks WHERE smoothnessScore IS NOT NULL")
    suspend fun getAvgSmoothnessScore(): Double?

    @Query("SELECT MAX(peakCorneringG) FROM tracks WHERE peakCorneringG IS NOT NULL")
    suspend fun getLifetimePeakCorneringG(): Double?

    @Query("SELECT AVG(brakingEfficiencyScore) FROM tracks WHERE brakingEfficiencyScore IS NOT NULL")
    suspend fun getAvgBrakingEfficiency(): Double?

    // --- Grip event detection ---

    @Query("UPDATE tracks SET gripEventCount = :count, gripEventSummary = :summary WHERE id = :trackId")
    suspend fun updateGripEvents(trackId: String, count: Int, summary: String?)

    // --- Trip assignment ---

    @Query("UPDATE tracks SET tripId = :tripId WHERE id = :trackId")
    suspend fun updateTripId(trackId: String, tripId: String?)

    @Query("UPDATE tracks SET tripId = NULL WHERE tripId = :tripId")
    suspend fun clearTripAssignments(tripId: String)
}
