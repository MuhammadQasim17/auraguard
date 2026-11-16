# AuraGuard 🛡️
### Edge-Native Telemetry & Proactive Safety Platform

AuraGuard is an enterprise-grade, edge-native Android personal safety platform designed to deliver continuous real-time threat detection, anomaly inference, and automated emergency dispatch completely on-device. Built with modern Android architectural patterns (Clean Architecture, Jetpack Compose, Kotlin Coroutines & Flow, Room DB, and LiteRT/TFLite), AuraGuard processes high-frequency sensor vectors and 16kHz PCM acoustic audio streams entirely locally—guaranteeing zero latency, 100% privacy, and reliability in critical situations.

---

## 🏛️ Architecture Overview

AuraGuard enforces strict **Clean Architecture** principles decoupled into domain-driven layers to maintain scalability, testability, and high performance:

```
                  ┌──────────────────────────────────────────────┐
                  │          Jetpack Compose Material 3 UI       │
                  │   (Dashboard HUD, Incident History, Contacts) │
                  └──────────────────────┬───────────────────────┘
                                         │ StateFlow / Actions
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │                 MainViewModel                │
                  │        (MVI/MVVM Reactive State Engine)      │
                  └──────────────────────┬───────────────────────┘
                                         │
                  ┌──────────────────────┴───────────────────────┐
                  │          Foreground TelemetryService         │
                  └──────┬───────────────────────────────┬───────┘
                         │                               │
       ┌─────────────────┴─────────────┐  ┌──────────────┴──────────────┐
       │     Sensor Telemetry Engine   │  │   On-Device Acoustic ML    │
       │ (Linear Acceleration & Gyro)  │  │  (LiteRT 16kHz PCM Stream) │
       └─────────────────┬─────────────┘  └──────────────┬──────────────┘
                         │                               │
                         └───────────────┬───────────────┘
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │      15-Second Grace Period Manager          │
                  │  (Progressive Haptic & Interactive Disarm)   │
                  └──────────────────────┬───────────────────────┘
                                         │ Trigger (Timer Expiry)
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │         Autonomous Emergency Dispatcher      │
                  │  (Fused Location + SMS Dispatch + Room DB)   │
                  └──────────────────────────────────────────────┘
```

* **UI Layer**: Built entirely with **Jetpack Compose (Material 3)**. Reactive components stream real-time sensor waveforms and interactive countdown HUDs.
* **Service Layer**: Persistent Android **Foreground Service** (`TelemetryService`) utilizing foreground service types (`microphone`, `dataSync`) to guarantee background runtime stability across modern Android OS versions (Android 10 through 15 / API 36).
* **Data & Storage Layer**: **Room Database** utilizing reactive Kotlin `Flow` streams for persistence and instant updates across UI screens.
* **Edge Inference Engine**: Integrated **Google LiteRT** (TensorFlow Lite) engine optimized for 16 KB page memory alignment required by Android 15+.

---

## 🧩 Key Modules Breakdown

### 1. Sensor Telemetry Engine (Vector Dynamic Analysis)
* Captures high-frequency linear acceleration and rate of rotation via `Sensor.TYPE_ACCELEROMETER` and `Sensor.TYPE_GYROSCOPE`.
* Performs continuous vector norm computation:
  $$\|A\| = \sqrt{x^2 + y^2 + z^2}$$
* Utilizes a dual-threshold anomaly pipeline:
  * **Impact/Fall Threshold**: Detects severe dynamic impacts exceeding $35.0\,\text{m/s}^2$.
  * **Agitation/Struggle Threshold**: Detects violent rotational oscillations via angular momentum vectors ($\ge 15.0\,\text{rad/s}$).

### 2. On-Device Acoustic Classifier (16kHz PCM Edge Inference)
* Samples continuous raw audio using `AudioRecord` at 16,000 Hz, 16-bit Mono PCM.
* Streams sliding 1-second audio frames into a custom LiteRT model (`yamnet_threat_detector.tflite`).
* Infers real-time acoustic threat classifications (e.g., Glass Break, Scream, Gunshot, Explosion) with zero cloud dependency and strict privacy assurance.

### 3. Grace Period Manager & Progressive Haptic Alert
* Triggers a non-blocking 15-second countdown upon detecting any acoustic or physical anomaly.
* Emits progressive multi-stage haptic vibration pulses (`Vibrator` / `VibratorManager`) that escalate in frequency as the timer counts down.
* Provides single-tap instant disarm capability ("I AM SAFE") to prevent false alarms.

### 4. Autonomous Emergency Dispatcher
* **Fused Location**: Resolves high-accuracy GPS coordinates via Google Play Services `FusedLocationProviderClient`.
* **SMS Dispatch**: Automatically parses primary emergency contacts from Room DB and constructs structured SMS emergency payloads containing precise Google Maps location links.
* **Audit Persistence**: Writes encrypted local incident logs to Room Database with timestamp, confidence score, trigger type, and geographic coordinates.

---

## ⚡ Technical Highlights for Resume & Technical Interviews

* **16 KB Page Alignment Ready**: Native LiteRT C/C++ runtime bindings configured and verified for Android 15’s mandatory 16 KB memory page size architecture.
* **Battery-Efficient Sensor Batching**: Dynamically manages hardware sensor listener registration and unregistration to prevent CPU wake-lock battery drain.
* **Privacy-First Zero-Cloud Architecture**: Continuous audio analysis happens entirely in device RAM—no raw audio or PCM buffers ever leave the device.
* **Flow-Driven Unidirectional Data Flow (UDF)**: End-to-end reactive pipeline using `StateFlow`, `SharedFlow`, and `collectAsStateWithLifecycle` for seamless Compose UI updates without memory leaks.
* **Interactive Lockscreen Notification Action**: Integrated PendingIntent "DISARM / STOP" action on persistent foreground notifications, allowing instant dismissal from lockscreen or notification shade.

---

## 🛠️ Debug Test Harness

AuraGuard includes a dedicated **Debug Test Harness** card embedded directly on the HUD for developer builds (`BuildConfig.DEBUG = true`):
* **Simulate Fall Impact**: Triggers a high-impact fall anomaly ($35.0\,\text{m/s}^2$).
* **Simulate Acoustic Spike**: Triggers a high-confidence acoustic threat event.
* **Full Pipeline Verification**: Simulating an anomaly tests the full pipeline:
  1. Instant HUD countdown dialog opening (15s timer)
  2. Escalating haptic vibration feedback
  3. Automatic GPS location lock & SMS dispatch upon timer expiry
  4. Real-time insertion into the Room DB Incident History

---

## 🚀 Setup & Run Instructions

### Prerequisites
* **Android Studio**: Ladybug / Android Studio Jellyfish (2024.2+) or newer.
* **JDK**: OpenJDK 17 or Java 21.
* **Android SDK**: API Level 36 (Android 15), minimum API Level 26 (Android 8.0).

### Building & Running
1. Clone repository:
   ```bash
   git clone https://github.com/your-username/AuraGuard.git
   cd AuraGuard
   ```
2. Open project in **Android Studio**.
3. Perform Gradle Sync:
   ```bash
   ./gradlew assembleDebug
   ```
4. Deploy to physical Android device or emulator with API 26+:
   ```bash
   ./gradlew installDebug
   ```
5. Grant requested runtime permissions on launch (**Microphone**, **Location**, **SMS**, **Notifications**).

---

## 🧪 Testing

Execute unit tests across all repositories, viewmodels, and ML engines:
```bash
./gradlew test
```

---

## 📜 License
Developed as an advanced edge-native security engineering portfolio application.
