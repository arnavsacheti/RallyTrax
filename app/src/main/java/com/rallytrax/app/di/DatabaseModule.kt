package com.rallytrax.app.di

import android.content.Context
import androidx.room.Room
import com.rallytrax.app.data.local.RallyTraxDatabase
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.GasStationDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.GridCellDao
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RallyTraxDatabase {
        return Room.databaseBuilder(
            context,
            RallyTraxDatabase::class.java,
            "rallytrax.db",
        )
            .addMigrations(
                RallyTraxDatabase.MIGRATION_1_2,
                RallyTraxDatabase.MIGRATION_2_3,
                RallyTraxDatabase.MIGRATION_3_4,
                RallyTraxDatabase.MIGRATION_4_5,
                RallyTraxDatabase.MIGRATION_5_6,
                RallyTraxDatabase.MIGRATION_6_7,
            )
            .build()
    }

    @Provides
    fun provideTrackDao(database: RallyTraxDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    fun provideTrackPointDao(database: RallyTraxDatabase): TrackPointDao {
        return database.trackPointDao()
    }

    @Provides
    fun providePaceNoteDao(database: RallyTraxDatabase): PaceNoteDao {
        return database.paceNoteDao()
    }

    @Provides
    fun provideGridCellDao(database: RallyTraxDatabase): GridCellDao {
        return database.gridCellDao()
    }

    @Provides
    fun provideVehicleDao(database: RallyTraxDatabase): VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    fun provideFuelLogDao(database: RallyTraxDatabase): FuelLogDao {
        return database.fuelLogDao()
    }

    @Provides
    fun provideGasStationDao(database: RallyTraxDatabase): GasStationDao {
        return database.gasStationDao()
    }

    @Provides
    fun provideMaintenanceDao(database: RallyTraxDatabase): MaintenanceDao {
        return database.maintenanceDao()
    }
}
