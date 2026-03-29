package com.rallytrax.app.ui.library

import kotlin.math.cos

data class NearMeFilter(
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double = 25.0,
)

data class BoundingBox(
    val northLat: Double,
    val southLat: Double,
    val eastLon: Double,
    val westLon: Double,
)

fun NearMeFilter.toBoundingBox(): BoundingBox {
    val latDelta = radiusKm / 111.0
    val lonDelta = radiusKm / (111.0 * cos(Math.toRadians(latitude)))
    return BoundingBox(
        northLat = latitude + latDelta,
        southLat = latitude - latDelta,
        eastLon = longitude + lonDelta,
        westLon = longitude - lonDelta,
    )
}

fun BoundingBox.overlaps(
    trackNorthLat: Double,
    trackSouthLat: Double,
    trackEastLon: Double,
    trackWestLon: Double,
): Boolean {
    return trackSouthLat <= northLat &&
        trackNorthLat >= southLat &&
        trackWestLon <= eastLon &&
        trackEastLon >= westLon
}
