package com.auraguard.app.ml

data class AcousticThreatEvent(
    val label: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
)
