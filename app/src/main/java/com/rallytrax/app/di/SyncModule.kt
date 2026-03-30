package com.rallytrax.app.di

import android.content.Context
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        preferencesRepository: UserPreferencesRepository,
        vehicleDao: VehicleDao,
        maintenanceDao: MaintenanceDao,
        fuelLogDao: FuelLogDao,
    ): SyncManager = SyncManager(context, preferencesRepository, vehicleDao, maintenanceDao, fuelLogDao)
}
