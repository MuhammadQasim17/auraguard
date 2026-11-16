package com.auraguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraguard.app.data.TelemetryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TelemetryUiState(
    val isMonitoring: Boolean = false,
    val latestTelemetry: TelemetryEntity? = null,
    val history: List<TelemetryEntity> = emptyList(),
)

class MainTelemetryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TelemetryUiState())
    val uiState: StateFlow<TelemetryUiState> = _uiState.asStateFlow()

    fun startMonitoring() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMonitoring = true)
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMonitoring = false)
        }
    }
}
