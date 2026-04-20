package com.rallytrax.app.data.trips

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rallytrax.app.data.repository.CommonRouteRepository
import com.rallytrax.app.data.repository.TripSuggestionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs trip detection in the background.
 * Scheduled periodically (every 6 hours) and triggered after recording completes.
 */
@HiltWorker
class TripDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tripSuggestionRepository: TripSuggestionRepository,
    private val commonRouteRepository: CommonRouteRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TripDetectionWorker"
        const val UNIQUE_PERIODIC_WORK = "trip_detection_periodic"
        const val UNIQUE_ONE_SHOT_WORK = "trip_detection_one_shot"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting trip detection run")
        return try {
            val newSuggestions = tripSuggestionRepository.detectAndSuggest()
            val commonRoutes = commonRouteRepository.detectCommonRoutes()
            Log.d(TAG, "Detection complete: $newSuggestions new trip suggestions, $commonRoutes common routes")

            // Async AI enrichment (best-effort, non-blocking on failure)
            if (newSuggestions > 0) {
                tripSuggestionRepository.enrichPendingSuggestionsWithAi()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Trip detection failed", e)
            Result.retry()
        }
    }
}
