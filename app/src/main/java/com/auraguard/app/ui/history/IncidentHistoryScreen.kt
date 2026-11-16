package com.auraguard.app.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraguard.app.data.local.entity.IncidentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentHistoryScreen(
    incidents: List<IncidentEntity> = emptyList(),
    onToggleResolved: (IncidentEntity) -> Unit = {},
    onDeleteIncident: (IncidentEntity) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "INCIDENT HISTORY",
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
        if (incidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No safety incidents recorded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(incidents) { incident ->
                    ExpandableIncidentCard(
                        incident = incident,
                        onToggleResolved = { onToggleResolved(incident) },
                        onDeleteIncident = { onDeleteIncident(incident) },
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableIncidentCard(
    incident: IncidentEntity,
    onToggleResolved: () -> Unit,
    onDeleteIncident: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(incident.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Trigger Badge, Resolution Status, Expand Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = incident.triggerType,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (incident.resolved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (incident.resolved) "RESOLVED" else "UNRESOLVED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (incident.resolved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse Forensic Details" else "Expand Forensic Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dispatch Audit Badges Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Badge 1: SMS Status Chip
                val smsSuccess = incident.smsSentCount > 0
                Box(
                    modifier = Modifier
                        .background(
                            color = if (smsSuccess) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "SMS Audit",
                            tint = if (smsSuccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (smsSuccess) "Emergency SMS sent to ${incident.smsSentCount} contact(s)" else "SMS delivery failed (0 contacts)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (smsSuccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Badge 2: Phone Call Status Chip
                val callSuccess = incident.callInitiated
                val callText = if (callSuccess) {
                    val status = incident.deliveryStatusText
                    if (status != null && status.contains("Call placed to")) {
                        val namePart = status.substringAfter("Call placed to ").trim()
                        "Auto-call dialed: $namePart"
                    } else {
                        "Auto-call dialed: Primary Contact"
                    }
                } else {
                    "Call skipped (No primary contact)"
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (callSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone Call Audit",
                            tint = if (callSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = callText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (callSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Forensic Expandable Body
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "FORENSIC INCIDENT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Confidence Score Indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Confidence Score: ${(incident.confidenceScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        LinearProgressIndicator(
                            progress = { incident.confidenceScore.coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Location Forensic Coordinates
                    val lat = incident.latitude
                    val lng = incident.longitude
                    Text(
                        text = if (lat != null && lng != null) "Coordinates: Lat $lat°, Lng $lng°" else "Coordinates: Location Unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Audit Trail Section
                    Text(
                        text = "ACTION AUDIT TRAIL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val smsAuditText = if (incident.smsSentCount > 0) {
                        "• Emergency SOS SMS dispatched with live GPS location (${incident.smsSentCount} contacts)"
                    } else {
                        "• Emergency SOS SMS dispatch failed (No SMS permission or contacts)"
                    }

                    val callAuditText = if (incident.callInitiated) {
                        "• Emergency phone call placed to primary contact"
                    } else {
                        "• Emergency phone call skipped (No primary contact configured)"
                    }

                    Text(
                        text = "$smsAuditText\n$callAuditText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row: Open Maps, Toggle Status, Delete Record
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (lat != null && lng != null) {
                            Button(
                                onClick = {
                                    val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text("Maps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onToggleResolved,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = if (incident.resolved) "Mark Unresolved" else "Mark Resolved",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        IconButton(onClick = onDeleteIncident) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Forensic Log",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
