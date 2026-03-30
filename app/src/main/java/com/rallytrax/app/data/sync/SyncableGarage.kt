package com.rallytrax.app.data.sync

import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncableGarage(
    val vehicles: List<SyncableVehicle> = emptyList(),
    val maintenanceSchedules: List<SyncableMaintenanceSchedule> = emptyList(),
    val maintenanceRecords: List<SyncableMaintenanceRecord> = emptyList(),
    val fuelLogs: List<SyncableFuelLog> = emptyList(),
    val modifiedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): SyncableGarage {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

@Serializable
data class SyncableVehicle(
    val id: String,
    val name: String,
    val year: Int,
    val make: String,
    val model: String,
    val trim: String? = null,
    val vin: String? = null,
    val photoUri: String? = null,
    val engineDisplacementL: Double? = null,
    val cylinders: Int? = null,
    val horsePower: Int? = null,
    val drivetrain: String? = null,
    val transmissionType: String? = null,
    val transmissionSpeeds: Int? = null,
    val curbWeightKg: Double? = null,
    val fuelType: String = "Gasoline",
    val tankSizeGal: Double? = null,
    val epaCityMpg: Double? = null,
    val epaHwyMpg: Double? = null,
    val epaCombinedMpg: Double? = null,
    val tireSize: String? = null,
    val modsList: String? = null,
    val odometerKm: Double = 0.0,
    val vehicleType: String = "CAR",
    val oilType: String? = null,
    val engineConfiguration: String? = null,
    val wheelDiameter: Double? = null,
    val isActive: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun toEntity(): VehicleEntity = VehicleEntity(
        id = id,
        name = name,
        year = year,
        make = make,
        model = model,
        trim = trim,
        vin = vin,
        photoUri = photoUri,
        engineDisplacementL = engineDisplacementL,
        cylinders = cylinders,
        horsePower = horsePower,
        drivetrain = drivetrain,
        transmissionType = transmissionType,
        transmissionSpeeds = transmissionSpeeds,
        curbWeightKg = curbWeightKg,
        fuelType = fuelType,
        tankSizeGal = tankSizeGal,
        epaCityMpg = epaCityMpg,
        epaHwyMpg = epaHwyMpg,
        epaCombinedMpg = epaCombinedMpg,
        tireSize = tireSize,
        modsList = modsList,
        odometerKm = odometerKm,
        vehicleType = vehicleType,
        oilType = oilType,
        engineConfiguration = engineConfiguration,
        wheelDiameter = wheelDiameter,
        isActive = isActive,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun VehicleEntity.toSyncable(): SyncableVehicle = SyncableVehicle(
    id = id,
    name = name,
    year = year,
    make = make,
    model = model,
    trim = trim,
    vin = vin,
    photoUri = photoUri,
    engineDisplacementL = engineDisplacementL,
    cylinders = cylinders,
    horsePower = horsePower,
    drivetrain = drivetrain,
    transmissionType = transmissionType,
    transmissionSpeeds = transmissionSpeeds,
    curbWeightKg = curbWeightKg,
    fuelType = fuelType,
    tankSizeGal = tankSizeGal,
    epaCityMpg = epaCityMpg,
    epaHwyMpg = epaHwyMpg,
    epaCombinedMpg = epaCombinedMpg,
    tireSize = tireSize,
    modsList = modsList,
    odometerKm = odometerKm,
    vehicleType = vehicleType,
    oilType = oilType,
    engineConfiguration = engineConfiguration,
    wheelDiameter = wheelDiameter,
    isActive = isActive,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Serializable
data class SyncableMaintenanceSchedule(
    val id: String,
    val vehicleId: String,
    val serviceType: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val lastServiceDate: Long? = null,
    val lastServiceOdometerKm: Double? = null,
    val nextDueDate: Long? = null,
    val nextDueOdometerKm: Double? = null,
    val status: String = "UPCOMING",
    val notifyDaysBefore: Int = 30,
    val createdAt: Long = 0L,
) {
    fun toEntity(): MaintenanceScheduleEntity = MaintenanceScheduleEntity(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastServiceDate = lastServiceDate,
        lastServiceOdometerKm = lastServiceOdometerKm,
        nextDueDate = nextDueDate,
        nextDueOdometerKm = nextDueOdometerKm,
        status = status,
        notifyDaysBefore = notifyDaysBefore,
        createdAt = createdAt,
    )
}

fun MaintenanceScheduleEntity.toSyncable(): SyncableMaintenanceSchedule =
    SyncableMaintenanceSchedule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastServiceDate = lastServiceDate,
        lastServiceOdometerKm = lastServiceOdometerKm,
        nextDueDate = nextDueDate,
        nextDueOdometerKm = nextDueOdometerKm,
        status = status,
        notifyDaysBefore = notifyDaysBefore,
        createdAt = createdAt,
    )

@Serializable
data class SyncableMaintenanceRecord(
    val id: String,
    val vehicleId: String,
    val category: String,
    val serviceType: String,
    val date: Long = 0L,
    val odometerKm: Double? = null,
    val costParts: Double? = null,
    val costLabor: Double? = null,
    val costTotal: Double = 0.0,
    val provider: String? = null,
    val isDiy: Boolean = false,
    val notes: String? = null,
    val receiptPhotoUri: String? = null,
    val createdAt: Long = 0L,
) {
    fun toEntity(): MaintenanceRecordEntity = MaintenanceRecordEntity(
        id = id,
        vehicleId = vehicleId,
        category = category,
        serviceType = serviceType,
        date = date,
        odometerKm = odometerKm,
        costParts = costParts,
        costLabor = costLabor,
        costTotal = costTotal,
        provider = provider,
        isDiy = isDiy,
        notes = notes,
        receiptPhotoUri = receiptPhotoUri,
        createdAt = createdAt,
    )
}

fun MaintenanceRecordEntity.toSyncable(): SyncableMaintenanceRecord =
    SyncableMaintenanceRecord(
        id = id,
        vehicleId = vehicleId,
        category = category,
        serviceType = serviceType,
        date = date,
        odometerKm = odometerKm,
        costParts = costParts,
        costLabor = costLabor,
        costTotal = costTotal,
        provider = provider,
        isDiy = isDiy,
        notes = notes,
        receiptPhotoUri = receiptPhotoUri,
        createdAt = createdAt,
    )

@Serializable
data class SyncableFuelLog(
    val id: String,
    val vehicleId: String,
    val trackId: String? = null,
    val date: Long = 0L,
    val odometerKm: Double = 0.0,
    val volumeL: Double = 0.0,
    val isFullTank: Boolean = true,
    val pricePerUnit: Double? = null,
    val totalCost: Double? = null,
    val fuelGrade: String? = null,
    val stationName: String? = null,
    val stationLat: Double? = null,
    val stationLon: Double? = null,
    val computedMpg: Double? = null,
    val isMissed: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = 0L,
) {
    fun toEntity(): FuelLogEntity = FuelLogEntity(
        id = id,
        vehicleId = vehicleId,
        trackId = trackId,
        date = date,
        odometerKm = odometerKm,
        volumeL = volumeL,
        isFullTank = isFullTank,
        pricePerUnit = pricePerUnit,
        totalCost = totalCost,
        fuelGrade = fuelGrade,
        stationName = stationName,
        stationLat = stationLat,
        stationLon = stationLon,
        computedMpg = computedMpg,
        isMissed = isMissed,
        notes = notes,
        createdAt = createdAt,
    )
}

fun FuelLogEntity.toSyncable(): SyncableFuelLog = SyncableFuelLog(
    id = id,
    vehicleId = vehicleId,
    trackId = trackId,
    date = date,
    odometerKm = odometerKm,
    volumeL = volumeL,
    isFullTank = isFullTank,
    pricePerUnit = pricePerUnit,
    totalCost = totalCost,
    fuelGrade = fuelGrade,
    stationName = stationName,
    stationLat = stationLat,
    stationLon = stationLon,
    computedMpg = computedMpg,
    isMissed = isMissed,
    notes = notes,
    createdAt = createdAt,
)
