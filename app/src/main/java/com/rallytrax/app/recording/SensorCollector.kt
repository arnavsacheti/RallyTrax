package com.rallytrax.app.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Collects phone sensor data (accelerometer, gyroscope, barometer) for
 * Jemba-style inertial pace note generation.
 *
 * Call [start] to begin collecting and [stop] to release listeners.
 * Use [snapshot] to get the latest readings at any time (e.g., on each GPS fix).
 */
class SensorCollector(context: Context) : SensorEventListener {

    data class SensorSnapshot(
        val lateralAccelMps2: Double?, // y-axis linear acceleration
        val verticalAccelMps2: Double?, // z-axis linear acceleration
        val yawRateDegPerS: Double?, // z-axis gyroscope (yaw rate)
        val barometerAltitudeM: Double?, // altitude from pressure sensor
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    // Latest readings (written by sensor thread, read by GPS thread)
    @Volatile private var latestLateralAccel: Double? = null
    @Volatile private var latestVerticalAccel: Double? = null
    @Volatile private var latestYawRate: Double? = null
    @Volatile private var latestPressureHpa: Float? = null

    val isAccelerometerAvailable: Boolean get() = accelerometer != null
    val isGyroscopeAvailable: Boolean get() = gyroscope != null
    val isBarometerAvailable: Boolean get() = barometer != null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        latestLateralAccel = null
        latestVerticalAccel = null
        latestYawRate = null
        latestPressureHpa = null
    }

    /** Returns the most recent sensor readings. Safe to call from any thread. */
    fun snapshot(): SensorSnapshot {
        val altitudeM = latestPressureHpa?.let { pressure ->
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure).toDouble()
        }
        return SensorSnapshot(
            lateralAccelMps2 = latestLateralAccel,
            verticalAccelMps2 = latestVerticalAccel,
            yawRateDegPerS = latestYawRate,
            barometerAltitudeM = altitudeM,
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // x = lateral (side-to-side), y = longitudinal (front-back), z = vertical
                // For a phone mounted in landscape on the dash:
                // We store y as lateral and z as vertical.
                // However, device orientation varies — store raw y and z,
                // and let post-processing handle rotation if needed.
                latestLateralAccel = event.values[1].toDouble()
                latestVerticalAccel = event.values[2].toDouble()
            }
            Sensor.TYPE_GYROSCOPE -> {
                // z-axis rotation = yaw rate (rad/s → deg/s)
                latestYawRate = Math.toDegrees(event.values[2].toDouble())
            }
            Sensor.TYPE_PRESSURE -> {
                latestPressureHpa = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
