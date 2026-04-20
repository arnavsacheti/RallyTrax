package com.rallytrax.app.data.trips

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.rallytrax.app.data.local.entity.TrackEntity
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses Firebase AI Logic (Gemini) to generate contextual trip names and route descriptions.
 * Falls back to local heuristics when AI is unavailable or times out.
 */
@Singleton
class TripNamingService @Inject constructor() {

    companion object {
        private const val TAG = "TripNamingService"
        private const val AI_TIMEOUT_MS = 10_000L
        private const val MODEL_NAME = "gemini-2.0-flash"
    }

    private val generativeModel by lazy {
        try {
            Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = generationConfig {
                        temperature = 0.7f
                        maxOutputTokens = 100
                    },
                )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Firebase AI model", e)
            null
        }
    }

    /**
     * Generate a contextual name for a trip suggestion.
     *
     * @param stints The stints in the suggested trip, in chronological order.
     * @return A descriptive trip name, or null if both AI and local fallback fail.
     */
    suspend fun generateTripName(stints: List<TrackEntity>): String {
        // Try AI first
        val aiName = tryGenerateAiTripName(stints)
        if (aiName != null) {
            Log.d(TAG, "AI generated trip name: $aiName")
            return aiName
        }

        // Fallback to local heuristic
        return generateLocalTripName(stints)
    }

    /**
     * Generate a description for a commonly driven route.
     *
     * @param stints Sample stints that match this route.
     * @param avgDistanceM Average distance in meters.
     * @param driveCount Number of times driven.
     * @return A route description, or null if AI is unavailable.
     */
    suspend fun generateRouteDescription(
        stints: List<TrackEntity>,
        avgDistanceM: Double,
        driveCount: Int,
    ): String? {
        val model = generativeModel ?: return null

        val prompt = buildRouteDescriptionPrompt(stints, avgDistanceM, driveCount)

        return try {
            val result = withTimeoutOrNull(AI_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            result?.text?.trim()?.removeSurrounding("\"")
        } catch (e: Exception) {
            Log.w(TAG, "AI route description failed", e)
            null
        }
    }

    private suspend fun tryGenerateAiTripName(stints: List<TrackEntity>): String? {
        val model = generativeModel ?: return null

        val prompt = buildTripNamePrompt(stints)

        return try {
            val result = withTimeoutOrNull(AI_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            val text = result?.text?.trim()?.removeSurrounding("\"")
            // Validate: should be short (< 60 chars), not empty, no weird formatting
            if (text != null && text.length in 3..60 && !text.contains("\n")) {
                text
            } else {
                Log.w(TAG, "AI returned invalid trip name: $text")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI trip naming failed", e)
            null
        }
    }

    private fun buildTripNamePrompt(stints: List<TrackEntity>): String {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        val stintSummaries = stints.joinToString("\n") { stint ->
            val date = dateFormat.format(Date(stint.recordedAt))
            val distKm = String.format(Locale.US, "%.1f", stint.distanceMeters / 1000.0)
            val durMin = stint.durationMs / 60000
            val type = stint.routeType ?: "Unknown"
            "- $date: ${stint.name}, ${distKm}km, ${durMin}min, type: $type"
        }

        val totalDistKm = String.format(Locale.US, "%.1f", stints.sumOf { it.distanceMeters } / 1000.0)
        val totalDurMin = stints.sumOf { it.durationMs } / 60000

        return """
            |You are naming a driving trip for a rally/driving enthusiast app.
            |Generate a short, descriptive trip name (3-8 words) based on these driving stints:
            |
            |$stintSummaries
            |
            |Total: $totalDistKm km, $totalDurMin min, ${stints.size} stints
            |
            |Guidelines:
            |- Use natural language, like "Saturday Canyon Run" or "Morning Commute Loop"
            |- Reference time of day, day of week, or driving character when relevant
            |- Keep it under 40 characters
            |- Return ONLY the name, no quotes, no explanation
        """.trimMargin()
    }

    private fun buildRouteDescriptionPrompt(
        stints: List<TrackEntity>,
        avgDistanceM: Double,
        driveCount: Int,
    ): String {
        val sample = stints.firstOrNull()
        val distKm = String.format(Locale.US, "%.1f", avgDistanceM / 1000.0)
        val routeType = sample?.routeType ?: "Unknown"
        val difficulty = sample?.difficultyRating ?: "Unknown"
        val surface = sample?.primarySurface ?: "Unknown"

        return """
            |Describe a commonly driven route for a driving/rally app in 1-2 sentences.
            |
            |Route stats:
            |- Average distance: ${distKm} km
            |- Driven $driveCount times
            |- Route type: $routeType
            |- Difficulty: $difficulty
            |- Primary surface: $surface
            |
            |Guidelines:
            |- Be concise and descriptive
            |- Mention the character of the drive (twisty, scenic, commute, etc.)
            |- Return ONLY the description, no formatting
        """.trimMargin()
    }

    /**
     * Local fallback: generate a trip name without AI.
     */
    private fun generateLocalTripName(stints: List<TrackEntity>): String {
        if (stints.isEmpty()) return "Trip"

        val startTime = stints.minOf { it.recordedAt }
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val dayName = dayFormat.format(Date(startTime))

        val hourFormat = SimpleDateFormat("H", Locale.getDefault())
        val hour = hourFormat.format(Date(startTime)).toIntOrNull() ?: 12

        val period = when {
            hour < 6 -> "Early Morning"
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            hour < 21 -> "Evening"
            else -> "Night"
        }

        val totalDistKm = stints.sumOf { it.distanceMeters } / 1000.0
        val suffix = when {
            totalDistKm > 100 -> "Road Trip"
            stints.size > 3 -> "Drive"
            stints.any { it.routeType?.contains("Canyon") == true } -> "Canyon Run"
            stints.any { it.routeType?.contains("Mountain") == true } -> "Mountain Drive"
            else -> "Drive"
        }

        return "$dayName $period $suffix"
    }
}
