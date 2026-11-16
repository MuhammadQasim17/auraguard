package com.auraguard.app.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraguard.app.ui.components.DisarmCountdownDialog
import com.auraguard.app.ui.theme.AuraGuardTheme
import com.auraguard.app.viewmodel.DashboardUiState
import com.auraguard.app.viewmodel.ShieldStatus
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState = DashboardUiState(),
    onToggleService: () -> Unit = {},
    onTriggerPanic: () -> Unit = {},
    onDisarmEmergency: () -> Unit = {},
    onSimulateFall: () -> Unit = {},
    onSimulateAcoustic: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AURA GUARD HUD",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Central Shield Status Card
            ShieldStatusCard(
                shieldStatus = uiState.shieldStatus,
                isServiceRunning = uiState.isServiceRunning,
                onToggleService = onToggleService,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Real-time Animated Waveform / Telemetry Graph
            TelemetryWaveformCard()

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Telemetry Metrics Card
            TelemetryMetricsCard(
                sensorMagnitude = uiState.liveSensorMagnitude,
                decibels = uiState.liveDecibels,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Debug Test Harness Card (Dev / Debug Build Only)
            if (com.auraguard.app.BuildConfig.DEBUG) {
                DebugTestHarnessCard(
                    onSimulateFall = onSimulateFall,
                    onSimulateAcoustic = onSimulateAcoustic,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 5. Manual Emergency Panic Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTriggerPanic()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "MANUAL SOS PANIC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ShieldStatusCard(
    shieldStatus: ShieldStatus,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PulseAlpha",
    )

    val shieldColor = when (shieldStatus) {
        ShieldStatus.ARMED, ShieldStatus.MONITORING -> MaterialTheme.colorScheme.primary
        ShieldStatus.ALERT -> MaterialTheme.colorScheme.error
        ShieldStatus.DISARMED -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp),
            ) {
                if (isServiceRunning) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .alpha(pulseAlpha)
                            .background(shieldColor, shape = CircleShape),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(shieldColor.copy(alpha = 0.2f), shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(shieldColor, shape = CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isServiceRunning) "ACTIVE DEFENSE ARMED" else "ACTIVE DEFENSE PAUSED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = shieldColor,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Background Telemetry Engine",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                )
            }
        }
    }
}

@Composable
fun TelemetryWaveformCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "Wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "WavePhase",
    )

    val waveColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "REAL-TIME SENSOR WAVEFORM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
            ) {
                val width = size.width
                val height = size.height
                val midY = height / 2

                val path = Path()
                path.moveTo(0f, midY)

                val points = 50
                for (i in 0..points) {
                    val x = (width / points) * i
                    val angle = (i.toFloat() / points) * (4 * Math.PI).toFloat() + wavePhase
                    val y = midY + (sin(angle.toDouble()).toFloat() * (height / 3f))
                    path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = waveColor,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun TelemetryMetricsCard(
    sensorMagnitude: Float,
    decibels: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MOTION VECTOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.2f m/s²", sensorMagnitude),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ACOUSTIC LEVEL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.1f dB", decibels),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun DebugTestHarnessCard(
    onSimulateFall: () -> Unit,
    onSimulateAcoustic: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "🛠 DEBUG TEST HARNESS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "DEV BUILD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Simulate edge anomaly triggers to test end-to-end pipeline (15s grace period -> vibration -> Room DB record).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSimulateFall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Simulate Fall",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onSimulateAcoustic,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Simulate Acoustic",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    AuraGuardTheme {
        DashboardScreen()
    }
}
