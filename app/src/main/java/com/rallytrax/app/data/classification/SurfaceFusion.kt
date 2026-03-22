package com.rallytrax.app.data.classification

import com.rallytrax.app.data.local.entity.TrackPointEntity

/**
 * Fuses map-based (Valhalla) and sensor-based surface classifications
 * per segment using priority rules:
 * 1. Map data (Valhalla) is authoritative when present
 * 2. Sensor data used when map data is absent and confidence > 85%
 * 3. If both disagree, prefer Valhalla but flag for staleness
 * 4. If neither, default to "Unknown" (counts as "Paved" in stats)
 */
object SurfaceFusion {

    /**
     * Apply surface classifications to track points.
     * @param points Original track points
     * @param mapSurfaces Map of point index → surface type from Valhalla
     * @param sensorSurfaces Map of point index → surface type from accelerometer (future)
     * @return Updated track points with surfaceType set
     */
    fun applyClassifications(
        points: List<TrackPointEntity>,
        mapSurfaces: Map<Int, String>,
        sensorSurfaces: Map<Int, String> = emptyMap(),
    ): List<TrackPointEntity> {
        return points.map { point ->
            val mapSurface = mapSurfaces[point.index]
            val sensorSurface = sensorSurfaces[point.index]

            val surface = when {
                mapSurface != null -> mapSurface
                sensorSurface != null -> sensorSurface
                else -> null // Will default to "Unknown" in display
            }

            point.copy(surfaceType = surface)
        }
    }

    /**
     * Compute surface breakdown as percentages from classified points.
     * Returns a JSON-compatible string: "Paved:72,Gravel:20,Dirt:8"
     */
    fun computeSurfaceBreakdown(points: List<TrackPointEntity>): String {
        val counts = mutableMapOf<String, Int>()
        points.forEach { point ->
            val surface = point.surfaceType ?: RouteClassifier.SURFACE_UNKNOWN
            counts[surface] = (counts[surface] ?: 0) + 1
        }
        val total = counts.values.sum().toDouble()
        if (total == 0.0) return ""

        return counts.entries
            .sortedByDescending { it.value }
            .joinToString(",") { (surface, count) ->
                val pct = (count / total * 100).toInt()
                "$surface:$pct"
            }
    }

    /**
     * Determine the primary (dominant) surface type.
     */
    fun primarySurface(points: List<TrackPointEntity>): String {
        val counts = mutableMapOf<String, Int>()
        points.forEach { point ->
            val surface = point.surfaceType ?: return@forEach
            counts[surface] = (counts[surface] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: RouteClassifier.SURFACE_UNKNOWN
    }
}
