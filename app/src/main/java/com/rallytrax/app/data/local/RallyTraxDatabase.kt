package com.rallytrax.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RallyTraxDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackPointDao(): TrackPointDao
}
