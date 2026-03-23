package com.rallytrax.app.data.achievements

import com.rallytrax.app.data.local.dao.AchievementDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.entity.AchievementEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementTracker @Inject constructor(
    private val achievementDao: AchievementDao,
    private val trackDao: TrackDao,
) {

    suspend fun seedAchievements() {
        val existing = achievementDao.getTotalCount()
        if (existing > 0) return
        achievementDao.insertAll(ALL_ACHIEVEMENTS)
    }

    /**
     * Check and update achievement progress after a track is saved.
     * Returns list of newly unlocked achievements (for celebration UI).
     */
    suspend fun checkAndUpdate(track: TrackEntity): List<AchievementEntity> {
        val now = System.currentTimeMillis()
        val newlyUnlocked = mutableListOf<AchievementEntity>()

        // Distance milestones
        val totalDistance = trackDao.getTotalDistanceAllTime()
        val totalDistanceKm = totalDistance / 1000.0
        for (def in DISTANCE_MILESTONES) {
            val achievement = achievementDao.getById(def.id) ?: continue
            if (achievement.unlockedAt != null) continue
            val updated = achievement.copy(
                currentValue = totalDistanceKm,
                progress = (totalDistanceKm / def.targetValue).coerceAtMost(1.0),
            )
            if (totalDistanceKm >= def.targetValue) {
                val unlocked = updated.copy(unlockedAt = now, progress = 1.0)
                achievementDao.update(unlocked)
                newlyUnlocked.add(unlocked)
            } else {
                achievementDao.update(updated)
            }
        }

        // Route mastery
        val routeTracks = trackDao.getTracksForRoute(track.name)
        val routeCount = routeTracks.size
        for (def in ROUTE_MASTERY_MILESTONES) {
            val achievement = achievementDao.getById(def.id) ?: continue
            if (achievement.unlockedAt != null) continue
            val updated = achievement.copy(
                currentValue = routeCount.toDouble(),
                progress = (routeCount.toDouble() / def.targetValue).coerceAtMost(1.0),
            )
            if (routeCount >= def.targetValue.toInt()) {
                val unlocked = updated.copy(unlockedAt = now, progress = 1.0)
                achievementDao.update(unlocked)
                newlyUnlocked.add(unlocked)
            } else {
                achievementDao.update(updated)
            }
        }

        // Streak achievements
        val streak = computeCurrentStreak()
        for (def in STREAK_MILESTONES) {
            val achievement = achievementDao.getById(def.id) ?: continue
            if (achievement.unlockedAt != null) continue
            val updated = achievement.copy(
                currentValue = streak.toDouble(),
                progress = (streak.toDouble() / def.targetValue).coerceAtMost(1.0),
            )
            if (streak >= def.targetValue.toInt()) {
                val unlocked = updated.copy(unlockedAt = now, progress = 1.0)
                achievementDao.update(unlocked)
                newlyUnlocked.add(unlocked)
            } else {
                achievementDao.update(updated)
            }
        }

        // Performance: PR on any route
        val prAchievement = achievementDao.getById("performance_pr") ?: return newlyUnlocked
        if (prAchievement.unlockedAt == null && routeTracks.size >= 2) {
            val bestTime = trackDao.getPersonalBestForRoute(track.name)
            if (bestTime != null && track.durationMs > 0 && track.durationMs <= bestTime) {
                val unlocked = prAchievement.copy(
                    unlockedAt = now,
                    progress = 1.0,
                    currentValue = 1.0,
                )
                achievementDao.update(unlocked)
                newlyUnlocked.add(unlocked)
            }
        }

        return newlyUnlocked
    }

    private suspend fun computeCurrentStreak(): Int {
        val allTracks = trackDao.getAllTracksOnce()
        if (allTracks.isEmpty()) return 0

        val zone = ZoneId.systemDefault()
        val drivingDays = allTracks
            .map { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()

        if (drivingDays.isEmpty()) return 0

        val today = Instant.now().atZone(zone).toLocalDate()
        // Streak must include today or yesterday
        val firstDay = drivingDays.first()
        if (ChronoUnit.DAYS.between(firstDay, today) > 1) return 0

        var streak = 1
        for (i in 0 until drivingDays.size - 1) {
            val diff = ChronoUnit.DAYS.between(drivingDays[i + 1], drivingDays[i])
            if (diff == 1L) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    companion object {
        private data class AchievementDef(val id: String, val targetValue: Double)

        private val DISTANCE_MILESTONES = listOf(
            AchievementDef("distance_100km", 100.0),
            AchievementDef("distance_500km", 500.0),
            AchievementDef("distance_1000km", 1000.0),
            AchievementDef("distance_5000km", 5000.0),
            AchievementDef("distance_10000km", 10000.0),
        )

        private val ROUTE_MASTERY_MILESTONES = listOf(
            AchievementDef("route_mastery_5", 5.0),
            AchievementDef("route_mastery_10", 10.0),
            AchievementDef("route_mastery_25", 25.0),
        )

        private val STREAK_MILESTONES = listOf(
            AchievementDef("streak_7", 7.0),
            AchievementDef("streak_30", 30.0),
        )

        val ALL_ACHIEVEMENTS = listOf(
            // Distance
            AchievementEntity("distance_100km", "DISTANCE", "Century", "Drive 100 km total", targetValue = 100.0),
            AchievementEntity("distance_500km", "DISTANCE", "Road Warrior", "Drive 500 km total", targetValue = 500.0),
            AchievementEntity("distance_1000km", "DISTANCE", "Thousand Miler", "Drive 1,000 km total", targetValue = 1000.0),
            AchievementEntity("distance_5000km", "DISTANCE", "Cross Country", "Drive 5,000 km total", targetValue = 5000.0),
            AchievementEntity("distance_10000km", "DISTANCE", "Globetrotter", "Drive 10,000 km total", targetValue = 10000.0),
            // Route Mastery
            AchievementEntity("route_mastery_5", "ROUTE_MASTERY", "Regular", "Complete the same route 5 times", targetValue = 5.0),
            AchievementEntity("route_mastery_10", "ROUTE_MASTERY", "Expert", "Complete the same route 10 times", targetValue = 10.0),
            AchievementEntity("route_mastery_25", "ROUTE_MASTERY", "Master", "Complete the same route 25 times", targetValue = 25.0),
            // Streaks
            AchievementEntity("streak_7", "STREAK", "Week Warrior", "Drive 7 days in a row", targetValue = 7.0),
            AchievementEntity("streak_30", "STREAK", "Monthly Devotion", "Drive 30 days in a row", targetValue = 30.0),
            // Performance
            AchievementEntity("performance_pr", "PERFORMANCE", "Personal Record", "Beat your best time on any route", targetValue = 1.0),
        )
    }
}
