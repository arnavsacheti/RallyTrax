package com.rallytrax.app.di

import android.content.Context
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
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
        trackDao: TrackDao,
        trackPointDao: TrackPointDao,
        paceNoteDao: PaceNoteDao,
    ): SyncManager = SyncManager(context, preferencesRepository, trackDao, trackPointDao, paceNoteDao)
}
