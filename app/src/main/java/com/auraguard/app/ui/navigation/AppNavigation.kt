package com.auraguard.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auraguard.app.data.model.IncidentType
import com.auraguard.app.ui.acoustic.AcousticLabScreen
import com.auraguard.app.ui.components.DisarmCountdownDialog
import com.auraguard.app.ui.contacts.ContactsScreen
import com.auraguard.app.ui.dashboard.DashboardScreen
import com.auraguard.app.ui.history.IncidentHistoryScreen
import com.auraguard.app.ui.sensor.SensorStudioScreen
import com.auraguard.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Shield)
    data object AcousticLab : Screen("acoustic_lab", "Acoustic AI", Icons.Default.GraphicEq)
    data object SensorStudio : Screen("sensor_studio", "Sensors", Icons.Default.Sensors)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Contacts : Screen("contacts", "Contacts", Icons.Default.People)
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val incidents by viewModel.incidents.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    val liveSpectrum by viewModel.liveAudioSpectrum.collectAsState()
    val liveDecibels by viewModel.liveDecibels.collectAsState()
    val liveClassificationMap by viewModel.liveClassificationMap.collectAsState()
    val liveTelemetryHistory by viewModel.liveTelemetryHistory.collectAsState()

    val screens = listOf(
        Screen.Dashboard,
        Screen.AcousticLab,
        Screen.SensorStudio,
        Screen.History,
        Screen.Contacts,
    )

    // Root-level global emergency countdown overlay across ALL screens
    if (uiState.isCountdownActive) {
        DisarmCountdownDialog(
            secondsLeft = uiState.countdownSecondsLeft,
            incidentType = uiState.activeIncidentType,
            onDisarm = { viewModel.disarmEmergency() },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(220)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(220)) },
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    uiState = uiState,
                    onToggleService = { viewModel.toggleTelemetryService() },
                    onTriggerPanic = { viewModel.triggerManualPanic() },
                    onDisarmEmergency = { viewModel.disarmEmergency() },
                    onSimulateFall = { viewModel.triggerSimulatedEvent(IncidentType.HIGH_IMPACT_FALL, 0.96f) },
                    onSimulateAcoustic = { viewModel.triggerSimulatedEvent(IncidentType.ACOUSTIC_DISTRESS, 0.88f) },
                )
            }
            composable(Screen.AcousticLab.route) {
                AcousticLabScreen(
                    isServiceRunning = uiState.isServiceRunning,
                    onToggleService = { viewModel.toggleTelemetryService() },
                    initialMicSensitivity = viewModel.getCalibrationPreferences().micSensitivity,
                    onSensitivityChange = { sensitivity ->
                        val prefs = viewModel.getCalibrationPreferences()
                        viewModel.saveCalibration(
                            prefs.fallThresholdG,
                            prefs.dropDurationMs,
                            prefs.struggleSensitivity,
                            sensitivity,
                        )
                    },
                    liveAudioSpectrum = liveSpectrum,
                    liveDecibels = liveDecibels,
                    liveClassificationMap = liveClassificationMap,
                )
            }
            composable(Screen.SensorStudio.route) {
                SensorStudioScreen(
                    prefs = viewModel.getCalibrationPreferences(),
                    telemetryHistory = liveTelemetryHistory,
                    onSaveCalibration = { fallG, dropMs ->
                        val prefs = viewModel.getCalibrationPreferences()
                        viewModel.saveCalibration(
                            fallG,
                            dropMs,
                            prefs.struggleSensitivity,
                            prefs.micSensitivity,
                        )
                    },
                )
            }
            composable(Screen.History.route) {
                IncidentHistoryScreen(
                    incidents = incidents,
                    onToggleResolved = { incident -> viewModel.toggleIncidentResolved(incident) },
                    onDeleteIncident = { incident -> viewModel.deleteIncident(incident) },
                )
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    contacts = contacts,
                    selectedSubId = viewModel.getPreferredSubId(),
                    onSelectSubId = { subId ->
                        viewModel.setPreferredSubId(subId)
                    },
                    onAddContact = { name, phone, isPrimary ->
                        viewModel.addContact(name, phone, isPrimary)
                    },
                    onSetPrimaryContact = { contact ->
                        viewModel.setPrimaryContact(contact)
                    },
                    onRemoveContact = { contact ->
                        viewModel.removeContact(contact)
                    },
                )
            }
        }
    }
}
