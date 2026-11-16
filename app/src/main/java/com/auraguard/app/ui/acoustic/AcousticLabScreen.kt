package com.auraguard.app.ui.acoustic

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcousticLabScreen(
    isServiceRunning: Boolean = true,
    onToggleService: () -> Unit = {},
    initialMicSensitivity: Float = 1.0f,
    onSensitivityChange: (Float) -> Unit = {},
    liveAudioSpectrum: List<Float> = List(24) { 0.05f },
    liveDecibels: Float = 42.0f,
    liveClassificationMap: Map<String, Float> = emptyMap(),
) {
    val context = LocalContext.current
    var micSensitivity by remember { mutableFloatStateOf(initialMicSensitivity) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ACOUSTIC AI LAB",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
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
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Audio Engine Control & Sensitivity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acoustic ML Streaming Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "16kHz PCM Real-time Edge Inference",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    micSensitivity = 1.0f
                                    onSensitivityChange(1.0f)
                                    Toast.makeText(context, "Microphone gain reset to 1.0x", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Microphone Gain",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Switch(
                                checked = isServiceRunning,
                                onCheckedChange = { onToggleService() },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Microphone Gain Sensitivity: ${String.format("%.1fx", micSensitivity)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = micSensitivity,
                        onValueChange = { micSensitivity = it },
                        valueRange = 0.5f..3.0f,
                        steps = 5,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                micSensitivity = 1.0f
                                onSensitivityChange(1.0f)
                                Toast.makeText(context, "Microphone gain reset to factory default (1.0x)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Factory Reset",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }

                        Button(
                            onClick = {
                                onSensitivityChange(micSensitivity)
                                Toast.makeText(context, "Acoustic gain settings saved", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Save Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Real-Time Acoustic Frequency Visualizer
            AcousticFrequencyVisualizerCard(
                micSensitivity = micSensitivity,
                liveAudioSpectrum = liveAudioSpectrum,
                liveDecibels = liveDecibels,
                liveClassificationMap = liveClassificationMap,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Multi-class TFLite/LiteRT Inference Confidence Meters
            AcousticInferenceMetersCard(liveClassificationMap = liveClassificationMap)

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Model Architecture & Edge Security Specs Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MODEL ARCHITECTURE SPECIFICATIONS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Model: yamnet_threat_detector.tflite\n" +
                            "• Sample Rate: 16,000 Hz, 16-bit Mono PCM\n" +
                            "• Memory Footprint: 16 KB Page-Aligned (Android 15+ Compatible)\n" +
                            "• Privacy Guarantee: 100% Zero-Cloud On-Device Execution",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AcousticFrequencyVisualizerCard(
    micSensitivity: Float,
    liveAudioSpectrum: List<Float>,
    liveDecibels: Float,
    liveClassificationMap: Map<String, Float> = emptyMap(),
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    val currentDb = liveDecibels * micSensitivity
    val screamConf = liveClassificationMap["Human Scream / Distress"] ?: 0.02f
    val glassConf = liveClassificationMap["Glass Shatter / Breakage"] ?: 0.01f
    val impactConf = liveClassificationMap["High Decibel Impact"] ?: 0.01f
    val maxThreat = maxOf(screamConf, maxOf(glassConf, impactConf))

    val isTriggerLevel = currentDb >= 74.0f && maxThreat >= 0.70f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LIVE SPECTRUM FREQUENCY ANALYZER",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.1f", currentDb)} dB SPL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isTriggerLevel) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isTriggerLevel) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (isTriggerLevel) "TRIGGER LEVEL" else "NORMAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTriggerLevel) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            ) {
                val width = size.width
                val height = size.height
                val barCount = 24
                val barGap = 8.dp.toPx()
                val totalGapWidth = barGap * (barCount - 1)
                val barWidth = (width - totalGapWidth) / barCount

                val dbScaleFactor = ((currentDb - 30.0f) / 70.0f).coerceIn(0.05f, 1.0f)
                val isWarningZone = currentDb >= 82.0f

                for (i in 0 until barCount) {
                    val x = i * (barWidth + barGap)
                    val rawVal = if (i in liveAudioSpectrum.indices) liveAudioSpectrum[i] else 0.05f
                    val normalizedVal = (rawVal * dbScaleFactor * 1.5f).coerceIn(0.05f, 1.0f)
                    val barHeight = height * normalizedVal

                    val color = if (isWarningZone) errorColor else primaryColor

                    drawRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(x, height - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
        }
    }
}

@Composable
fun AcousticInferenceMetersCard(
    liveClassificationMap: Map<String, Float>,
) {
    val screamConf = liveClassificationMap["Human Scream / Distress"] ?: 0.02f
    val glassConf = liveClassificationMap["Glass Shatter / Breakage"] ?: 0.01f
    val impactConf = liveClassificationMap["High Decibel Impact"] ?: 0.01f
    val ambientConf = liveClassificationMap["Normal Ambient Background"] ?: 0.96f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ON-DEVICE CLASSIFICATION METERS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            AcousticClassMeter(label = "Human Scream / Distress", confidence = screamConf, isThreat = true)
            Spacer(modifier = Modifier.height(8.dp))
            AcousticClassMeter(label = "Glass Shatter / Breakage", confidence = glassConf, isThreat = true)
            Spacer(modifier = Modifier.height(8.dp))
            AcousticClassMeter(label = "High Decibel Impact", confidence = impactConf, isThreat = true)
            Spacer(modifier = Modifier.height(8.dp))
            AcousticClassMeter(label = "Normal Ambient Background", confidence = ambientConf, isThreat = false)
        }
    }
}

@Composable
fun AcousticClassMeter(
    label: String,
    confidence: Float,
    isThreat: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isThreat && confidence > 0.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (isThreat && confidence > 0.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )
    }
}
