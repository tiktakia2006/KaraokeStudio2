package com.example.karaokestudio.domain.scoring

import com.example.karaokestudio.domain.lyrics.LrcParser
import com.example.karaokestudio.domain.lyrics.LyricLine
import kotlin.math.roundToInt

data class VoiceSample(val timeMs: Long, val amplitude: Int)

data class ScoreResult(
    val score: Int,
    val stars: Int,
    val activeShare: Float,
    val analyzedMs: Long
)

object ScoreCalculator {

    const val FRAME_MS = 100L
    private const val MIN_FRAMES = 20
    private const val MAX_LINE_MS = 10_000L

    fun thresholdFor(sensitivity: Int): Int = (4_000 - sensitivity.coerceIn(0, 100) * 35).coerceAtLeast(300)

    fun calculate(samples: List<VoiceSample>, lines: List<LyricLine>, threshold: Int): ScoreResult? {
        var inLyrics = 0
        var active = 0
        for (sample in samples) {
            val index = LrcParser.indexAt(lines, sample.timeMs)
            if (index < 0) continue
            val line = lines[index]
            if (line.text.isBlank() || sample.timeMs - line.timeMs > MAX_LINE_MS) continue
            inLyrics++
            if (sample.amplitude >= threshold) active++
        }
        if (inLyrics < MIN_FRAMES) return null
        val share = active.toFloat() / inLyrics
        val score = (share * 100).roundToInt().coerceIn(0, 100)
        return ScoreResult(score, starsFor(score), share, inLyrics * FRAME_MS)
    }

    fun starsFor(score: Int): Int = when {
        score >= 90 -> 5
        score >= 75 -> 4
        score >= 55 -> 3
        score >= 35 -> 2
        score >= 15 -> 1
        else -> 0
    }

    fun titleFor(score: Int): String = when {
        score >= 90 -> "Блискуче виконання"
        score >= 75 -> "Чудовий результат"
        score >= 55 -> "Добре, так тримати"
        score >= 35 -> "Непогано, є куди рости"
        score >= 15 -> "Спробуйте ще раз і співайте гучніше"
        else -> "Майже не було чутно голосу"
    }
}
