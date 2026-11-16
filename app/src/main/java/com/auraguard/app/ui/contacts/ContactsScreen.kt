package com.auraguard.app.ui.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.auraguard.app.data.local.entity.EmergencyContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<EmergencyContactEntity> = emptyList(),
    selectedSubId: Int = -1,
    onSelectSubId: (Int) -> Unit = {},
    onAddContact: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSetPrimaryContact: (EmergencyContactEntity) -> Unit = {},
    onRemoveContact: (EmergencyContactEntity) -> Unit = {},
) {
    val context = LocalContext.current
    var showManualAddDialog by remember { mutableStateOf(false) }

    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    val activeSubscriptions: List<SubscriptionInfo> = if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    ) {
        try {
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
    ) { uri: Uri? ->
        uri?.let { contactUri ->
            val details = queryContactDetails(context, contactUri)
            if (details != null) {
                val (name, phone) = details
                onAddContact(name, phone, contacts.isEmpty())
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        }
    }

    fun launchContactPicker() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            pickContactLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    if (showManualAddDialog) {
        AddContactDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { name, phone, isPrimary ->
                onAddContact(name, phone, isPrimary || contacts.isEmpty())
                showManualAddDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EMERGENCY CONTACTS",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact Manually")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Dual-SIM Line Selection Card
            SimSelectorCard(
                activeSubscriptions = activeSubscriptions,
                selectedSubId = selectedSubId,
                onSelectSubId = onSelectSubId,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Pick from Phone Contacts & Manual Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { launchContactPicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactPage,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Pick Phone Contact", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showManualAddDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Manual Entry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No emergency contacts added yet.\nTap 'Pick Phone Contact' or '+' to add.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(contacts) { contact ->
                        ContactCard(
                            contact = contact,
                            onSetPrimary = { onSetPrimaryContact(contact) },
                            onDelete = { onRemoveContact(contact) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimSelectorCard(
    activeSubscriptions: List<SubscriptionInfo>,
    selectedSubId: Int,
    onSelectSubId: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = "Emergency Dispatch SIM",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeSubscriptions.isEmpty()) {
                    FilterChip(
                        selected = selectedSubId == 0 || selectedSubId == -1,
                        onClick = { onSelectSubId(0) },
                        label = { Text("SIM 1 (Default)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                    )

                    FilterChip(
                        selected = selectedSubId == 1,
                        onClick = { onSelectSubId(1) },
                        label = { Text("SIM 2 (Secondary)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    activeSubscriptions.forEach { subInfo ->
                        val subId = subInfo.subscriptionId
                        val simName = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}"
                        val isSelected = (selectedSubId == subId) || (selectedSubId == -1 && subInfo == activeSubscriptions.first())
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSubId(subId) },
                            label = { Text("SIM ${subInfo.simSlotIndex + 1}: $simName", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: EmergencyContactEntity,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (contact.isPrimary) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "PRIMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSetPrimary) {
                    Icon(
                        imageVector = if (contact.isPrimary) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Set Primary Contact",
                        tint = if (contact.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Contact",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, isPrimary: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Emergency Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name.trim(), phone.trim(), false)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun queryContactDetails(context: Context, contactUri: Uri): Pair<String, String>? {
    val contentResolver = context.contentResolver
    var name = ""
    var phone = ""

    contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            val contactId = if (idIndex >= 0) cursor.getString(idIndex) else ""
            name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
            val hasPhone = if (hasPhoneIndex >= 0) cursor.getString(hasPhoneIndex) ?: "0" else "0"

            if ((hasPhone == "1" || hasPhone.lowercase() == "true") && contactId.isNotEmpty()) {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null,
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numberIndex >= 0) {
                            phone = phoneCursor.getString(numberIndex) ?: ""
                        }
                    }
                }
            }
        }
    }

    if (name.isNotBlank() && phone.isNotBlank()) {
        return Pair(name, phone)
    }
    return null
}
