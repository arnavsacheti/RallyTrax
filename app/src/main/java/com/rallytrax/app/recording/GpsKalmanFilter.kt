package com.rallytrax.app.recording

import kotlin.math.abs
import kotlin.math.cos

/**
 * Extended Kalman filter for GPS tracking with a constant-velocity motion model.
 *
 * State vector: [lat, lon, vLat, vLon] where velocities are in degrees/second.
 * Works in a local linearized coordinate frame, converting GPS accuracy (metres)
 * to degree-space internally.
 *
 * Benefits over raw GPS:
 * - Smooths out GPS jitter/noise
 * - Predicts position between GPS fixes for higher-rate UI updates
 * - Adapts measurement trust based on reported GPS accuracy
 * - Rejects outlier jumps gracefully via increased uncertainty
 */
class GpsKalmanFilter {

    // State: [lat, lon, vLat (deg/s), vLon (deg/s)]
    private var x = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

    // Error covariance matrix (4x4, stored as flat array row-major)
    private var P = DoubleArray(16)

    // Process noise (tuned for vehicle dynamics)
    private var lastTimestampMs: Long = 0L
    private var initialized = false

    /**
     * Chi-squared 99.9% threshold for 2 dof. Position innovations with
     * Mahalanobis² above this are treated as outliers (tunnels, ferries, hot
     * starts, multipath jumps) and skipped — predict still advanced the state
     * and inflated covariance, so the next inlier fix snaps us back quickly.
     */
    private val innovationGateChiSq2dof = 13.82

    var rejectedOutlierCount: Int = 0
        private set

    /** Metres per degree latitude (approximately constant) */
    private val metersPerDegLat = 111_320.0

    /** Metres per degree longitude at current latitude */
    private fun metersPerDegLon(latDeg: Double): Double {
        return 111_320.0 * cos(Math.toRadians(latDeg))
    }

    /** Process noise spectral density — acceleration variance in deg²/s⁴ */
    // ~3 m/s² typical vehicle acceleration, converted to degrees
    private val processNoiseAccelMps2 = 3.0

    /**
     * Floor below which the 2x2 innovation covariance determinant is treated
     * as singular. Variances live in deg² with sigma≈1e-4 deg for a 10m fix,
     * so det≈1e-16 in normal operation; 1e-20 catches truly degenerate cases
     * without falsely rejecting tight, well-conditioned fixes.
     */
    private val singularDetThreshold = 1e-20

    /**
     * Initialize the filter with the first GPS fix.
     */
    fun init(lat: Double, lon: Double, accuracyM: Float, timestampMs: Long) {
        x[0] = lat
        x[1] = lon
        x[2] = 0.0
        x[3] = 0.0

        val sigmaLat = accuracyM / metersPerDegLat
        val sigmaLon = accuracyM / metersPerDegLon(lat).coerceAtLeast(1.0)

        // Initialize P as diagonal
        P = DoubleArray(16)
        P[idx(0, 0)] = sigmaLat * sigmaLat
        P[idx(1, 1)] = sigmaLon * sigmaLon
        // High initial velocity uncertainty (~30 m/s in deg/s)
        P[idx(2, 2)] = (30.0 / metersPerDegLat) * (30.0 / metersPerDegLat)
        P[idx(3, 3)] = (30.0 / metersPerDegLon(lat).coerceAtLeast(1.0)).let { it * it }

        lastTimestampMs = timestampMs
        initialized = true
    }

    val isInitialized: Boolean get() = initialized

    /**
     * Predict step: propagate the state forward by dt seconds using constant-velocity model.
     * Call this at a high rate (e.g. every 50ms) for smooth interpolation.
     */
    fun predict(timestampMs: Long): FilteredLocation {
        if (!initialized) return FilteredLocation(0.0, 0.0, 0.0, 0.0)

        val dt = ((timestampMs - lastTimestampMs).coerceAtLeast(0) / 1000.0)
            .coerceAtMost(5.0) // Cap to prevent explosion after long gaps
        lastTimestampMs = timestampMs

        // State prediction: x_new = F * x
        x[0] += x[2] * dt
        x[1] += x[3] * dt

        // Covariance prediction: P_new = F * P * F^T + Q
        // F = [[1, 0, dt, 0], [0, 1, 0, dt], [0, 0, 1, 0], [0, 0, 0, 1]]
        val newP = DoubleArray(16)

        // Compute F * P * F^T + Q efficiently
        // Row 0: lat
        newP[idx(0, 0)] = P[idx(0, 0)] + dt * (P[idx(2, 0)] + P[idx(0, 2)]) + dt * dt * P[idx(2, 2)]
        newP[idx(0, 1)] = P[idx(0, 1)] + dt * (P[idx(2, 1)] + P[idx(0, 3)]) + dt * dt * P[idx(2, 3)]
        newP[idx(0, 2)] = P[idx(0, 2)] + dt * P[idx(2, 2)]
        newP[idx(0, 3)] = P[idx(0, 3)] + dt * P[idx(2, 3)]

        // Row 1: lon
        newP[idx(1, 0)] = P[idx(1, 0)] + dt * (P[idx(3, 0)] + P[idx(1, 2)]) + dt * dt * P[idx(3, 2)]
        newP[idx(1, 1)] = P[idx(1, 1)] + dt * (P[idx(3, 1)] + P[idx(1, 3)]) + dt * dt * P[idx(3, 3)]
        newP[idx(1, 2)] = P[idx(1, 2)] + dt * P[idx(3, 2)]
        newP[idx(1, 3)] = P[idx(1, 3)] + dt * P[idx(3, 3)]

        // Row 2: vLat
        newP[idx(2, 0)] = P[idx(2, 0)] + dt * P[idx(2, 2)]
        newP[idx(2, 1)] = P[idx(2, 1)] + dt * P[idx(2, 3)]
        newP[idx(2, 2)] = P[idx(2, 2)]
        newP[idx(2, 3)] = P[idx(2, 3)]

        // Row 3: vLon
        newP[idx(3, 0)] = P[idx(3, 0)] + dt * P[idx(3, 2)]
        newP[idx(3, 1)] = P[idx(3, 1)] + dt * P[idx(3, 3)]
        newP[idx(3, 2)] = P[idx(3, 2)]
        newP[idx(3, 3)] = P[idx(3, 3)]

        // Add process noise Q (continuous white noise acceleration model).
        // NOTE: noise is added independently along lat and lon, treating the
        // two axes as uncoupled. A vehicle actually accelerates along its
        // bearing, so the "true" Q has off-diagonal blocks coupling vLat/vLon
        // by sin(bearing)·cos(bearing). For typical driving the diagonal
        // approximation is fine — the velocity update step folds in the
        // bearing-aware speed measurement, which restores most of the lost
        // information. See issue #100 for a future bearing-coupled Q rework
        // and for the empirical test track that should validate it.
        val qLat = processNoiseAccelMps2 / metersPerDegLat
        val mpdLon = metersPerDegLon(x[0]).coerceAtLeast(1.0)
        val qLon = processNoiseAccelMps2 / mpdLon
        val q2Lat = qLat * qLat
        val q2Lon = qLon * qLon

        newP[idx(0, 0)] += q2Lat * dt * dt * dt / 3.0
        newP[idx(0, 2)] += q2Lat * dt * dt / 2.0
        newP[idx(2, 0)] += q2Lat * dt * dt / 2.0
        newP[idx(2, 2)] += q2Lat * dt

        newP[idx(1, 1)] += q2Lon * dt * dt * dt / 3.0
        newP[idx(1, 3)] += q2Lon * dt * dt / 2.0
        newP[idx(3, 1)] += q2Lon * dt * dt / 2.0
        newP[idx(3, 3)] += q2Lon * dt

        System.arraycopy(newP, 0, P, 0, 16)

        return currentEstimate()
    }

    /**
     * Update step: incorporate a new GPS measurement.
     * @param lat measured latitude
     * @param lon measured longitude
     * @param accuracyM reported GPS horizontal accuracy in metres
     * @param speedMps reported speed (m/s), used to constrain velocity if available
     * @param bearingDeg reported bearing (degrees), used with speed for velocity
     * @param timestampMs measurement timestamp
     */
    fun update(
        lat: Double,
        lon: Double,
        accuracyM: Float,
        speedMps: Double? = null,
        bearingDeg: Double? = null,
        timestampMs: Long,
    ): FilteredLocation {
        if (!initialized) {
            init(lat, lon, accuracyM, timestampMs)
            return currentEstimate()
        }

        // First predict to the measurement time
        predict(timestampMs)

        // Measurement noise from GPS accuracy
        val rLat = (accuracyM / metersPerDegLat).let { it * it }
        val mpdLon = metersPerDegLon(x[0]).coerceAtLeast(1.0)
        val rLon = (accuracyM / mpdLon).let { it * it }

        // Innovation (measurement residual)
        val yLat = lat - x[0]
        val yLon = lon - x[1]

        // Innovation covariance: S = H * P * H^T + R  (H = [[1,0,0,0],[0,1,0,0]])
        val s00 = P[idx(0, 0)] + rLat
        val s01 = P[idx(0, 1)]
        val s10 = P[idx(1, 0)]
        val s11 = P[idx(1, 1)] + rLon

        // Invert 2x2 S matrix. Use an epsilon floor — exact-equality on a
        // float det effectively never triggers, which would let near-singular
        // matrices slip through and explode 1/det.
        val det = s00 * s11 - s01 * s10
        if (abs(det) < singularDetThreshold) return currentEstimate()
        val invDet = 1.0 / det
        val si00 = s11 * invDet
        val si01 = -s01 * invDet
        val si10 = -s10 * invDet
        val si11 = s00 * invDet

        // Innovation gating: reject outlier fixes whose Mahalanobis² exceeds
        // the 99.9% χ² bound. S already includes R, so this scales naturally
        // with reported GPS accuracy — a 100 m-accuracy fix has to be that
        // much further off to be rejected.
        val mahalanobisSq =
            yLat * yLat * si00 + 2.0 * yLat * yLon * si01 + yLon * yLon * si11
        if (mahalanobisSq > innovationGateChiSq2dof) {
            rejectedOutlierCount++
            return currentEstimate()
        }

        // Kalman gain: K = P * H^T * S^-1 (4x2 matrix)
        val k = DoubleArray(8)
        for (i in 0..3) {
            val ph0 = P[idx(i, 0)]
            val ph1 = P[idx(i, 1)]
            k[i * 2 + 0] = ph0 * si00 + ph1 * si10
            k[i * 2 + 1] = ph0 * si01 + ph1 * si11
        }

        // State update: x = x + K * y
        for (i in 0..3) {
            x[i] += k[i * 2 + 0] * yLat + k[i * 2 + 1] * yLon
        }

        // Joseph-form covariance update. Position measurement observes states
        // 0 (lat) and 1 (lon); R = diag(rLat, rLon).
        josephUpdate(k, c0 = 0, c1 = 1, r0 = rLat, r1 = rLon)

        // If speed+bearing available, also fold in velocity observation
        if (speedMps != null && bearingDeg != null && speedMps > 0.5) {
            updateVelocity(speedMps, bearingDeg)
        }

        return currentEstimate()
    }

    /**
     * Optional velocity update using reported speed and bearing.
     * Treats [vLat, vLon] as a separate measurement.
     */
    private fun updateVelocity(speedMps: Double, bearingDeg: Double) {
        val bearingRad = Math.toRadians(bearingDeg)
        val vNorth = speedMps * cos(bearingRad) // m/s north
        val vEast = speedMps * kotlin.math.sin(bearingRad) // m/s east

        val measVLat = vNorth / metersPerDegLat
        val mpdLon = metersPerDegLon(x[0]).coerceAtLeast(1.0)
        val measVLon = vEast / mpdLon

        // Velocity measurement noise (~2 m/s accuracy)
        val rVLat = (2.0 / metersPerDegLat).let { it * it }
        val rVLon = (2.0 / mpdLon).let { it * it }

        // H_v = [[0,0,1,0],[0,0,0,1]], observe states 2 and 3
        val yVLat = measVLat - x[2]
        val yVLon = measVLon - x[3]

        val s00 = P[idx(2, 2)] + rVLat
        val s01 = P[idx(2, 3)]
        val s10 = P[idx(3, 2)]
        val s11 = P[idx(3, 3)] + rVLon

        val det = s00 * s11 - s01 * s10
        if (abs(det) < singularDetThreshold) return
        val invDet = 1.0 / det
        val si00 = s11 * invDet
        val si01 = -s01 * invDet
        val si10 = -s10 * invDet
        val si11 = s00 * invDet

        val k = DoubleArray(8)
        for (i in 0..3) {
            val ph0 = P[idx(i, 2)]
            val ph1 = P[idx(i, 3)]
            k[i * 2 + 0] = ph0 * si00 + ph1 * si10
            k[i * 2 + 1] = ph0 * si01 + ph1 * si11
        }

        for (i in 0..3) {
            x[i] += k[i * 2 + 0] * yVLat + k[i * 2 + 1] * yVLon
        }

        // Joseph-form covariance update. Velocity measurement observes states
        // 2 (vLat) and 3 (vLon); R = diag(rVLat, rVLon).
        josephUpdate(k, c0 = 2, c1 = 3, r0 = rVLat, r1 = rVLon)
    }

    /**
     * Joseph-form covariance update for a 2-row measurement matrix H that
     * selects two state indices [c0, c1] (i.e. H[0,c0]=H[1,c1]=1, rest 0)
     * with diagonal measurement noise R = diag(r0, r1).
     *
     *   P ← (I − K·H)·P·(I − K·H)ᵀ + K·R·Kᵀ
     *
     * Symmetric and positive-semidefinite-preserving across many updates,
     * unlike the simpler (I − K·H)·P form which can drift on long sessions
     * and produce a non-PSD P that quietly poisons subsequent gains.
     *
     * Sparsity of H lets us skip the explicit matrix multiply: for these
     * selectors,
     *   (I − K·H)[i,m] = δ_{im} − K[i,0]·δ_{m,c0} − K[i,1]·δ_{m,c1}
     * which collapses each Σₘ into three terms instead of four.
     */
    private fun josephUpdate(k: DoubleArray, c0: Int, c1: Int, r0: Double, r1: Double) {
        // Step 1: M = (I − K·H) · P
        val m = DoubleArray(16)
        for (i in 0..3) {
            val ki0 = k[i * 2 + 0]
            val ki1 = k[i * 2 + 1]
            for (j in 0..3) {
                m[idx(i, j)] = P[idx(i, j)] - ki0 * P[idx(c0, j)] - ki1 * P[idx(c1, j)]
            }
        }
        // Step 2: P_new = M · (I − K·H)ᵀ + K·R·Kᵀ
        val newP = DoubleArray(16)
        for (i in 0..3) {
            val ki0 = k[i * 2 + 0]
            val ki1 = k[i * 2 + 1]
            for (j in 0..3) {
                val kj0 = k[j * 2 + 0]
                val kj1 = k[j * 2 + 1]
                newP[idx(i, j)] =
                    m[idx(i, j)] -
                        kj0 * m[idx(i, c0)] -
                        kj1 * m[idx(i, c1)] +
                        ki0 * r0 * kj0 +
                        ki1 * r1 * kj1
            }
        }
        System.arraycopy(newP, 0, P, 0, 16)
    }

    /** Current filtered estimate. */
    fun currentEstimate(): FilteredLocation {
        val mpdLon = metersPerDegLon(x[0]).coerceAtLeast(1.0)
        return FilteredLocation(
            lat = x[0],
            lon = x[1],
            speedMps = kotlin.math.sqrt(
                (x[2] * metersPerDegLat) * (x[2] * metersPerDegLat) +
                    (x[3] * mpdLon) * (x[3] * mpdLon),
            ),
            bearingDeg = (Math.toDegrees(
                kotlin.math.atan2(x[3] * mpdLon, x[2] * metersPerDegLat),
            ) + 360.0) % 360.0,
        )
    }

    /** Reset the filter state (e.g. after a pause/resume). */
    fun reset() {
        initialized = false
        x = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        P = DoubleArray(16)
        lastTimestampMs = 0L
        rejectedOutlierCount = 0
    }

    /** Row-major index into 4x4 matrix stored as flat array. */
    private fun idx(row: Int, col: Int) = row * 4 + col
}

/** Output of the Kalman filter. */
data class FilteredLocation(
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val bearingDeg: Double,
)
