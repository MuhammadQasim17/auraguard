package com.auraguard.app.sensor

import com.auraguard.app.data.TelemetryEntity
import kotlinx.coroutines.flow.Flow

interface TelemetryCollector {
    fun startCollecting(): Flow<TelemetryEntity>
    fun stopCollecting()
}
