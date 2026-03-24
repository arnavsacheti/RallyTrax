package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.local.dao.DriverProfileDao
import com.rallytrax.app.data.local.entity.DriverProfileEntity
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Updates the persistent driver profile based on actual speed data from stints.
 *
 * For each pace note with a known turn radius, finds the driver's average speed
 * through that corner and updates the running average in the profile.
 */
object DriverProfileUpdater {

    /**
     * Update the driver profile with speed observations from a completed stint.
     *
     * @param notes Pace notes generated for the stint (must have turnRadiusM set)
     * @param points Track points with speed data
     * @param dao DriverProfileDao for persistence
     */
    suspend fun updateFromStint(
        notes: List<PaceNoteEntity>,
        points: List<TrackPointEntity>,
        dao: DriverProfileDao,
    ) {
        if (points.isEmpty() || notes.isEmpty()) return

        // Build a distance-indexed speed lookup
        val distances = computeCumulativeDistances(points)
        val observations = mutableMapOf<Int, MutableList<Double>>() // bucket -> list of speeds

        for (note in notes) {
            val radiusM = note.turnRadiusM ?: continue
            if (radiusM <= 0 || radiusM > 200) continue // Only corners, not straights

            val bucket = (radiusM / 10.0).toInt() * 10

            // Find speeds of track points within ~30m of the note's position
            val noteDistance = note.distanceFromStart
            val nearbySpeed = points.indices
                .filter { i -> distances[i] in (noteDistance - 30.0)..(noteDistance + 30.0) }
                .mapNotNull { i -> points[i].speed }
                .filter { it > 0.5 } // Filter out stopped points

            if (nearbySpeed.isNotEmpty()) {
                val avgSpeed = nearbySpeed.average()
                observations.getOrPut(bucket) { mutableListOf() }.add(avgSpeed)
            }
        }

        // Update the persistent profile with running averages
        for ((bucket, speeds) in observations) {
            val newAvg = speeds.average()
            val existing = dao.getByRadiusBucket(bucket)

            if (existing != null) {
                // Running weighted average: (oldAvg * oldCount + newSum) / (oldCount + newCount)
                val totalCount = existing.sampleCount + speeds.size
                val updatedAvg = (existing.avgSpeedMps * existing.sampleCount + speeds.sum()) / totalCount
                dao.upsert(
                    existing.copy(
                        avgSpeedMps = updatedAvg,
                        sampleCount = totalCount,
                        lastUpdated = System.currentTimeMillis(),
                    )
                )
            } else {
                dao.upsert(
                    DriverProfileEntity(
                        radiusBucketM = bucket,
                        avgSpeedMps = newAvg,
                        sampleCount = speeds.size,
                        lastUpdated = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    /** Load the driver profile as a map of radiusBucket -> avgSpeedMps. */
    suspend fun loadProfile(dao: DriverProfileDao): Map<Int, Double> {
        return dao.getAll()
            .filter { it.sampleCount >= 3 } // Require minimum samples for reliability
            .associate { it.radiusBucketM to it.avgSpeedMps }
    }

    private fun computeCumulativeDistances(points: List<TrackPointEntity>): DoubleArray {
        val distances = DoubleArray(points.size)
        for (i in 1 until points.size) {
            distances[i] = distances[i - 1] + haversine(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon,
            )
        }
        return distances
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
