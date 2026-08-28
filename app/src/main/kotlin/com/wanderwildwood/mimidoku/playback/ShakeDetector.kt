package com.wanderwildwood.mimidoku.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Notices the device being shaken, and nothing else.
 *
 * This exists for one job: a reader who is falling asleep can call off the sleep timer by picking
 * the device up and shaking it, without finding a button in the dark. It only listens while the
 * timer is running, because an accelerometer left registered is a battery drain for nothing.
 */
class ShakeDetector(
    context: Context,
    private val threshold: Float,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeAt = 0L

    fun start() {
        val sensor = accelerometer ?: return
        // The slowest rate Android offers. A shake lasts a good fraction of a second, so there is
        // nothing to gain from being told about it two hundred times.
        sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        // Gravity is always in there, so the resting magnitude is g rather than zero; what matters
        // is how far past g the device is being thrown.
        val force = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (force < threshold) return

        // One shake is many events. Anything within a second of the last one is the same shake.
        val now = System.currentTimeMillis()
        if (now - lastShakeAt < QUIET_MS) return
        lastShakeAt = now
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val QUIET_MS = 1_000L
    }
}
