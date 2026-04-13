package com.rallytrax.app.data.repository

import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TripDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val trackDao: TrackDao,
) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()

    fun getTripById(tripId: String): Flow<TripEntity?> = tripDao.getTripById(tripId)

    fun getTracksForTrip(tripId: String): Flow<List<TrackEntity>> = tripDao.getTracksForTrip(tripId)

    fun getTrackCountForTrip(tripId: String): Flow<Int> = tripDao.getTrackCountForTrip(tripId)

    fun getTotalDistanceForTrip(tripId: String): Flow<Double?> = tripDao.getTotalDistanceForTrip(tripId)

    fun getTotalDurationForTrip(tripId: String): Flow<Long?> = tripDao.getTotalDurationForTrip(tripId)

    suspend fun getTrackCountForTripOnce(tripId: String): Int = tripDao.getTrackCountForTripOnce(tripId)
    suspend fun getTotalDistanceForTripOnce(tripId: String): Double = tripDao.getTotalDistanceForTripOnce(tripId)
    suspend fun getTotalDurationForTripOnce(tripId: String): Long = tripDao.getTotalDurationForTripOnce(tripId)

    suspend fun createTrip(name: String, description: String? = null): String {
        val now = System.currentTimeMillis()
        val trip = TripEntity(name = name, description = description, createdAt = now, updatedAt = now)
        tripDao.insertTrip(trip)
        return trip.id
    }

    suspend fun updateTrip(trip: TripEntity) {
        tripDao.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun assignTrackToTrip(trackId: String, tripId: String?) {
        trackDao.updateTripId(trackId, tripId)
        if (tripId != null) {
            val trip = tripDao.getTripById(tripId).first()
            trip?.let { tripDao.updateTrip(it.copy(updatedAt = System.currentTimeMillis())) }
        }
    }

    suspend fun deleteTrip(trip: TripEntity) {
        trackDao.clearTripAssignments(trip.id)
        tripDao.deleteTrip(trip)
    }
}
