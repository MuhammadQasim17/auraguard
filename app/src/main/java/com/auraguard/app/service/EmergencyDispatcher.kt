package com.auraguard.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.auraguard.app.data.local.CalibrationPreferencesManager
import com.auraguard.app.data.local.dao.EmergencyContactDao
import com.auraguard.app.data.local.entity.EmergencyContactEntity
import com.auraguard.app.data.local.entity.IncidentEntity
import com.auraguard.app.data.repository.IncidentRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class EmergencyDispatcher(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val incidentRepository: IncidentRepository,
    private val emergencyContactDao: EmergencyContactDao,
) {

    suspend fun triggerEmergencyProtocol(incidentType: String, confidence: Float) {
        // 1. Fetch Location
        val location = fetchLocation()
        val lat = location?.latitude
        val lng = location?.longitude

        // 2. Fetch Contacts
        val contacts = fetchEmergencyContacts()

        // 3. Construct Payload
        val locationUrl = if ((lat != null) && (lng != null)) {
            "https://maps.google.com/?q=$lat,$lng"
        } else {
            "Location Unavailable"
        }

        val confidencePercent = (confidence * 100).toInt()
        val sosMessage = "EMERGENCY ALERT: AuraGuard detected [$incidentType] " +
            "(Confidence: $confidencePercent%). Location: $locationUrl"

        // 4. Dispatch SMS to all contacts
        val smsSentCount = sendSmsToContacts(contacts, sosMessage)

        // 5. Trigger Phone Call to Primary Contact
        val callInitiated = triggerPhoneCallForPrimaryContact(contacts)

        // 6. Build Delivery Status Verification Text
        val primaryContact = contacts.find { it.isPrimary } ?: contacts.firstOrNull()
        val callStatusStr = if (callInitiated) {
            if (primaryContact != null) "Call placed to ${primaryContact.name}" else "Call placed"
        } else {
            "No Call"
        }
        val deliveryStatusText = "Delivered to $smsSentCount/${contacts.size} contacts • $callStatusStr"

        // 7. Persist Incident Record into Room DB with Delivery Proof
        incidentRepository.insertIncident(
            IncidentEntity(
                timestamp = System.currentTimeMillis(),
                triggerType = incidentType,
                confidenceScore = confidence,
                latitude = lat,
                longitude = lng,
                resolved = false,
                smsSentCount = smsSentCount,
                callInitiated = callInitiated,
                deliveryStatusText = deliveryStatusText,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(): Location? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLoc -> continuation.resume(lastLoc) }
                            .addOnFailureListener { continuation.resume(null) }
                    }
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc -> continuation.resume(lastLoc) }
                        .addOnFailureListener { continuation.resume(null) }
                }
        }
    }

    private suspend fun fetchEmergencyContacts(): List<EmergencyContactEntity> {
        return try {
            emergencyContactDao.getAllContacts().first()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching emergency contacts from database", e)
            emptyList()
        }
    }

    private fun sendSmsToContacts(contacts: List<EmergencyContactEntity>, messageText: String): Int {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.e(TAG, "SEND_SMS permission missing! Unable to dispatch emergency SMS.")
            showToast("AuraGuard Alert: SEND_SMS permission missing!")
            return 0
        }

        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured in database.")
            showToast("AuraGuard Alert: No emergency contacts configured!")
            return 0
        }

        var sentCount = 0
        try {
            val calibrationPrefs = CalibrationPreferencesManager(context)
            val targetSubId = calibrationPrefs.preferredSubId

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (targetSubId != -1) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(targetSubId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                }
            } else {
                if (targetSubId != -1) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(targetSubId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            for (contact in contacts) {
                if (contact.phoneNumber.isNotBlank()) {
                    val parts = smsManager.divideMessage(messageText)
                    val sentIntents = ArrayList<PendingIntent>()

                    for (i in parts.indices) {
                        val sentAction = "com.auraguard.app.action.SMS_SENT_${System.currentTimeMillis()}_$i"
                        val sentIntent = PendingIntent.getBroadcast(
                            context,
                            0,
                            Intent(sentAction),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        sentIntents.add(sentIntent)
                    }

                    smsManager.sendMultipartTextMessage(
                        contact.phoneNumber,
                        null,
                        parts,
                        sentIntents,
                        null,
                    )
                    sentCount++
                    Log.d(TAG, "Emergency SMS dispatched to ${contact.name} (${contact.phoneNumber}) [subId=$targetSubId]")
                }
            }

            if (sentCount > 0) {
                showToast("Emergency SMS dispatched to $sentCount contact(s)")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending SMS: permission revoked at runtime", e)
            showToast("Failed to send SMS: Security permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency SMS", e)
            showToast("Failed to send emergency SMS: ${e.localizedMessage}")
        }

        return sentCount
    }

    private fun triggerPhoneCallForPrimaryContact(contacts: List<EmergencyContactEntity>): Boolean {
        val primaryContact = contacts.find { it.isPrimary } ?: contacts.firstOrNull()

        if (primaryContact == null || primaryContact.phoneNumber.isBlank()) {
            Log.w(TAG, "No primary contact available for emergency call.")
            return false
        }

        val rawPhone = primaryContact.phoneNumber
        val cleanPhone = rawPhone.replace("[^0-9+]".toRegex(), "")
        if (cleanPhone.isBlank()) {
            Log.e(TAG, "Invalid phone number for primary contact: $rawPhone")
            return false
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (hasCallPermission) {
                Log.d(TAG, "Initiating direct ACTION_CALL to $cleanPhone (${primaryContact.name})")

                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

                @SuppressLint("MissingPermission")
                val callAccounts: List<PhoneAccountHandle> = if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        telecomManager.callCapablePhoneAccounts ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val calibrationPrefs = CalibrationPreferencesManager(context)
                val targetSubId = calibrationPrefs.preferredSubId

                var matchedHandle: PhoneAccountHandle? = null

                if (targetSubId != -1 && callAccounts.isNotEmpty()) {
                    for (handle in callAccounts) {
                        if (handle.id.contains(targetSubId.toString())) {
                            matchedHandle = handle
                            break
                        }
                    }
                }

                if (matchedHandle == null) {
                    @SuppressLint("MissingPermission")
                    val defaultHandle: PhoneAccountHandle? = if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    matchedHandle = defaultHandle ?: callAccounts.firstOrNull()
                }

                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (matchedHandle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedHandle)
                    }
                }

                context.startActivity(callIntent)
                showToast("Initiating emergency call to ${primaryContact.name}...")
                true
            } else {
                Log.w(TAG, "CALL_PHONE permission missing. Falling back gracefully to ACTION_DIAL.")
                showToast("CALL_PHONE permission missing: opening phone dialer...")
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException calling primary contact", e)
            showToast("Security Exception: Cannot place call directly.")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call or dialer", e)
            showToast("Failed to initiate emergency call: ${e.localizedMessage}")
            false
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "EmergencyDispatcher"
    }
}
