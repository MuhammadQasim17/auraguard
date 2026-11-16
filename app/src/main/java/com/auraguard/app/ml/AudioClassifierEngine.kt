package com.auraguard.app.ml

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioClassifierEngine {
    val acousticEvents: SharedFlow<AcousticThreatEvent>
    val liveAudioSpectrum: StateFlow<List<Float>>
    val liveDecibels: StateFlow<Float>
    val liveClassificationMap: StateFlow<Map<String, Float>>

    fun startListening()
    fun stopListening()
}
