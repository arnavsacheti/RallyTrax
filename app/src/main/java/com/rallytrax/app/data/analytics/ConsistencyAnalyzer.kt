package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.SegmentRunEntity
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Compares multiple runs of the same segment to produce a consistency score.
 */
object ConsistencyAnalyzer {

    data class ConsistencyResult(
        val overallScore: Int,
        val speedVariancePct: Double,
        val bestRunId: String?,
        val worstRunId: String?,
    )

    /**
     * Requires at least 2 runs. Returns null otherwise.
     *
     * Scores based on coefficient of variation (CV) of avgSpeedMps:
     *   CV < 5%  → 95
     *   CV < 10% → 80
     *   CV < 20% → 55
     *   CV >= 20% → 20
     */
    fun analyze(runs: List<SegmentRunEntity>): ConsistencyResult? {
        if (runs.size < 2) return null

        val speeds = runs.map { it.avgSpeedMps }
        val mean = speeds.average()
        if (mean == 0.0) return null

        val stdDev = sqrt(speeds.sumOf { (it - mean).pow(2) } / (speeds.size - 1))
        val cv = (stdDev / mean) * 100.0

        val score = when {
            cv < 5.0 -> 95
            cv < 10.0 -> 80
            cv < 20.0 -> 55
            else -> 20
        }

        val best = runs.maxByOrNull { it.avgSpeedMps }
        val worst = runs.minByOrNull { it.avgSpeedMps }

        return ConsistencyResult(
            overallScore = score,
            speedVariancePct = cv,
            bestRunId = best?.id,
            worstRunId = worst?.id,
        )
    }
}
