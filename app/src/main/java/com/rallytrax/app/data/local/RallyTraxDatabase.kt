package com.rallytrax.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rallytrax.app.data.local.dao.GridCellDao
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.GasStationDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.data.local.entity.GasStationEntity
import com.rallytrax.app.data.local.entity.GridCellEntity
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.local.entity.VehicleEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackPointEntity::class,
        PaceNoteEntity::class,
        GridCellEntity::class,
        VehicleEntity::class,
        FuelLogEntity::class,
        GasStationEntity::class,
        MaintenanceRecordEntity::class,
        MaintenanceScheduleEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class RallyTraxDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun paceNoteDao(): PaceNoteDao
    abstract fun gridCellDao(): GridCellDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun gasStationDao(): GasStationDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `grid_cells` (
                        `cellId` INTEGER NOT NULL,
                        `gridLat` INTEGER NOT NULL,
                        `gridLon` INTEGER NOT NULL,
                        `trackCount` INTEGER NOT NULL DEFAULT 0,
                        `pointCount` INTEGER NOT NULL DEFAULT 0,
                        `lastUpdated` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`cellId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add vehicleId FK column to tracks
                db.execSQL("ALTER TABLE tracks ADD COLUMN vehicleId TEXT DEFAULT NULL")

                // Create vehicles table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `make` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `trim` TEXT,
                        `vin` TEXT,
                        `photoUri` TEXT,
                        `engineDisplacementL` REAL,
                        `cylinders` INTEGER,
                        `horsePower` INTEGER,
                        `drivetrain` TEXT,
                        `transmissionType` TEXT,
                        `transmissionSpeeds` INTEGER,
                        `curbWeightKg` REAL,
                        `fuelType` TEXT NOT NULL DEFAULT 'Gasoline',
                        `tankSizeGal` REAL,
                        `epaCityMpg` REAL,
                        `epaHwyMpg` REAL,
                        `epaCombinedMpg` REAL,
                        `tireSize` TEXT,
                        `modsList` TEXT,
                        `odometerKm` REAL NOT NULL DEFAULT 0.0,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create fuel_logs table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_logs` (
                        `id` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `trackId` TEXT,
                        `date` INTEGER NOT NULL DEFAULT 0,
                        `odometerKm` REAL NOT NULL DEFAULT 0.0,
                        `volumeL` REAL NOT NULL DEFAULT 0.0,
                        `isFullTank` INTEGER NOT NULL DEFAULT 1,
                        `pricePerUnit` REAL,
                        `totalCost` REAL,
                        `fuelGrade` TEXT,
                        `stationName` TEXT,
                        `stationLat` REAL,
                        `stationLon` REAL,
                        `computedMpg` REAL,
                        `isMissed` INTEGER NOT NULL DEFAULT 0,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                // Create gas_stations cache table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gas_stations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `brand` TEXT,
                        `fetchedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `maintenance_records` (
                        `id` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `serviceType` TEXT NOT NULL,
                        `date` INTEGER NOT NULL DEFAULT 0,
                        `odometerKm` REAL,
                        `costParts` REAL,
                        `costLabor` REAL,
                        `costTotal` REAL NOT NULL DEFAULT 0.0,
                        `provider` TEXT,
                        `isDiy` INTEGER NOT NULL DEFAULT 0,
                        `notes` TEXT,
                        `receiptPhotoUri` TEXT,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `maintenance_schedules` (
                        `id` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `serviceType` TEXT NOT NULL,
                        `intervalKm` REAL,
                        `intervalMonths` INTEGER,
                        `lastServiceDate` INTEGER,
                        `lastServiceOdometerKm` REAL,
                        `nextDueDate` INTEGER,
                        `nextDueOdometerKm` REAL,
                        `status` TEXT NOT NULL DEFAULT 'UPCOMING',
                        `notifyDaysBefore` INTEGER NOT NULL DEFAULT 30,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
