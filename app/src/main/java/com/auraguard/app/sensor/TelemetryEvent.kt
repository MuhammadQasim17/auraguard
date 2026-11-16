package com.auraguard.app.sensor

sealed interface TelemetryEvent {
    data object Normal : TelemetryEvent
    data class ImpactAnomaly(
        val magnitude: Float,
        val timestamp: Long = System.currentTimeMillis(),
    ) : TelemetryEvent
    data class ShakeAnomaly(
        val gyroMagnitude: Float,
        val timestamp: Long = System.currentTimeMillis(),
    ) : TelemetryEvent
}
