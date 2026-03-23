package com.rallytrax.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rallytrax.app.data.sync.SyncableSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class UnitSystem { METRIC, IMPERIAL }
enum class GpsAccuracy { HIGH, BATTERY_SAVER }
enum class MapProviderPreference { AUTO, GOOGLE_MAPS, OPENSTREETMAP }

object GpsIntervalConfig {
    // HIGH accuracy: rally/motorsport — aggressive updates for smooth tracking
    const val HIGH_INTERVAL_MS = 200L
    const val HIGH_MIN_INTERVAL_MS = 100L
    const val HIGH_MIN_DISTANCE_M = 0f

    // BATTERY_SAVER: relaxed updates
    const val SAVER_INTERVAL_MS = 2000L
    const val SAVER_MIN_INTERVAL_MS = 1000L
    const val SAVER_MIN_DISTANCE_M = 5f
}

data class UserPreferencesData(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val gpsAccuracy: GpsAccuracy = GpsAccuracy.HIGH,
    val mapProvider: MapProviderPreference = MapProviderPreference.AUTO,
    val ttsRate: Float = 1.25f,
    val ttsPitch: Float = 1.15f,
    val ttsEnabled: Boolean = true,
    val paceNoteSensitivity: Float = 5f,
    val onboardingCompleted: Boolean = false,
    val keepScreenOn: Boolean = false,
    // Activity lifecycle preferences
    val autoPauseEnabled: Boolean = true,
    val autoPauseDelaySeconds: Int = 30,
    val weeklyDistanceGoalKm: Double? = null,
    // Sync-related
    val lastSyncTime: Long = 0L,
    val backupTracksEnabled: Boolean = false,
    val drivePageToken: String? = null,
    val signedInEmail: String? = null,
)

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val GPS_ACCURACY = stringPreferencesKey("gps_accuracy")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val MAP_PROVIDER = stringPreferencesKey("map_provider")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val PACE_NOTE_SENSITIVITY = floatPreferencesKey("pace_note_sensitivity")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        // Activity lifecycle
        val AUTO_PAUSE_ENABLED = booleanPreferencesKey("auto_pause_enabled")
        val AUTO_PAUSE_DELAY_SECONDS = intPreferencesKey("auto_pause_delay_seconds")
        val WEEKLY_DISTANCE_GOAL_KM = doublePreferencesKey("weekly_distance_goal_km")
        // Sync
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val BACKUP_TRACKS_ENABLED = booleanPreferencesKey("backup_tracks_enabled")
        val DRIVE_PAGE_TOKEN = stringPreferencesKey("drive_page_token")
        val SIGNED_IN_EMAIL = stringPreferencesKey("signed_in_email")
        // Per-field modification timestamps for sync conflict resolution
        val THEME_MODE_MODIFIED_AT = longPreferencesKey("theme_mode_modified_at")
        val UNIT_SYSTEM_MODIFIED_AT = longPreferencesKey("unit_system_modified_at")
        val GPS_ACCURACY_MODIFIED_AT = longPreferencesKey("gps_accuracy_modified_at")
        val MAP_PROVIDER_MODIFIED_AT = longPreferencesKey("map_provider_modified_at")
        val TTS_RATE_MODIFIED_AT = longPreferencesKey("tts_rate_modified_at")
        val TTS_PITCH_MODIFIED_AT = longPreferencesKey("tts_pitch_modified_at")
        val TTS_ENABLED_MODIFIED_AT = longPreferencesKey("tts_enabled_modified_at")
    }

    val preferences: Flow<UserPreferencesData> = dataStore.data.map { prefs ->
        UserPreferencesData(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                try { ThemeMode.valueOf(it) } catch (_: Exception) { ThemeMode.SYSTEM }
            } ?: ThemeMode.SYSTEM,
            unitSystem = prefs[Keys.UNIT_SYSTEM]?.let {
                try { UnitSystem.valueOf(it) } catch (_: Exception) { UnitSystem.METRIC }
            } ?: UnitSystem.METRIC,
            gpsAccuracy = prefs[Keys.GPS_ACCURACY]?.let {
                try { GpsAccuracy.valueOf(it) } catch (_: Exception) { GpsAccuracy.HIGH }
            } ?: GpsAccuracy.HIGH,
            mapProvider = prefs[Keys.MAP_PROVIDER]?.let {
                try { MapProviderPreference.valueOf(it) } catch (_: Exception) { MapProviderPreference.AUTO }
            } ?: MapProviderPreference.AUTO,
            ttsRate = prefs[Keys.TTS_RATE] ?: 1.25f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.15f,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            paceNoteSensitivity = prefs[Keys.PACE_NOTE_SENSITIVITY] ?: 5f,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            autoPauseEnabled = prefs[Keys.AUTO_PAUSE_ENABLED] ?: true,
            autoPauseDelaySeconds = prefs[Keys.AUTO_PAUSE_DELAY_SECONDS] ?: 30,
            weeklyDistanceGoalKm = prefs[Keys.WEEKLY_DISTANCE_GOAL_KM],
            lastSyncTime = prefs[Keys.LAST_SYNC_TIME] ?: 0L,
            backupTracksEnabled = prefs[Keys.BACKUP_TRACKS_ENABLED] ?: false,
            drivePageToken = prefs[Keys.DRIVE_PAGE_TOKEN],
            signedInEmail = prefs[Keys.SIGNED_IN_EMAIL],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit {
            it[Keys.THEME_MODE] = mode.name
            it[Keys.THEME_MODE_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setUnitSystem(system: UnitSystem) {
        dataStore.edit {
            it[Keys.UNIT_SYSTEM] = system.name
            it[Keys.UNIT_SYSTEM_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setGpsAccuracy(accuracy: GpsAccuracy) {
        dataStore.edit {
            it[Keys.GPS_ACCURACY] = accuracy.name
            it[Keys.GPS_ACCURACY_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setMapProvider(provider: MapProviderPreference) {
        dataStore.edit {
            it[Keys.MAP_PROVIDER] = provider.name
            it[Keys.MAP_PROVIDER_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setTtsRate(rate: Float) {
        dataStore.edit {
            it[Keys.TTS_RATE] = rate
            it[Keys.TTS_RATE_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setTtsPitch(pitch: Float) {
        dataStore.edit {
            it[Keys.TTS_PITCH] = pitch
            it[Keys.TTS_PITCH_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.TTS_ENABLED] = enabled
            it[Keys.TTS_ENABLED_MODIFIED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setPaceNoteSensitivity(sensitivity: Float) {
        dataStore.edit { it[Keys.PACE_NOTE_SENSITIVITY] = sensitivity }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLastSyncTime(time: Long) {
        dataStore.edit { it[Keys.LAST_SYNC_TIME] = time }
    }

    suspend fun setBackupTracksEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKUP_TRACKS_ENABLED] = enabled }
    }

    suspend fun setDrivePageToken(token: String?) {
        dataStore.edit {
            if (token != null) {
                it[Keys.DRIVE_PAGE_TOKEN] = token
            } else {
                it.remove(Keys.DRIVE_PAGE_TOKEN)
            }
        }
    }

    suspend fun setSignedInEmail(email: String?) {
        dataStore.edit {
            if (email != null) {
                it[Keys.SIGNED_IN_EMAIL] = email
            } else {
                it.remove(Keys.SIGNED_IN_EMAIL)
            }
        }
    }

    suspend fun toSyncableSettings(): SyncableSettings {
        val p = dataStore.data.first()
        return SyncableSettings(
            themeMode = p[Keys.THEME_MODE] ?: "SYSTEM",
            themeModeModifiedAt = p[Keys.THEME_MODE_MODIFIED_AT] ?: 0L,
            unitSystem = p[Keys.UNIT_SYSTEM] ?: "METRIC",
            unitSystemModifiedAt = p[Keys.UNIT_SYSTEM_MODIFIED_AT] ?: 0L,
            gpsAccuracy = p[Keys.GPS_ACCURACY] ?: "HIGH",
            gpsAccuracyModifiedAt = p[Keys.GPS_ACCURACY_MODIFIED_AT] ?: 0L,
            mapProvider = p[Keys.MAP_PROVIDER] ?: "AUTO",
            mapProviderModifiedAt = p[Keys.MAP_PROVIDER_MODIFIED_AT] ?: 0L,
            ttsRate = p[Keys.TTS_RATE] ?: 1.25f,
            ttsRateModifiedAt = p[Keys.TTS_RATE_MODIFIED_AT] ?: 0L,
            ttsPitch = p[Keys.TTS_PITCH] ?: 1.15f,
            ttsPitchModifiedAt = p[Keys.TTS_PITCH_MODIFIED_AT] ?: 0L,
            ttsEnabled = p[Keys.TTS_ENABLED] ?: true,
            ttsEnabledModifiedAt = p[Keys.TTS_ENABLED_MODIFIED_AT] ?: 0L,
        )
    }

    suspend fun applySyncableSettings(settings: SyncableSettings) {
        dataStore.edit { prefs ->
            // Only apply each field if the incoming timestamp is newer
            val currentThemeModified = prefs[Keys.THEME_MODE_MODIFIED_AT] ?: 0L
            if (settings.themeModeModifiedAt > currentThemeModified) {
                prefs[Keys.THEME_MODE] = settings.themeMode
                prefs[Keys.THEME_MODE_MODIFIED_AT] = settings.themeModeModifiedAt
            }

            val currentUnitModified = prefs[Keys.UNIT_SYSTEM_MODIFIED_AT] ?: 0L
            if (settings.unitSystemModifiedAt > currentUnitModified) {
                prefs[Keys.UNIT_SYSTEM] = settings.unitSystem
                prefs[Keys.UNIT_SYSTEM_MODIFIED_AT] = settings.unitSystemModifiedAt
            }

            val currentGpsModified = prefs[Keys.GPS_ACCURACY_MODIFIED_AT] ?: 0L
            if (settings.gpsAccuracyModifiedAt > currentGpsModified) {
                prefs[Keys.GPS_ACCURACY] = settings.gpsAccuracy
                prefs[Keys.GPS_ACCURACY_MODIFIED_AT] = settings.gpsAccuracyModifiedAt
            }

            val currentMapModified = prefs[Keys.MAP_PROVIDER_MODIFIED_AT] ?: 0L
            if (settings.mapProviderModifiedAt > currentMapModified) {
                prefs[Keys.MAP_PROVIDER] = settings.mapProvider
                prefs[Keys.MAP_PROVIDER_MODIFIED_AT] = settings.mapProviderModifiedAt
            }

            val currentRateModified = prefs[Keys.TTS_RATE_MODIFIED_AT] ?: 0L
            if (settings.ttsRateModifiedAt > currentRateModified) {
                prefs[Keys.TTS_RATE] = settings.ttsRate
                prefs[Keys.TTS_RATE_MODIFIED_AT] = settings.ttsRateModifiedAt
            }

            val currentPitchModified = prefs[Keys.TTS_PITCH_MODIFIED_AT] ?: 0L
            if (settings.ttsPitchModifiedAt > currentPitchModified) {
                prefs[Keys.TTS_PITCH] = settings.ttsPitch
                prefs[Keys.TTS_PITCH_MODIFIED_AT] = settings.ttsPitchModifiedAt
            }

            val currentTtsEnabledModified = prefs[Keys.TTS_ENABLED_MODIFIED_AT] ?: 0L
            if (settings.ttsEnabledModifiedAt > currentTtsEnabledModified) {
                prefs[Keys.TTS_ENABLED] = settings.ttsEnabled
                prefs[Keys.TTS_ENABLED_MODIFIED_AT] = settings.ttsEnabledModifiedAt
            }
        }
    }

    suspend fun setAutoPauseEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_PAUSE_ENABLED] = enabled }
    }

    suspend fun setAutoPauseDelaySeconds(seconds: Int) {
        dataStore.edit { it[Keys.AUTO_PAUSE_DELAY_SECONDS] = seconds }
    }

    suspend fun setWeeklyDistanceGoalKm(goalKm: Double?) {
        dataStore.edit {
            if (goalKm != null) {
                it[Keys.WEEKLY_DISTANCE_GOAL_KM] = goalKm
            } else {
                it.remove(Keys.WEEKLY_DISTANCE_GOAL_KM)
            }
        }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { it.clear() }
    }
}
