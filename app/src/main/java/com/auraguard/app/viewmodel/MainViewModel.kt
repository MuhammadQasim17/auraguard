package com.auraguard.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auraguard.app.data.local.AuraGuardDatabase
import com.auraguard.app.data.local.entity.EmergencyContactEntity
import com.auraguard.app.data.local.entity.IncidentEntity
import com.auraguard.app.data.repository.IncidentRepositoryImpl
import com.auraguard.app.service.GracePeriodState
import com.auraguard.app.service.TelemetryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShieldStatus {
    ARMED,
    MONITORING,
    ALERT,
    DISARMED,
}

data class DashboardUiState(
    val shieldStatus: ShieldStatus = ShieldStatus.ARMED,
    val isServiceRunning: Boolean = true,
    val liveSensorMagnitude: Float = 9.81f,
    val liveDecibels: Float = 42.0f,
    val countdownSecondsLeft: Int = 15,
    val isCountdownActive: Boolean = false,
    val activeIncidentType: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AuraGuardDatabase.getDatabase(application)
    private val incidentRepository = IncidentRepositoryImpl(database.incidentDao())
    private val emergencyContactDao = database.emergencyContactDao()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val incidents: StateFlow<List<IncidentEntity>> = incidentRepository.getAllIncidents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val contacts: StateFlow<List<EmergencyContactEntity>> = emergencyContactDao.getAllContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val liveAudioSpectrum: StateFlow<List<Float>> = com.auraguard.app.ml.AudioClassifierEngineImpl.globalSpectrum
    val liveDecibels: StateFlow<Float> = com.auraguard.app.ml.AudioClassifierEngineImpl.globalDecibels
    val liveClassificationMap: StateFlow<Map<String, Float>> = com.auraguard.app.ml.AudioClassifierEngineImpl.globalClassificationMap
    val liveTelemetryHistory: StateFlow<List<Triple<Float, Float, Float>>> = com.auraguard.app.sensor.SensorAnomalyDetector.globalTelemetryHistory

    init {
        viewModelScope.launch {
            com.auraguard.app.service.GracePeriodManager.globalState.collect { state ->
                handleGracePeriodState(state)
            }
        }
    }

    private val calibrationPrefs = com.auraguard.app.data.local.CalibrationPreferencesManager(application)

    fun toggleTelemetryService() {
        val currentRunning = _uiState.value.isServiceRunning
        if (currentRunning) {
            TelemetryService.stopService(getApplication())
            _uiState.value = _uiState.value.copy(
                isServiceRunning = false,
                shieldStatus = ShieldStatus.DISARMED,
            )
        } else {
            TelemetryService.startService(getApplication())
            _uiState.value = _uiState.value.copy(
                isServiceRunning = true,
                shieldStatus = ShieldStatus.ARMED,
            )
        }
    }

    fun triggerManualPanic() {
        triggerSimulatedEvent(com.auraguard.app.data.model.IncidentType.MANUAL_SOS_PANIC, 1.0f)
    }

    fun triggerSimulatedEvent(type: com.auraguard.app.data.model.IncidentType, confidence: Float) {
        TelemetryService.simulateAnomaly(getApplication(), type.rawValue, confidence)
    }

    fun simulateAnomaly(incidentType: String, confidence: Float = 0.95f) {
        TelemetryService.simulateAnomaly(getApplication(), incidentType, confidence)
    }

    fun handleGracePeriodState(state: GracePeriodState) {
        when (state) {
            is GracePeriodState.CountdownTick -> {
                _uiState.value = _uiState.value.copy(
                    isCountdownActive = true,
                    countdownSecondsLeft = state.secondsLeft,
                    activeIncidentType = state.incidentType,
                    shieldStatus = ShieldStatus.ALERT,
                )
            }
            is GracePeriodState.CountdownFinished -> {
                _uiState.value = _uiState.value.copy(
                    isCountdownActive = false,
                    shieldStatus = ShieldStatus.ARMED,
                )
            }
            GracePeriodState.Disarmed, GracePeriodState.Idle -> {
                _uiState.value = _uiState.value.copy(
                    isCountdownActive = false,
                    shieldStatus = ShieldStatus.ARMED,
                )
            }
        }
    }

    fun disarmEmergency() {
        TelemetryService.disarmAlarm(getApplication())
        _uiState.value = _uiState.value.copy(
            isCountdownActive = false,
            activeIncidentType = null,
            shieldStatus = ShieldStatus.ARMED,
        )
    }

    fun addContact(name: String, phoneNumber: String, isPrimary: Boolean = false) {
        viewModelScope.launch {
            val contact = EmergencyContactEntity(
                name = name,
                phoneNumber = phoneNumber,
                isPrimary = isPrimary,
            )
            val contactId = emergencyContactDao.insertContactWithPrimaryCheck(contact)
            if (isPrimary) {
                emergencyContactDao.setSinglePrimaryContact(contactId)
            }
        }
    }

    fun setPrimaryContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            emergencyContactDao.setSinglePrimaryContact(contact.id)
        }
    }

    fun removeContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            emergencyContactDao.deleteContact(contact)
        }
    }

    fun toggleIncidentResolved(incident: IncidentEntity) {
        viewModelScope.launch {
            incidentRepository.setIncidentResolvedStatus(incident.id, !incident.resolved)
        }
    }

    fun deleteIncident(incident: IncidentEntity) {
        viewModelScope.launch {
            incidentRepository.deleteIncident(incident)
        }
    }

    fun resolveIncident(incidentId: Long) {
        viewModelScope.launch {
            incidentRepository.resolveIncident(incidentId)
        }
    }

    fun saveCalibration(fallG: Float, dropMs: Int, struggleSens: Float, micSens: Float) {
        calibrationPrefs.fallThresholdG = fallG
        calibrationPrefs.dropDurationMs = dropMs
        calibrationPrefs.struggleSensitivity = struggleSens
        calibrationPrefs.micSensitivity = micSens
    }

    fun setPreferredSimSlot(slotIndex: Int) {
        calibrationPrefs.preferredSimSlot = slotIndex
    }

    fun getPreferredSimSlot(): Int {
        return calibrationPrefs.preferredSimSlot
    }

    fun setPreferredSubId(subId: Int) {
        calibrationPrefs.preferredSubId = subId
    }

    fun getPreferredSubId(): Int {
        return calibrationPrefs.preferredSubId
    }

    fun getCalibrationPreferences(): com.auraguard.app.data.local.CalibrationPreferencesManager {
        return calibrationPrefs
    }
}
