package com.auraguard.app.ui.sensor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraguard.app.data.local.CalibrationPreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorStudioScreen(
    prefs: CalibrationPreferencesManager? = null,
    telemetryHistory: List<Triple<Float, Float, Float>> = emptyList(),
    onSaveCalibration: (Float, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    var fallGThreshold by remember { mutableFloatStateOf(prefs?.fallThresholdG ?: 3.5f) }
    var dropDurationMs by remember { mutableIntStateOf(prefs?.dropDurationMs ?: 300) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SENSOR STUDIO & CALIBRATION",
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

            // 1. Dynamic Real-Time 3-Axis Telemetry Canvas Graph
            ThreeAxisTelemetryGraphCard(telemetryHistory = telemetryHistory)

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Sensor Calibration Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ANOMALY SENSITIVITY CALIBRATION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fall G-Force Threshold
                    Text(
                        text = "Impact Fall Sensitivity: ${String.format("%.1f G", fallGThreshold)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Threshold for sudden deceleration impact forces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = fallGThreshold,
                        onValueChange = { fallGThreshold = it },
                        valueRange = 2.0f..6.0f,
                        steps = 8,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Free-Fall Drop Duration
                    Text(
                        text = "Weightless Drop Window: ${dropDurationMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Duration of free-fall near 0 G prior to impact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = dropDurationMs.toFloat(),
                        onValueChange = { dropDurationMs = it.toInt() },
                        valueRange = 100f..800f,
                        steps = 7,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                fallGThreshold = 3.5f
                                dropDurationMs = 275
                                onSaveCalibration(3.5f, 275)
                                Toast.makeText(context, "Reset to factory defaults (3.5G, 275ms)", Toast.LENGTH_SHORT).show()
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
                                onSaveCalibration(fallGThreshold, dropDurationMs)
                                Toast.makeText(context, "Calibration settings saved locally", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThreeAxisTelemetryGraphCard(
    telemetryHistory: List<Triple<Float, Float, Float>>,
) {
    val colorX = Color(0xFFEF5350)
    val colorY = Color(0xFF66BB6A)
    val colorZ = Color(0xFF42A5F5)

    val lastX = telemetryHistory.lastOrNull()?.first ?: 0.0f
    val lastY = telemetryHistory.lastOrNull()?.second ?: 0.0f
    val lastZ = telemetryHistory.lastOrNull()?.third ?: 9.81f

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
                    text = "3-AXIS TELEMETRY STREAM",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AxisLegendItem(label = "X", color = colorX)
                    AxisLegendItem(label = "Y", color = colorY)
                    AxisLegendItem(label = "Z", color = colorZ)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = String.format("X: %.2f  Y: %.2f  Z: %.2f m/s²", lastX, lastY, lastZ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val width = size.width
                val height = size.height
                val midY = height / 2f
                val maxHistory = 60
                val scale = height / 30.0f // maps -15 to +15 m/s^2

                if (telemetryHistory.size >= 2) {
                    val pathX = Path()
                    val pathY = Path()
                    val pathZ = Path()

                    val stepX = width / (maxHistory - 1).coerceAtLeast(1)

                    telemetryHistory.forEachIndexed { index, pt ->
                        val posX = index * stepX
                        val posY_X = (midY - (pt.first * scale)).coerceIn(0f, height)
                        val posY_Y = (midY - (pt.second * scale)).coerceIn(0f, height)
                        val posY_Z = (midY - (pt.third * scale)).coerceIn(0f, height)

                        if (index == 0) {
                            pathX.moveTo(posX, posY_X)
                            pathY.moveTo(posX, posY_Y)
                            pathZ.moveTo(posX, posY_Z)
                        } else {
                            pathX.lineTo(posX, posY_X)
                            pathY.lineTo(posX, posY_Y)
                            pathZ.lineTo(posX, posY_Z)
                        }
                    }

                    drawPath(path = pathX, color = colorX, style = Stroke(width = 2.5.dp.toPx()))
                    drawPath(path = pathY, color = colorY, style = Stroke(width = 2.5.dp.toPx()))
                    drawPath(path = pathZ, color = colorZ, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun AxisLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
