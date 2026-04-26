package com.rallytrax.app.data.repository

import com.rallytrax.app.data.api.FuelEconomyApiClient
import com.rallytrax.app.data.api.FuelEconomyData
import com.rallytrax.app.data.api.FuelEconomyTrim
import com.rallytrax.app.data.api.NhtsaApiClient
import com.rallytrax.app.data.api.NhtsaMake
import com.rallytrax.app.data.api.NhtsaModel
import com.rallytrax.app.data.api.NhtsaVinResult
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val trackDao: TrackDao,
    private val nhtsaApiClient: NhtsaApiClient,
    private val fuelEconomyApiClient: FuelEconomyApiClient,
    private val syncManager: SyncManager,
) {
    // ── Vehicle CRUD ─────────────────────────────────────────────────────────

    fun getAllVehicles(): Flow<List<VehicleEntity>> = vehicleDao.getAllVehicles()

    fun getArchivedVehicles(): Flow<List<VehicleEntity>> = vehicleDao.getArchivedVehicles()

    suspend fun getVehicleById(id: String): VehicleEntity? = vehicleDao.getVehicleById(id)

    suspend fun getActiveVehicle(): VehicleEntity? = vehicleDao.getActiveVehicle()

    suspend fun addVehicle(vehicle: VehicleEntity) {
        vehicleDao.insertVehicle(vehicle)
        syncManager.scheduleDebouncedSync()
    }

    suspend fun updateVehicle(vehicle: VehicleEntity) {
        vehicleDao.updateVehicle(vehicle.copy(updatedAt = System.currentTimeMillis()))
        syncManager.scheduleDebouncedSync()
    }

    suspend fun setActiveVehicle(vehicleId: String) {
        vehicleDao.clearActiveFlag()
        val vehicle = vehicleDao.getVehicleById(vehicleId) ?: return
        vehicleDao.updateVehicle(
            vehicle.copy(isActive = true, updatedAt = System.currentTimeMillis()),
        )
        syncManager.scheduleDebouncedSync()
    }

    suspend fun archiveVehicle(vehicleId: String) {
        vehicleDao.archiveVehicle(vehicleId)
        syncManager.scheduleDebouncedSync()
    }

    suspend fun unarchiveVehicle(vehicleId: String) {
        vehicleDao.unarchiveVehicle(vehicleId)
        syncManager.scheduleDebouncedSync()
    }

    // ── Track–Vehicle Linking ────────────────────────────────────────────────

    fun getTracksForVehicle(vehicleId: String): Flow<List<TrackEntity>> =
        trackDao.getTracksByVehicleId(vehicleId)

    suspend fun getTotalDistanceForVehicle(vehicleId: String): Double =
        trackDao.getTotalDistanceForVehicle(vehicleId)

    suspend fun getTrackCountForVehicle(vehicleId: String): Int =
        trackDao.getTrackCountForVehicle(vehicleId)

    /** Combined trackCount + totalDistance in a single SQL round-trip. */
    suspend fun getStatsForVehicle(vehicleId: String): com.rallytrax.app.data.local.dao.VehicleStatsProjection =
        trackDao.getStatsForVehicle(vehicleId)

    suspend fun assignVehicleToTrack(trackId: String, vehicleId: String) {
        val track = trackDao.getTrackById(trackId) ?: return
        trackDao.updateTrack(track.copy(vehicleId = vehicleId))
    }

    // ── NHTSA API ────────────────────────────────────────────────────────────

    suspend fun fetchMakesByYear(year: Int): List<NhtsaMake> =
        nhtsaApiClient.getMakesByYear(year)

    suspend fun fetchModelsByMakeAndYear(make: String, year: Int): List<NhtsaModel> =
        nhtsaApiClient.getModelsByMakeAndYear(make, year)

    suspend fun decodeVin(vin: String): NhtsaVinResult =
        nhtsaApiClient.decodeVin(vin)

    // ── EPA API ──────────────────────────────────────────────────────────────

    suspend fun fetchEpaTrims(year: Int, make: String, model: String): List<FuelEconomyTrim> =
        fuelEconomyApiClient.getTrims(year, make, model)

    suspend fun fetchEpaVehicleData(fuelEconomyId: Int): FuelEconomyData? =
        fuelEconomyApiClient.getVehicleData(fuelEconomyId)
}
