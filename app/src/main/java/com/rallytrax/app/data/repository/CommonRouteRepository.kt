package com.rallytrax.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.rallytrax.app.data.local.RallyTraxDatabase
import com.rallytrax.app.data.local.dao.CommonRouteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.CommonRouteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.trips.CommonRouteDetector
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates common route detection: fetches all stints, runs [CommonRouteDetector],
 * and replaces the stored common routes with fresh results.
 */
@Singleton
class CommonRouteRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val commonRouteDao: CommonRouteDao,
    private val commonRouteDetector: CommonRouteDetector,
    private val database: RallyTraxDatabase,
) {
    companion object {
        private const val TAG = "CommonRouteRepo"
    }

    fun getAllCommonRoutes(): Flow<List<CommonRouteEntity>> =
        commonRouteDao.getAllCommonRoutes()

    fun getCommonRoutesWithMinDrives(minDrives: Int): Flow<List<CommonRouteEntity>> =
        commonRouteDao.getCommonRoutesWithMinDrives(minDrives)

    suspend fun getCommonRouteById(id: String): CommonRouteEntity? =
        commonRouteDao.getCommonRouteById(id)

    /**
     * Run common route detection on all stints and replace stored routes.
     * Returns the number of common routes detected.
     */
    suspend fun detectCommonRoutes(): Int {
        val startMs = System.currentTimeMillis()
        try {
            val allStints = trackDao.getStintsOnce()
            if (allStints.size < CommonRouteDetector.MIN_DRIVES) {
                Log.d(TAG, "Only ${allStints.size} stints — skipping common route detection")
                return 0
            }

            // Load start/end points
            val startPoints = mutableMapOf<String, TrackPointEntity>()
            val endPoints = mutableMapOf<String, TrackPointEntity>()
            val sampledPoints = mutableMapOf<String, List<TrackPointEntity>>()

            for (stint in allStints) {
                val points = trackPointDao.getPointsForTrackOnce(stint.id)
                if (points.isNotEmpty()) {
                    startPoints[stint.id] = points.first()
                    endPoints[stint.id] = points.last()

                    // Sample evenly-spaced points for trajectory comparison
                    if (points.size >= CommonRouteDetector.TRAJECTORY_SAMPLES) {
                        val step = points.size / CommonRouteDetector.TRAJECTORY_SAMPLES
                        sampledPoints[stint.id] = (0 until CommonRouteDetector.TRAJECTORY_SAMPLES).map {
                            points[it * step]
                        }
                    }
                }
            }

            val clusters = commonRouteDetector.detect(
                stints = allStints,
                startPoints = startPoints,
                endPoints = endPoints,
                sampledPoints = sampledPoints,
            )

            // Replace all stored common routes with fresh detection. Wrap in
            // a transaction so a crash between deleteAll and insertCommonRoutes
            // doesn't leave the user with an empty common-routes table.
            val entities = clusters.map { cluster ->
                CommonRouteEntity(
                    id = cluster.id,
                    name = cluster.name,
                    stintIds = cluster.stints.joinToString(";") { it.id },
                    representativeTrackId = cluster.representativeTrackId,
                    driveCount = cluster.stints.size,
                    startLat = cluster.startLat,
                    startLon = cluster.startLon,
                    endLat = cluster.endLat,
                    endLon = cluster.endLon,
                    avgDistanceMeters = cluster.avgDistanceMeters,
                    avgDurationMs = cluster.avgDurationMs,
                    bestDurationMs = cluster.bestDurationMs,
                    avgSpeedMps = cluster.avgSpeedMps,
                    lastDrivenAt = cluster.lastDrivenAt,
                )
            }

            database.withTransaction {
                commonRouteDao.deleteAll()
                if (entities.isNotEmpty()) {
                    commonRouteDao.insertCommonRoutes(entities)
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "Common route detection complete: ${entities.size} routes from ${allStints.size} stints (${elapsed}ms)")
            return entities.size
        } catch (e: Exception) {
            Log.e(TAG, "Common route detection failed", e)
            return 0
        }
    }
}
