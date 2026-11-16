package com.auraguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_records")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val decibelValue: Float = 0f,
    val label: String = "Normal",
    val confidence: Float = 0f,
)
