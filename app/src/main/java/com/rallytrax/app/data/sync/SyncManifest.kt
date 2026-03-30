package com.rallytrax.app.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tracks all synced objects in the Drive appDataFolder with their file IDs,
 * MD5 hashes, and modification timestamps.
 */
@Serializable
data class SyncManifest(
    val version: Int = 1,
    val settingsFileId: String? = null,
    val settingsMd5: String? = null,
    val settingsModifiedAt: Long = 0L,
    // Garage data (vehicles, maintenance, fuel logs)
    val garageFileId: String? = null,
    val garageMd5: String? = null,
    val garageModifiedAt: Long? = null,
    // Stubs for future stages
    val maintenanceFileId: String? = null,
    val fuelLogFileId: String? = null,
    val statsFileId: String? = null,
    val gpxFileIds: Map<String, String> = emptyMap(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): SyncManifest {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}
