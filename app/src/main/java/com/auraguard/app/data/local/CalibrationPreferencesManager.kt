package com.auraguard.app.data.local

import android.content.Context
import android.content.SharedPreferences

class CalibrationPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auraguard_sensor_prefs", Context.MODE_PRIVATE)

    var fallThresholdG: Float
        get() = prefs.getFloat(KEY_FALL_THRESHOLD_G, 3.0f)
        set(value) = prefs.edit().putFloat(KEY_FALL_THRESHOLD_G, value).apply()

    var dropDurationMs: Int
        get() = prefs.getInt(KEY_DROP_DURATION_MS, 300)
        set(value) = prefs.edit().putInt(KEY_DROP_DURATION_MS, value).apply()

    var struggleSensitivity: Float
        get() = prefs.getFloat(KEY_STRUGGLE_SENSITIVITY, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_STRUGGLE_SENSITIVITY, value).apply()

    var micSensitivity: Float
        get() = prefs.getFloat(KEY_MIC_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_MIC_SENSITIVITY, value).apply()

    var preferredSimSlot: Int
        get() = prefs.getInt(KEY_PREFERRED_SIM_SLOT, 0)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SIM_SLOT, value).apply()

    var preferredSubId: Int
        get() = prefs.getInt(KEY_PREFERRED_SUB_ID, -1)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SUB_ID, value).apply()

    companion object {
        private const val KEY_FALL_THRESHOLD_G = "fall_threshold_g"
        private const val KEY_DROP_DURATION_MS = "drop_duration_ms"
        private const val KEY_STRUGGLE_SENSITIVITY = "struggle_sensitivity"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_PREFERRED_SIM_SLOT = "preferred_sim_slot"
        private const val KEY_PREFERRED_SUB_ID = "preferred_sub_id"
    }
}
