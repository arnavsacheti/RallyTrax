package com.rallytrax.app.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuggedModeManager @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
) {
    val isRuggedModeActive: Flow<Boolean> = preferencesRepository.preferences.map { prefs ->
        if (!prefs.ruggedModeEnabled) return@map false
        val expiresAt = prefs.ruggedModeExpiresAt ?: return@map true // "until turned off"
        System.currentTimeMillis() < expiresAt
    }

    suspend fun enableRuggedMode(duration: RuggedModeDuration) {
        when (duration) {
            RuggedModeDuration.OFF -> {
                preferencesRepository.setRuggedModeEnabled(false)
                preferencesRepository.setRuggedModeExpiresAt(null)
            }
            RuggedModeDuration.UNTIL_OFF -> {
                preferencesRepository.setRuggedModeEnabled(true)
                preferencesRepository.setRuggedModeExpiresAt(null)
            }
            else -> {
                preferencesRepository.setRuggedModeEnabled(true)
                preferencesRepository.setRuggedModeExpiresAt(
                    System.currentTimeMillis() + (duration.hours!! * 3_600_000L),
                )
            }
        }
    }

    suspend fun disableRuggedMode() {
        preferencesRepository.setRuggedModeEnabled(false)
        preferencesRepository.setRuggedModeExpiresAt(null)
    }
}
