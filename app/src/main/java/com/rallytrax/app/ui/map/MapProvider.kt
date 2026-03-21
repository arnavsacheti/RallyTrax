package com.rallytrax.app.ui.map

import com.rallytrax.app.BuildConfig
import com.rallytrax.app.data.preferences.MapProviderPreference

/**
 * Determines which map provider to use at runtime based on user preference.
 * AUTO: uses Google Maps if an API key is configured, otherwise falls back to OSM.
 * GOOGLE_MAPS / OPENSTREETMAP: forces the chosen provider.
 */
object MapProvider {
    fun useGoogleMaps(preference: MapProviderPreference): Boolean = when (preference) {
        MapProviderPreference.AUTO -> BuildConfig.MAPS_API_KEY.isNotBlank()
        MapProviderPreference.GOOGLE_MAPS -> true
        MapProviderPreference.OPENSTREETMAP -> false
    }
}
