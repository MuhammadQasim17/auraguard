package com.auraguard.app.ml

data class ClassificationResult(
    val label: String,
    val confidence: Float
)

interface AudioClassifier {
    fun classify(audioData: ByteArray): ClassificationResult
    fun close()
}
