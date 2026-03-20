package com.rallytrax.app.di

import android.content.Context
import androidx.room.Room
import com.rallytrax.app.data.local.RallyTraxDatabase
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
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
        ).build()
    }

    @Provides
    fun provideTrackDao(database: RallyTraxDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    fun provideTrackPointDao(database: RallyTraxDatabase): TrackPointDao {
        return database.trackPointDao()
    }
}
