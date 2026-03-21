package com.rallytrax.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
    val onboardingCompleted: Boolean = false,
    val keepScreenOn: Boolean = false,
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
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
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
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUnitSystem(system: UnitSystem) {
        dataStore.edit { it[Keys.UNIT_SYSTEM] = system.name }
    }

    suspend fun setGpsAccuracy(accuracy: GpsAccuracy) {
        dataStore.edit { it[Keys.GPS_ACCURACY] = accuracy.name }
    }

    suspend fun setMapProvider(provider: MapProviderPreference) {
        dataStore.edit { it[Keys.MAP_PROVIDER] = provider.name }
    }

    suspend fun setTtsRate(rate: Float) {
        dataStore.edit { it[Keys.TTS_RATE] = rate }
    }

    suspend fun setTtsPitch(pitch: Float) {
        dataStore.edit { it[Keys.TTS_PITCH] = pitch }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { it.clear() }
    }
}
