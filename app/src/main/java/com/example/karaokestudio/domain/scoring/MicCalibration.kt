package com.example.karaokestudio.domain.scoring

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MicRecommendation(
    val sensitivity: Int,
    val threshold: Int,
    val noiseLevel: Int,
    val voiceLevel: Int
)

object MicCalibration {

    const val MIN_VOICE_LEVEL = 600
    const val MIN_RATIO = 1.5
    private const val MAX_THRESHOLD = 4_000
    private const val NOISE_FLOOR = 150
    private const val STEP = 35

    fun percentile(values: List<Int>, fraction: Double): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * fraction.coerceIn(0.0, 1.0)).roundToInt()
        return sorted[index]
    }

    fun recommend(noise: List<Int>, voice: List<Int>): MicRecommendation? {
        if (noise.isEmpty() || voice.isEmpty()) return null
        val noiseLevel = percentile(noise, 0.95)
        val voiceLevel = percentile(voice, 0.75)
        if (voiceLevel < MIN_VOICE_LEVEL) return null
        if (voiceLevel < noiseLevel * MIN_RATIO) return null
        val geometric = sqrt(noiseLevel.coerceAtLeast(NOISE_FLOOR).toDouble() * voiceLevel)
        val wanted = geometric.coerceAtLeast(noiseLevel * 1.3).coerceIn(ScoreCalculator.thresholdFor(100).toDouble(), MAX_THRESHOLD.toDouble())
        val sensitivity = ((MAX_THRESHOLD - wanted) / STEP).roundToInt().coerceIn(0, 100)
        return MicRecommendation(
            sensitivity = sensitivity,
            threshold = ScoreCalculator.thresholdFor(sensitivity),
            noiseLevel = noiseLevel,
            voiceLevel = voiceLevel
        )
    }
}
