package com.rallytrax.app.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Represents the subset of user preferences that are synced to Google Drive.
 * Each field includes a modification timestamp for last-write-wins conflict resolution.
 */
@Serializable
data class SyncableSettings(
    val themeMode: String = "SYSTEM",
    val themeModeModifiedAt: Long = 0L,
    val unitSystem: String = "METRIC",
    val unitSystemModifiedAt: Long = 0L,
    val gpsAccuracy: String = "HIGH",
    val gpsAccuracyModifiedAt: Long = 0L,
    val mapProvider: String = "AUTO",
    val mapProviderModifiedAt: Long = 0L,
    val ttsRate: Float = 1.25f,
    val ttsRateModifiedAt: Long = 0L,
    val ttsPitch: Float = 1.15f,
    val ttsPitchModifiedAt: Long = 0L,
    val ttsEnabled: Boolean = true,
    val ttsEnabledModifiedAt: Long = 0L,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    /**
     * Merge this (local) with remote settings using last-write-wins per field.
     * Returns a new SyncableSettings with the most recent value for each field.
     */
    fun mergeWith(remote: SyncableSettings): SyncableSettings {
        return SyncableSettings(
            themeMode = if (remote.themeModeModifiedAt > themeModeModifiedAt) remote.themeMode else themeMode,
            themeModeModifiedAt = maxOf(themeModeModifiedAt, remote.themeModeModifiedAt),
            unitSystem = if (remote.unitSystemModifiedAt > unitSystemModifiedAt) remote.unitSystem else unitSystem,
            unitSystemModifiedAt = maxOf(unitSystemModifiedAt, remote.unitSystemModifiedAt),
            gpsAccuracy = if (remote.gpsAccuracyModifiedAt > gpsAccuracyModifiedAt) remote.gpsAccuracy else gpsAccuracy,
            gpsAccuracyModifiedAt = maxOf(gpsAccuracyModifiedAt, remote.gpsAccuracyModifiedAt),
            mapProvider = if (remote.mapProviderModifiedAt > mapProviderModifiedAt) remote.mapProvider else mapProvider,
            mapProviderModifiedAt = maxOf(mapProviderModifiedAt, remote.mapProviderModifiedAt),
            ttsRate = if (remote.ttsRateModifiedAt > ttsRateModifiedAt) remote.ttsRate else ttsRate,
            ttsRateModifiedAt = maxOf(ttsRateModifiedAt, remote.ttsRateModifiedAt),
            ttsPitch = if (remote.ttsPitchModifiedAt > ttsPitchModifiedAt) remote.ttsPitch else ttsPitch,
            ttsPitchModifiedAt = maxOf(ttsPitchModifiedAt, remote.ttsPitchModifiedAt),
            ttsEnabled = if (remote.ttsEnabledModifiedAt > ttsEnabledModifiedAt) remote.ttsEnabled else ttsEnabled,
            ttsEnabledModifiedAt = maxOf(ttsEnabledModifiedAt, remote.ttsEnabledModifiedAt),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): SyncableSettings {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}
