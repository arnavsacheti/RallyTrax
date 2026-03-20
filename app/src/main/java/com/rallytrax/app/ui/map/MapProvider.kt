package com.rallytrax.app.ui.map

import com.rallytrax.app.BuildConfig

/**
 * Determines which map provider to use at runtime.
 * Google Maps requires a valid API key; when absent, OSM tiles are used instead.
 */
object MapProvider {
    val useGoogleMaps: Boolean
        get() = BuildConfig.MAPS_API_KEY.isNotBlank()
}
