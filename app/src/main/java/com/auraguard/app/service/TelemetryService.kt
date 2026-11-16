package com.auraguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.auraguard.app.MainActivity
import com.auraguard.app.data.local.AuraGuardDatabase
import com.auraguard.app.data.repository.IncidentRepositoryImpl
import com.auraguard.app.ml.AudioClassifierEngineImpl
import com.auraguard.app.sensor.SensorAnomalyDetector
import com.auraguard.app.sensor.TelemetryEvent
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TelemetryService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var sensorAnomalyDetector: SensorAnomalyDetector
    private lateinit var audioClassifierEngine: AudioClassifierEngineImpl
    private lateinit var emergencyDispatcher: EmergencyDispatcher
    private lateinit var gracePeriodManager: GracePeriodManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val database = AuraGuardDatabase.getDatabase(this)
        val incidentRepository = IncidentRepositoryImpl(database.incidentDao())
        val emergencyContactDao = database.emergencyContactDao()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        emergencyDispatcher = EmergencyDispatcher(
            context = this,
            fusedLocationClient = fusedLocationClient,
            incidentRepository = incidentRepository,
            emergencyContactDao = emergencyContactDao,
        )

        gracePeriodManager = GracePeriodManager(
            context = this,
            scope = serviceScope,
        )

        sensorAnomalyDetector = SensorAnomalyDetector(this)
        sensorAnomalyDetector.startListening()

        audioClassifierEngine = AudioClassifierEngineImpl(this)
        audioClassifierEngine.startListening()

        observeTelemetryEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISARM -> {
                if (::gracePeriodManager.isInitialized) {
                    gracePeriodManager.disarm()
                }
            }
            ACTION_STOP_SERVICE -> {
                if (::gracePeriodManager.isInitialized) {
                    gracePeriodManager.disarm()
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SIMULATE_ANOMALY -> {
                val type = intent.getStringExtra(EXTRA_INCIDENT_TYPE) ?: "SIMULATED_HIGH_IMPACT_FALL"
                val conf = intent.getFloatExtra(EXTRA_CONFIDENCE, 0.95f)
                triggerGracePeriod(type, conf)
            }
        }

        val notification = createNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundType,
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorAnomalyDetector.isInitialized) {
            sensorAnomalyDetector.stopListening()
        }
        if (::audioClassifierEngine.isInitialized) {
            audioClassifierEngine.close()
        }
        if (::gracePeriodManager.isInitialized) {
            gracePeriodManager.disarm()
        }
        serviceScope.cancel()
    }

    private fun observeTelemetryEvents() {
        // Observe Sensor Anomaly stream
        serviceScope.launch {
            sensorAnomalyDetector.telemetryEvents.collect { event ->
                when (event) {
                    is TelemetryEvent.ImpactAnomaly -> {
                        triggerGracePeriod(
                            incidentType = "HIGH_IMPACT_FALL",
                            confidence = (event.magnitude / 35.0f).coerceIn(0.70f, 1.0f),
                        )
                    }
                    is TelemetryEvent.ShakeAnomaly -> {
                        triggerGracePeriod(
                            incidentType = "AGGRESSIVE_SHAKE_STRUGGLE",
                            confidence = (event.gyroMagnitude / 15.0f).coerceIn(0.70f, 1.0f),
                        )
                    }
                    TelemetryEvent.Normal -> {
                        // Normal telemetry
                    }
                }
            }
        }

        // Observe Acoustic ML Threat stream
        serviceScope.launch {
            audioClassifierEngine.acousticEvents.collect { threatEvent ->
                triggerGracePeriod(
                    incidentType = "ACOUSTIC_${threatEvent.label.uppercase().replace(' ', '_')}",
                    confidence = threatEvent.confidence,
                )
            }
        }
    }

    private fun triggerGracePeriod(incidentType: String, confidence: Float) {
        gracePeriodManager.startGracePeriod(
            incidentType = incidentType,
            confidence = confidence,
            durationSeconds = 15,
        ) { type, conf ->
            emergencyDispatcher.triggerEmergencyProtocol(type, conf)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AuraGuard Active Defense Channel",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Continuous active defense monitoring channel"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, TelemetryService::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, TelemetryService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AuraGuard Active Defense")
            .setContentText("AuraGuard Active Defense Running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DISARM / STOP",
                stopPendingIntent,
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "auraguard_telemetry_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISARM = "com.auraguard.app.action.DISARM_ALARM"
        const val ACTION_STOP_SERVICE = "com.auraguard.app.action.STOP_SERVICE"
        const val ACTION_SIMULATE_ANOMALY = "com.auraguard.app.action.SIMULATE_ANOMALY"

        const val EXTRA_INCIDENT_TYPE = "extra_incident_type"
        const val EXTRA_CONFIDENCE = "extra_confidence"

        fun startService(context: Context) {
            val intent = Intent(context, TelemetryService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelemetryService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun disarmAlarm(context: Context) {
            val intent = Intent(context, TelemetryService::class.java).apply {
                action = ACTION_DISARM
            }
            context.startService(intent)
        }

        fun simulateAnomaly(context: Context, incidentType: String, confidence: Float) {
            val intent = Intent(context, TelemetryService::class.java).apply {
                action = ACTION_SIMULATE_ANOMALY
                putExtra(EXTRA_INCIDENT_TYPE, incidentType)
                putExtra(EXTRA_CONFIDENCE, confidence)
            }
            context.startForegroundService(intent)
        }
    }
}
