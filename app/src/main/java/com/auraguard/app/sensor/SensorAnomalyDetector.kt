package com.auraguard.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.auraguard.app.data.local.CalibrationPreferencesManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class SensorAnomalyDetector(
    context: Context,
) : SensorEventListener {

    private val calibrationPrefs = CalibrationPreferencesManager(context)

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _telemetryEvents = MutableSharedFlow<TelemetryEvent>(extraBufferCapacity = 64)
    val telemetryEvents: SharedFlow<TelemetryEvent> = _telemetryEvents.asSharedFlow()

    private val _liveTelemetryHistory = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
    val liveTelemetryHistory: StateFlow<List<Triple<Float, Float, Float>>> = _liveTelemetryHistory.asStateFlow()

    private var freeFallStartTime: Long = 0L
    private var isInFreeFall: Boolean = false
    private var lastFreeFallEndTime: Long = 0L

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Record rolling telemetry point
        val newPoint = Triple(x, y, z)
        val currentList = _globalTelemetryHistory.value.toMutableList()
        currentList.add(newPoint)
        if (currentList.size > MAX_HISTORY_POINTS) {
            currentList.removeAt(0)
        }
        _globalTelemetryHistory.value = currentList
        _liveTelemetryHistory.value = currentList

        val magnitude = sqrt(((x * x) + (y * y) + (z * z)).toDouble()).toFloat()
        val currentTime = System.currentTimeMillis()

        val fallGThreshold = calibrationPrefs.fallThresholdG // default 3.0f
        val minDropMs = calibrationPrefs.dropDurationMs.toLong().coerceIn(150L, 650L) // default ~275ms

        // Phase 1: Near-zero gravity free-fall (phone is airborne, |a| < 3.8 m/s^2)
        if (magnitude < FREE_FALL_ACCEL_THRESHOLD) {
            if (!isInFreeFall) {
                isInFreeFall = true
                freeFallStartTime = currentTime
            }
        } else {
            val freeFallDuration = if (isInFreeFall) currentTime - freeFallStartTime else 0L
            if (isInFreeFall) {
                lastFreeFallEndTime = currentTime
            }
            isInFreeFall = false

            // Phase 2: High-G impact spike IMMEDIATELY following free-fall
            val timeSinceFreeFall = currentTime - lastFreeFallEndTime
            val effectiveImpactThreshold = (fallGThreshold * 9.81f).coerceIn(24.0f, 60.0f) // ~2.45G - 6.1G

            if (freeFallDuration in minDropMs..800L &&
                timeSinceFreeFall <= POST_IMPACT_MAX_WINDOW_MS &&
                magnitude >= effectiveImpactThreshold
            ) {
                _telemetryEvents.tryEmit(
                    TelemetryEvent.ImpactAnomaly(
                        magnitude = magnitude,
                        timestamp = currentTime,
                    ),
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    companion object {
        private const val FREE_FALL_ACCEL_THRESHOLD = 3.8f // m/s^2 (< 0.38 G)
        private const val POST_IMPACT_MAX_WINDOW_MS = 250L // 250ms window immediately following free-fall
        private const val MAX_HISTORY_POINTS = 60

        private val _globalTelemetryHistory = MutableStateFlow<List<Triple<Float, Float, Float>>>(emptyList())
        val globalTelemetryHistory: StateFlow<List<Triple<Float, Float, Float>>> = _globalTelemetryHistory.asStateFlow()
    }
}
