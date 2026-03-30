package com.rallytrax.app.data.analytics

import com.rallytrax.app.data.local.entity.SegmentRunEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyAnalyzerTest {

    private fun makeRun(id: String, avgSpeedMps: Double): SegmentRunEntity =
        SegmentRunEntity(
            id = id,
            segmentId = "seg-1",
            trackId = "track-1",
            startPointIndex = 0,
            endPointIndex = 100,
            durationMs = 60_000L,
            avgSpeedMps = avgSpeedMps,
            maxSpeedMps = avgSpeedMps * 1.5,
            timestamp = System.currentTimeMillis(),
        )

    // --- Returns null for fewer than 2 runs ---

    @Test
    fun analyze_emptyList_returnsNull() {
        assertNull(ConsistencyAnalyzer.analyze(emptyList()))
    }

    @Test
    fun analyze_singleRun_returnsNull() {
        assertNull(ConsistencyAnalyzer.analyze(listOf(makeRun("r1", 10.0))))
    }

    // --- CV < 5% -> score 95 ---

    @Test
    fun analyze_twoRuns_lowVariance_returnsScore95() {
        val runs = listOf(makeRun("r1", 10.0), makeRun("r2", 10.2))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(95, result.overallScore)
    }

    @Test
    fun analyze_fiveRuns_lowVariance_returnsScore95() {
        val runs = listOf(
            makeRun("r1", 20.0),
            makeRun("r2", 20.1),
            makeRun("r3", 19.9),
            makeRun("r4", 20.05),
            makeRun("r5", 19.95),
        )
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(95, result.overallScore)
    }

    // --- CV < 10% -> score 80 ---

    @Test
    fun analyze_cvUnder10_returnsScore80() {
        // speeds: 10.0 and 11.0 -> mean=10.5, stddev~0.707, CV~6.7%
        val runs = listOf(makeRun("r1", 10.0), makeRun("r2", 11.0))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(80, result.overallScore)
    }

    // --- CV < 20% -> score 55 ---

    @Test
    fun analyze_cvUnder20_returnsScore55() {
        // speeds: 10.0 and 12.5 -> mean=11.25, stddev~1.768, CV~15.7%
        val runs = listOf(makeRun("r1", 10.0), makeRun("r2", 12.5))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(55, result.overallScore)
    }

    // --- CV >= 20% -> score 20 ---

    @Test
    fun analyze_cvOver20_returnsScore20() {
        // speeds: 10.0 and 15.0 -> mean=12.5, stddev~3.536, CV~28.3%
        val runs = listOf(makeRun("r1", 10.0), makeRun("r2", 15.0))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(20, result.overallScore)
    }

    @Test
    fun analyze_manyRuns_highVariance_returnsScore20() {
        val runs = (1..20).map { makeRun("r$it", 5.0 + it * 2.0) }
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(20, result.overallScore)
    }

    // --- Best and worst run identification ---

    @Test
    fun analyze_identifiesBestAndWorstRun() {
        val runs = listOf(
            makeRun("slow", 8.0),
            makeRun("mid", 12.0),
            makeRun("fast", 16.0),
        )
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals("fast", result.bestRunId)
        assertEquals("slow", result.worstRunId)
    }

    @Test
    fun analyze_twoRuns_bestAndWorst() {
        val runs = listOf(makeRun("a", 5.0), makeRun("b", 25.0))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals("b", result.bestRunId)
        assertEquals("a", result.worstRunId)
    }

    // --- Edge case: all same speed (CV = 0) ---

    @Test
    fun analyze_allSameSpeed_returnsScore95() {
        val runs = listOf(
            makeRun("r1", 15.0),
            makeRun("r2", 15.0),
            makeRun("r3", 15.0),
        )
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertEquals(95, result.overallScore)
        assertEquals(0.0, result.speedVariancePct, 0.001)
    }

    // --- Edge case: all zero speed ---

    @Test
    fun analyze_allZeroSpeed_returnsNull() {
        val runs = listOf(makeRun("r1", 0.0), makeRun("r2", 0.0))
        assertNull(ConsistencyAnalyzer.analyze(runs))
    }

    @Test
    fun analyze_speedVariancePct_isPositive() {
        val runs = listOf(makeRun("r1", 10.0), makeRun("r2", 12.0))
        val result = ConsistencyAnalyzer.analyze(runs)!!
        assertTrue(result.speedVariancePct > 0.0)
    }
}
