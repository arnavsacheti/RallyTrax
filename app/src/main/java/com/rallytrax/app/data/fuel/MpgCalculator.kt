package com.rallytrax.app.data.fuel

import com.rallytrax.app.data.local.entity.FuelLogEntity

/**
 * Fill-to-fill MPG calculation engine.
 *
 * Uses the industry-standard algorithm:
 * - Full-to-full: MPG = (current_odometer − previous_full_odometer) / gallons_filled
 * - Partial fills: accumulate gallons, compute at next full fill
 * - isMissed: breaks the chain, next calculation starts fresh
 */
object MpgCalculator {

    private const val KM_PER_MILE = 1.60934
    private const val LITRES_PER_GALLON = 3.78541

    /**
     * Calculate MPG for each fuel log entry using fill-to-fill algorithm.
     * Logs must be sorted by odometerKm ASC.
     * Returns updated logs with computedMpg set where calculable.
     */
    fun calculateFillToFill(logs: List<FuelLogEntity>): List<FuelLogEntity> {
        if (logs.size < 2) return logs

        val result = mutableListOf<FuelLogEntity>()
        var lastFullOdometerKm: Double? = null
        var accumulatedLitres = 0.0
        var chainBroken = true // Start with no baseline

        for (log in logs) {
            if (log.isMissed) {
                // Break the chain — mark as null MPG, reset accumulation
                result.add(log.copy(computedMpg = null))
                lastFullOdometerKm = null
                accumulatedLitres = 0.0
                chainBroken = true
                continue
            }

            if (log.isFullTank) {
                if (lastFullOdometerKm != null && !chainBroken) {
                    // Can calculate MPG
                    val distanceKm = log.odometerKm - lastFullOdometerKm
                    val totalLitres = accumulatedLitres + log.volumeL
                    val distanceMiles = distanceKm / KM_PER_MILE
                    val totalGallons = totalLitres / LITRES_PER_GALLON
                    val mpg = if (totalGallons > 0) distanceMiles / totalGallons else null
                    result.add(log.copy(computedMpg = mpg))
                } else {
                    // First full fill or chain was broken — set baseline, no MPG yet
                    result.add(log.copy(computedMpg = null))
                }
                // Reset for next segment
                lastFullOdometerKm = log.odometerKm
                accumulatedLitres = 0.0
                chainBroken = false
            } else {
                // Partial fill — accumulate but don't compute MPG
                accumulatedLitres += log.volumeL
                result.add(log.copy(computedMpg = null))
            }
        }

        return result
    }

    /**
     * Compute lifetime average MPG using total miles / total gallons (weighted).
     * Only considers logs between full-fill pairs.
     */
    fun lifetimeAverage(logs: List<FuelLogEntity>): Double? {
        if (logs.size < 2) return null

        val sorted = logs.sortedBy { it.odometerKm }
        var totalDistanceKm = 0.0
        var totalLitres = 0.0
        var lastFullOdometerKm: Double? = null
        var accumulatedLitres = 0.0
        var chainBroken = true

        for (log in sorted) {
            if (log.isMissed) {
                lastFullOdometerKm = null
                accumulatedLitres = 0.0
                chainBroken = true
                continue
            }
            if (log.isFullTank) {
                if (lastFullOdometerKm != null && !chainBroken) {
                    totalDistanceKm += log.odometerKm - lastFullOdometerKm
                    totalLitres += accumulatedLitres + log.volumeL
                }
                lastFullOdometerKm = log.odometerKm
                accumulatedLitres = 0.0
                chainBroken = false
            } else {
                accumulatedLitres += log.volumeL
            }
        }

        if (totalLitres <= 0 || totalDistanceKm <= 0) return null
        val totalMiles = totalDistanceKm / KM_PER_MILE
        val totalGallons = totalLitres / LITRES_PER_GALLON
        return totalMiles / totalGallons
    }

    /**
     * Compute cost per mile from fuel logs.
     */
    fun costPerMile(logs: List<FuelLogEntity>): Double? {
        val totalCost = logs.mapNotNull { it.totalCost }.sum()
        if (totalCost <= 0) return null

        val sorted = logs.sortedBy { it.odometerKm }
        if (sorted.size < 2) return null

        val totalDistanceKm = sorted.last().odometerKm - sorted.first().odometerKm
        if (totalDistanceKm <= 0) return null

        val totalMiles = totalDistanceKm / KM_PER_MILE
        return totalCost / totalMiles
    }

    /**
     * Compute MPG for a single new fill-up given previous logs for the same vehicle.
     * previousLogs should be sorted by odometerKm ASC.
     */
    fun computeMpgForNewEntry(
        newLog: FuelLogEntity,
        previousLogs: List<FuelLogEntity>,
    ): Double? {
        if (!newLog.isFullTank) return null

        // Walk backwards from latest to find the last full-fill baseline
        var accumulatedLitres = 0.0
        for (prev in previousLogs.sortedByDescending { it.odometerKm }) {
            if (prev.isMissed) return null // Chain is broken
            if (prev.isFullTank) {
                // Found baseline
                val distanceKm = newLog.odometerKm - prev.odometerKm
                val totalLitres = accumulatedLitres + newLog.volumeL
                if (distanceKm <= 0 || totalLitres <= 0) return null
                val distanceMiles = distanceKm / KM_PER_MILE
                val totalGallons = totalLitres / LITRES_PER_GALLON
                return distanceMiles / totalGallons
            } else {
                accumulatedLitres += prev.volumeL
            }
        }
        return null // No baseline found
    }
}
