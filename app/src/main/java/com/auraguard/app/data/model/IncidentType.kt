package com.auraguard.app.data.model

enum class IncidentType(val rawValue: String) {
    HIGH_IMPACT_FALL("HIGH_IMPACT_FALL"),
    ACOUSTIC_DISTRESS("ACOUSTIC_DISTRESS"),
    MANUAL_SOS_PANIC("MANUAL_SOS_PANIC"),
    AGGRESSIVE_SHAKE_STRUGGLE("AGGRESSIVE_SHAKE_STRUGGLE");

    companion object {
        fun fromRaw(value: String): IncidentType {
            return entries.find { it.rawValue.equals(value, ignoreCase = true) } ?: HIGH_IMPACT_FALL
        }
    }
}
