package com.rallytrax.app.data.repository

import android.util.Log
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.TripSuggestionDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TripSuggestionEntity
import com.rallytrax.app.data.trips.TripDetector
import com.rallytrax.app.data.trips.TripNamingService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates trip detection: fetches unassigned stints, runs [TripDetector],
 * deduplicates against existing suggestions, and persists new ones.
 *
 * Also handles accept (creates Trip + assigns stints) and dismiss (marks dismissed).
 */
@Singleton
class TripSuggestionRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val tripSuggestionDao: TripSuggestionDao,
    private val tripRepository: TripRepository,
    private val tripDetector: TripDetector,
    private val tripNamingService: TripNamingService,
) {
    companion object {
        private const val TAG = "TripSuggestionRepo"
    }

    fun getPendingSuggestions(): Flow<List<TripSuggestionEntity>> =
        tripSuggestionDao.getPendingSuggestions()

    fun getAllSuggestions(): Flow<List<TripSuggestionEntity>> =
        tripSuggestionDao.getAllSuggestions()

    /**
     * Run trip detection on all unassigned stints and persist any new suggestions.
     * Returns the number of new suggestions created.
     */
    suspend fun detectAndSuggest(): Int {
        val startMs = System.currentTimeMillis()
        try {
            // Get all stints not currently assigned to a trip
            val allStints = trackDao.getStintsOnce()
            val unassigned = allStints.filter { it.tripId == null }

            if (unassigned.size < TripDetector.MIN_STINTS_PER_TRIP) {
                Log.d(TAG, "Only ${unassigned.size} unassigned stints — skipping detection")
                return 0
            }

            // Load start/end points for spatial proximity check
            val startPoints = mutableMapOf<String, com.rallytrax.app.data.local.entity.TrackPointEntity>()
            val endPoints = mutableMapOf<String, com.rallytrax.app.data.local.entity.TrackPointEntity>()

            for (stint in unassigned) {
                val points = trackPointDao.getPointsForTrackOnce(stint.id)
                if (points.isNotEmpty()) {
                    startPoints[stint.id] = points.first()
                    endPoints[stint.id] = points.last()
                }
            }

            // Run detection
            val candidates = tripDetector.detect(
                stints = unassigned,
                startPoints = startPoints,
                endPoints = endPoints,
            )

            if (candidates.isEmpty()) {
                Log.d(TAG, "No trip candidates found from ${unassigned.size} stints")
                return 0
            }

            // Deduplicate: skip candidates where all stints are already in a pending suggestion
            val existingPending = tripSuggestionDao.getPendingSuggestionsOnce()
            val existingStintSets = existingPending.map { it.stintIds.split(";").toSet() }

            var newCount = 0
            for (candidate in candidates) {
                val candidateIds = candidate.stints.map { it.id }.toSet()

                // Check if this exact set (or superset) already exists
                val alreadySuggested = existingStintSets.any { existing ->
                    candidateIds.all { it in existing }
                }

                if (!alreadySuggested) {
                    val suggestion = TripSuggestionEntity(
                        stintIds = candidate.stints.joinToString(";") { it.id },
                        suggestedName = candidate.suggestedName,
                        status = "pending",
                        confidenceScore = candidate.confidenceScore,
                        totalDistanceMeters = candidate.totalDistanceMeters,
                        totalDurationMs = candidate.totalDurationMs,
                        stintCount = candidate.stints.size,
                        startTimestamp = candidate.startTimestamp,
                        endTimestamp = candidate.endTimestamp,
                    )
                    tripSuggestionDao.insertSuggestion(suggestion)
                    newCount++
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "Detection complete: $newCount new suggestions from ${candidates.size} candidates (${elapsed}ms)")
            return newCount
        } catch (e: Exception) {
            Log.e(TAG, "Trip detection failed", e)
            return 0
        }
    }

    /**
     * Accept a trip suggestion: create a Trip entity and assign all stints to it.
     *
     * @param suggestionId The suggestion to accept.
     * @param tripName Optional override name (uses suggestion name if null).
     */
    suspend fun acceptSuggestion(suggestionId: String, tripName: String? = null) {
        val suggestion = tripSuggestionDao.getSuggestionById(suggestionId) ?: return

        // Use AI name if available, else the provided override, else the local suggestion name
        val name = tripName
            ?: suggestion.aiGeneratedName
            ?: suggestion.suggestedName

        // Create the trip
        val tripId = tripRepository.createTrip(name = name)

        // Assign all stints to the trip
        val stintIds = suggestion.stintIds.split(";").filter { it.isNotBlank() }
        for (stintId in stintIds) {
            tripRepository.assignTrackToTrip(stintId, tripId)
        }

        // Mark suggestion as accepted
        tripSuggestionDao.updateStatus(suggestionId, "accepted")
        Log.d(TAG, "Accepted suggestion $suggestionId → trip $tripId with ${stintIds.size} stints")
    }

    /**
     * Dismiss a trip suggestion so it won't be shown again.
     */
    suspend fun dismissSuggestion(suggestionId: String) {
        tripSuggestionDao.updateStatus(suggestionId, "dismissed")
        Log.d(TAG, "Dismissed suggestion $suggestionId")
    }

    /**
     * Enrich pending suggestions with AI-generated names.
     * Called after detection or on-demand. Non-blocking — failures are logged and skipped.
     */
    suspend fun enrichPendingSuggestionsWithAi() {
        try {
            val pending = tripSuggestionDao.getPendingSuggestionsOnce()
                .filter { it.aiGeneratedName == null }

            for (suggestion in pending) {
                val stintIds = suggestion.stintIds.split(";").filter { it.isNotBlank() }
                val stints = stintIds.mapNotNull { trackDao.getTrackById(it) }
                if (stints.isEmpty()) continue

                val aiName = tripNamingService.generateTripName(stints)
                if (aiName != suggestion.suggestedName) {
                    tripSuggestionDao.updateAiName(suggestion.id, aiName)
                    Log.d(TAG, "AI enriched suggestion ${suggestion.id}: $aiName")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI enrichment failed (non-fatal)", e)
        }
    }

    /**
     * Get stint details for a suggestion (for UI display).
     */
    suspend fun getStintsForSuggestion(suggestionId: String): List<TrackEntity> {
        val suggestion = tripSuggestionDao.getSuggestionById(suggestionId) ?: return emptyList()
        val stintIds = suggestion.stintIds.split(";").filter { it.isNotBlank() }
        return stintIds.mapNotNull { trackDao.getTrackById(it) }
    }
}
