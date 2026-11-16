package com.auraguard.app.ml

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.auraguard.app.data.local.CalibrationPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioClassifierEngineImpl(
    private val context: Context,
) : AudioClassifierEngine {

    private val calibrationPrefs = CalibrationPreferencesManager(context)

    private val _acousticEvents = MutableSharedFlow<AcousticThreatEvent>(extraBufferCapacity = 64)
    override val acousticEvents: SharedFlow<AcousticThreatEvent> = _acousticEvents.asSharedFlow()

    private val _liveAudioSpectrum = MutableStateFlow<List<Float>>(List(24) { 0.05f })
    override val liveAudioSpectrum: StateFlow<List<Float>> = _liveAudioSpectrum.asStateFlow()

    private val _liveDecibels = MutableStateFlow<Float>(42.0f)
    override val liveDecibels: StateFlow<Float> = _liveDecibels.asStateFlow()

    private val _liveClassificationMap = MutableStateFlow<Map<String, Float>>(
        mapOf(
            "Human Scream / Distress" to 0.02f,
            "Glass Shatter / Breakage" to 0.01f,
            "High Decibel Impact" to 0.01f,
            "Normal Ambient Background" to 0.96f,
        ),
    )
    override val liveClassificationMap: StateFlow<Map<String, Float>> = _liveClassificationMap.asStateFlow()

    private var recordingJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var interpreter: Interpreter? = null

    private var lastEventTimestamp = 0L

    init {
        interpreter = loadModelIfAvailable(MODEL_FILE_NAME)
    }

    override fun startListening() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission missing. Cannot start listening.")
            return
        }

        stopListening()

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
            )
            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_BYTES)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )

            audioRecord?.startRecording()

            recordingJob = engineScope.launch {
                val audioBuffer = ShortArray(SAMPLE_RATE / 2) // 0.5s chunk
                while (isActive) {
                    try {
                        val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        if (readSize > 0) {
                            processAudioChunk(audioBuffer, readSize)
                        } else {
                            delay(50L)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inside audio reading loop", e)
                        delay(100L)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
        }
    }

    override fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    fun close() {
        stopListening()
        engineScope.cancel()
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TFLite interpreter", e)
        }
        interpreter = null
    }

    private fun processAudioChunk(buffer: ShortArray, size: Int) {
        if (size <= 1) return

        try {
            val micGain = calibrationPrefs.micSensitivity.coerceIn(0.5f, 3.0f)

            var zeroCrossings = 0
            var maxPeak = 0
            var sumSquares = 0.0
            var highDiffEnergy = 0.0

            for (i in 0 until size) {
                val rawSample = buffer[i].toInt()
                val sample = (rawSample * micGain).toInt().coerceIn(-32768, 32767)
                val absVal = abs(sample)
                if (absVal > maxPeak) maxPeak = absVal
                sumSquares += (sample.toDouble() * sample.toDouble())

                if (i > 0) {
                    val prevRawSample = buffer[i - 1].toInt()
                    val prevSample = (prevRawSample * micGain).toInt().coerceIn(-32768, 32767)
                    if ((sample >= 0 && prevSample < 0) || (sample < 0 && prevSample >= 0)) {
                        zeroCrossings++
                    }
                    val diff = sample - prevSample
                    highDiffEnergy += (diff.toDouble() * diff.toDouble())
                }
            }

            val safeSize = size.coerceAtLeast(1)
            val rms = sqrt(sumSquares / safeSize).toFloat()
            val zcr = zeroCrossings.toFloat() / safeSize.toFloat() // 0.0 to 1.0
            val highFreqRatio = (highDiffEnergy / (sumSquares + 1.0)).toFloat()

            // Real-time Decibels (dB SPL)
            val normRms = (rms / 32767.0f).coerceAtLeast(0.00001f)
            val rmsDbRaw = (20.0f * log10(normRms)) + 90.0f
            val decibels = if (rmsDbRaw.isNaN() || rmsDbRaw.isInfinite()) 40.0f else rmsDbRaw.coerceIn(30.0f, 105.0f)

            // Multi-band 24-frequency spectrum calculation for UI graph visualization
            val binSize = maxOf(size / 24, 1)
            val spectrumList = ArrayList<Float>(24)

            for (b in 0 until 24) {
                var binSum = 0.0
                val start = b * binSize
                val end = minOf(start + binSize, size)
                val count = maxOf(end - start, 1)

                for (j in start until end) {
                    val sample = (buffer[j].toDouble() * micGain).coerceIn(-32768.0, 32767.0)
                    binSum += sample * sample
                }
                val binRms = sqrt(binSum / count)
                val normVal = ((binRms / 32767.0) * 20.0).toFloat()
                val safeNormVal = if (normVal.isNaN() || normVal.isInfinite()) 0.05f else normVal.coerceIn(0.01f, 0.99f)
                spectrumList.add(safeNormVal)
            }

            // GLASS SHATTER DETECTION CRITERIA (ZCR + High-Pass First-Order Difference Energy):
            // 1. High Zero-Crossing Rate (zcr >= 0.28): glass shattering has sharp chaotic oscillations
            // 2. High-Frequency dominance (highFreqRatio >= 1.15)
            // 3. Sufficient peak amplitude (maxPeak >= effectivePeakThreshold)
            // 4. Voice Rejection: human speech vowels & voiced speech have low ZCR (0.05 - 0.18)
            val effectivePeakThreshold = (13500f / micGain.coerceIn(0.5f, 2.0f)).toInt()
            val isGlassShatter = (zcr >= 0.28f) && (highFreqRatio >= 1.15f) && (maxPeak >= effectivePeakThreshold)

            var glassConf: Float
            var screamConf: Float
            var impactConf: Float

            if (isGlassShatter) {
                glassConf = 0.92f
                screamConf = 0.02f
                impactConf = 0.02f
            } else {
                glassConf = if (maxPeak > 15000 && zcr < 0.22f) 0.02f else 0.01f

                // Human Scream Evaluation (Sustained High Energy & Moderate ZCR)
                if (maxPeak >= 22000 && rms >= 10000.0f && zcr in 0.08f..0.25f) {
                    screamConf = 0.88f
                } else {
                    screamConf = 0.02f
                }

                // High Decibel Impact Evaluation
                if (maxPeak >= 20000 && rms >= 8000.0f && zcr < 0.15f) {
                    impactConf = 0.82f
                } else {
                    impactConf = 0.01f
                }
            }

            if (screamConf.isNaN()) screamConf = 0.02f
            if (glassConf.isNaN()) glassConf = 0.01f
            if (impactConf.isNaN()) impactConf = 0.01f

            val highestThreat = maxOf(glassConf, maxOf(screamConf, impactConf))
            val ambientConf = if (isGlassShatter) {
                0.05f
            } else {
                (1.0f - highestThreat).coerceIn(0.05f, 0.98f)
            }

            val classificationMap = mapOf(
                "Human Scream / Distress" to screamConf.coerceIn(0.01f, 0.99f),
                "Glass Shatter / Breakage" to glassConf.coerceIn(0.01f, 0.99f),
                "High Decibel Impact" to impactConf.coerceIn(0.01f, 0.99f),
                "Normal Ambient Background" to ambientConf.coerceIn(0.01f, 0.99f),
            )

            // ALWAYS UPDATE AND EMIT LIVE UI STATEFLOWS ON EVERY SINGLE CYCLE
            _liveAudioSpectrum.value = spectrumList
            _liveDecibels.value = decibels
            _liveClassificationMap.value = classificationMap

            _globalSpectrum.value = spectrumList
            _globalDecibels.value = decibels
            _globalClassificationMap.value = classificationMap

            // Trigger immediately on a clear glass shatter event:
            if (isGlassShatter) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEventTimestamp > DEBOUNCE_MS) {
                    lastEventTimestamp = currentTime
                    _acousticEvents.tryEmit(
                        AcousticThreatEvent(
                            label = "ACOUSTIC_GLASS_SHATTER",
                            confidence = 0.92f,
                            timestamp = currentTime,
                        ),
                    )
                }
            } else if (highestThreat >= 0.75f) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEventTimestamp > DEBOUNCE_MS) {
                    lastEventTimestamp = currentTime
                    val activeLabel = if (impactConf >= 0.75f) "ACOUSTIC_HIGH_DECIBEL_IMPACT" else "ACOUSTIC_DISTRESS"
                    _acousticEvents.tryEmit(
                        AcousticThreatEvent(
                            label = activeLabel,
                            confidence = highestThreat,
                            timestamp = currentTime,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processAudioChunk", e)
        }
    }

    private fun loadModelIfAvailable(modelName: String): Interpreter? {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            Interpreter(modelBuffer)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AudioClassifierEngine"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_BYTES = 4096
        private const val MODEL_FILE_NAME = "acoustic_model.tflite"

        private const val DEBOUNCE_MS = 1000L

        private val _globalSpectrum = MutableStateFlow<List<Float>>(List(24) { 0.05f })
        val globalSpectrum: StateFlow<List<Float>> = _globalSpectrum.asStateFlow()

        private val _globalDecibels = MutableStateFlow<Float>(42.0f)
        val globalDecibels: StateFlow<Float> = _globalDecibels.asStateFlow()

        private val _globalClassificationMap = MutableStateFlow<Map<String, Float>>(
            mapOf(
                "Human Scream / Distress" to 0.02f,
                "Glass Shatter / Breakage" to 0.01f,
                "High Decibel Impact" to 0.01f,
                "Normal Ambient Background" to 0.96f,
            ),
        )
        val globalClassificationMap: StateFlow<Map<String, Float>> = _globalClassificationMap.asStateFlow()
    }
}
