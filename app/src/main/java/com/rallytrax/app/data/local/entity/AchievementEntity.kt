package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val unlockedAt: Long? = null,
    val progress: Double = 0.0,
    val targetValue: Double = 0.0,
    val currentValue: Double = 0.0,
)
