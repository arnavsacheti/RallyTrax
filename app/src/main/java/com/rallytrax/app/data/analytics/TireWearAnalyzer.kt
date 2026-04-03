package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.TrackEntity

object TireWearAnalyzer {
    data class TirePerformancePoint(
        val stintDate: Long,
        val avgCorneringG: Double?,
        val gripEventCount: Int,
        val kmOnTires: Double,
    )

    fun analyze(
        stints: List<TrackEntity>,
        tireInstallDate: Long,
        tireInstallOdometerKm: Double,
    ): List<TirePerformancePoint> {
        return stints
            .filter { it.recordedAt >= tireInstallDate }
            .sortedBy { it.recordedAt }
            .map { stint ->
                TirePerformancePoint(
                    stintDate = stint.recordedAt,
                    avgCorneringG = stint.avgCorneringG,
                    gripEventCount = stint.gripEventCount ?: 0,
                    kmOnTires = stint.distanceMeters / 1000.0,
                )
            }
    }
}
