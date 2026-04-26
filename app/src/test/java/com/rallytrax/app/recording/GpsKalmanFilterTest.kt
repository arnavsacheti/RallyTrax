package com.rallytrax.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsKalmanFilterTest {

    @Test
    fun `outlier fix is rejected and does not corrupt state`() {
        val filter = GpsKalmanFilter()
        var t = 0L

        // Steady drive eastward at ~10 m/s (≈36 km/h) with good GPS
        filter.update(lat = 47.0, lon = 8.0, accuracyM = 5f, speedMps = 10.0, bearingDeg = 90.0, timestampMs = t)
        repeat(20) {
            t += 1000
            // 10 m/s east at lat 47° ≈ 10 / (111320·cos47°) deg/s ≈ 1.317e-4 deg/s
            val lon = 8.0 + (it + 1) * 1.317e-4
            filter.update(lat = 47.0, lon = lon, accuracyM = 5f, speedMps = 10.0, bearingDeg = 90.0, timestampMs = t)
        }
        val before = filter.currentEstimate()

        // Inject a wild outlier: ~50 km north (tunnel multipath / ferry-class jump)
        // with the same reported accuracy. Should be gated out.
        t += 1000
        val outlier = filter.update(
            lat = 47.5,
            lon = before.lon,
            accuracyM = 5f,
            speedMps = 10.0,
            bearingDeg = 90.0,
            timestampMs = t,
        )

        assertTrue("outlier should be rejected", filter.rejectedOutlierCount >= 1)
        // Estimate should still track the steady eastbound trajectory, not jump 50 km north.
        assertEquals(47.0, outlier.lat, 0.01)
    }

    @Test
    fun `inlier fix within noise envelope is accepted`() {
        val filter = GpsKalmanFilter()
        var t = 0L
        filter.update(lat = 47.0, lon = 8.0, accuracyM = 10f, speedMps = 0.0, bearingDeg = 0.0, timestampMs = t)
        repeat(5) {
            t += 1000
            // Stationary fix with ~10 m of GPS noise — well within gate.
            val jitter = if (it % 2 == 0) 5e-5 else -5e-5
            filter.update(lat = 47.0 + jitter, lon = 8.0, accuracyM = 10f, timestampMs = t)
        }
        assertEquals(0, filter.rejectedOutlierCount)
    }

    @Test
    fun `covariance stays symmetric across many updates (Joseph form)`() {
        val filter = GpsKalmanFilter()
        val pField = GpsKalmanFilter::class.java.getDeclaredField("P").apply { isAccessible = true }

        var t = 0L
        filter.update(lat = 47.0, lon = 8.0, accuracyM = 5f, speedMps = 10.0, bearingDeg = 90.0, timestampMs = t)

        var worstAsymmetry = 0.0
        repeat(2_000) { i ->
            t += 200
            // Driving east at ~10 m/s with mild Gaussian-ish jitter.
            val lon = 8.0 + (i + 1) * 200.0 / 1000.0 * 1.317e-4
            val jitter = ((i * 31 % 7) - 3) * 1e-6
            filter.update(
                lat = 47.0 + jitter,
                lon = lon + jitter,
                accuracyM = 5f,
                speedMps = 10.0,
                bearingDeg = 90.0,
                timestampMs = t,
            )
            val p = pField.get(filter) as DoubleArray
            for (a in 0..3) for (b in (a + 1)..3) {
                val asym = Math.abs(p[a * 4 + b] - p[b * 4 + a])
                val mag = Math.abs(p[a * 4 + b]) + Math.abs(p[b * 4 + a]) + 1e-30
                worstAsymmetry = maxOf(worstAsymmetry, asym / mag)
            }
        }
        // Joseph form should keep relative asymmetry at floating-point noise.
        // The naïve (I-KH)P form drifts by orders of magnitude over thousands
        // of updates.
        assertTrue("P drifted from symmetric (relative asym=$worstAsymmetry)", worstAsymmetry < 1e-9)
    }

    @Test
    fun `longitude velocity covariance grows linearly with dt (regression for q2Lon dt squared bug)`() {
        // Before the fix, P[3,3] grew quadratically with dt during predict,
        // diverging from P[2,2]. After the fix they should grow at the same
        // physical rate (modulo the cos(lat) scaling between lat and lon
        // metres-per-degree), which means the *ratio* stays roughly constant.
        val filter = GpsKalmanFilter()
        filter.update(lat = 47.0, lon = 8.0, accuracyM = 5f, speedMps = 0.0, bearingDeg = 0.0, timestampMs = 0L)

        val lookP = GpsKalmanFilter::class.java.getDeclaredField("P").apply { isAccessible = true }

        filter.predict(1_000L)
        val pAfter1s = (lookP.get(filter) as DoubleArray).copyOf()
        val ratio1 = pAfter1s[3 * 4 + 3] / pAfter1s[2 * 4 + 2]

        filter.predict(11_000L) // +10 s
        val pAfter11s = lookP.get(filter) as DoubleArray
        val ratio11 = pAfter11s[3 * 4 + 3] / pAfter11s[2 * 4 + 2]

        // With the bug, ratio11/ratio1 was ~10× (extra dt factor on lon side).
        // After the fix the ratio is governed by cos²(lat) and stays stable.
        val drift = ratio11 / ratio1
        assertTrue("vLon vs vLat covariance ratio should not blow up with dt (was $drift)", drift < 2.0)
    }
}
