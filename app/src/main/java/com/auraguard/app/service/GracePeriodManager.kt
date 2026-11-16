package com.auraguard.app.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GracePeriodState {
    data object Idle : GracePeriodState
    data class CountdownTick(val secondsLeft: Int, val incidentType: String, val confidence: Float) : GracePeriodState
    data class CountdownFinished(val incidentType: String, val confidence: Float) : GracePeriodState
    data object Disarmed : GracePeriodState
}

class GracePeriodManager(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<GracePeriodState>(GracePeriodState.Idle)
    val state: StateFlow<GracePeriodState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun startGracePeriod(
        incidentType: String,
        confidence: Float,
        durationSeconds: Int = 15,
        onCountdownFinished: suspend (String, Float) -> Unit,
    ) {
        if (_state.value is GracePeriodState.CountdownTick) return

        countdownJob?.cancel()
        countdownJob = scope.launch(Dispatchers.Default) {
            for (seconds in durationSeconds downTo 1) {
                val tickState = GracePeriodState.CountdownTick(
                    secondsLeft = seconds,
                    incidentType = incidentType,
                    confidence = confidence,
                )
                _state.value = tickState
                _globalState.value = tickState
                triggerProgressiveHaptic(seconds)
                delay(1000L)
            }

            val finishedState = GracePeriodState.CountdownFinished(incidentType, confidence)
            _state.value = finishedState
            _globalState.value = finishedState
            onCountdownFinished(incidentType, confidence)
        }
    }

    fun disarm() {
        countdownJob?.cancel()
        countdownJob = null
        cancelVibration()
        _state.value = GracePeriodState.Disarmed
        _globalState.value = GracePeriodState.Disarmed
    }

    private fun triggerProgressiveHaptic(currentSecondsLeft: Int) {
        if (!vibrator.hasVibrator()) return

        val pulseDurationMs = if (currentSecondsLeft <= 5) 300L else 150L
        val amplitude = if (currentSecondsLeft <= 5) 255 else 128
        vibrator.vibrate(VibrationEffect.createOneShot(pulseDurationMs, amplitude))
    }

    private fun cancelVibration() {
        if (vibrator.hasVibrator()) {
            vibrator.cancel()
        }
    }

    companion object {
        private val _globalState = MutableStateFlow<GracePeriodState>(GracePeriodState.Idle)
        val globalState: StateFlow<GracePeriodState> = _globalState.asStateFlow()
    }
}
