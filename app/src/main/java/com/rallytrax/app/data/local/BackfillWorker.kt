package com.rallytrax.app.data.local

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.pacenotes.TrackPointComputer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Backfills accelMps2 and curvatureDegPerM for existing tracks
 * that were recorded before v1.1 (Stage 1.1.3).
 * Enqueued once on first launch after the v2→v3 migration.
 */
@HiltWorker
class BackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val tracks = trackDao.getAllTracksOnce()
            for (track in tracks) {
                val points = trackPointDao.getPointsForTrackOnce(track.id)
                // Skip if already backfilled (first non-edge point has accel data)
                if (points.size >= 3 && points[1].accelMps2 != null) continue

                val enriched = TrackPointComputer.computeFields(points)
                if (enriched.isNotEmpty()) {
                    trackPointDao.insertPoints(enriched)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
