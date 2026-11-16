package com.auraguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: String,
    val confidenceScore: Float,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val resolved: Boolean = false,
    val smsSentCount: Int = 0,
    val callInitiated: Boolean = false,
    val deliveryStatusText: String? = null,
)
