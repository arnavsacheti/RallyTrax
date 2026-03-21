package com.rallytrax.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class, PaceNoteEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class RallyTraxDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun paceNoteDao(): PaceNoteDao

    companion object {
        // Planned migrations for v1.1:
        // v3 → v4 (Stage 1.1.4): CREATE TABLE grid_cells for heatmap tile provider

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pace_notes` (
                        `id` TEXT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `pointIndex` INTEGER NOT NULL,
                        `distanceFromStart` REAL NOT NULL,
                        `noteType` TEXT NOT NULL,
                        `severity` INTEGER NOT NULL,
                        `modifier` TEXT NOT NULL,
                        `callText` TEXT NOT NULL,
                        `callDistanceM` REAL NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pace_notes_trackId` ON `pace_notes` (`trackId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN accelMps2 REAL")
                db.execSQL("ALTER TABLE track_points ADD COLUMN curvatureDegPerM REAL")
            }
        }
    }
}
