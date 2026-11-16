package com.example.karaokestudio

import com.example.karaokestudio.domain.lyrics.LyricLine
import com.example.karaokestudio.domain.scoring.ScoreCalculator
import com.example.karaokestudio.domain.scoring.VoiceSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    private val lines = listOf(
        LyricLine(0, ""),
        LyricLine(2_000, "Рядок один"),
        LyricLine(6_000, "Рядок два"),
        LyricLine(10_000, "")
    )

    private fun samples(from: Long, to: Long, amplitude: Int) =
        (from until to step 100).map { VoiceSample(it, amplitude) }

    @Test
    fun loudSingingGivesFullScore() {
        val result = ScoreCalculator.calculate(samples(2_000, 10_000, 8_000), lines, 2_000)
        assertNotNull(result)
        assertEquals(100, result!!.score)
        assertEquals(5, result.stars)
    }

    @Test
    fun silenceGivesZero() {
        val result = ScoreCalculator.calculate(samples(2_000, 10_000, 0), lines, 2_000)
        assertEquals(0, result!!.score)
    }

    @Test
    fun halfActiveGivesAboutFifty() {
        val data = samples(2_000, 6_000, 8_000) + samples(6_000, 10_000, 0)
        assertEquals(50, ScoreCalculator.calculate(data, lines, 2_000)!!.score)
    }

    @Test
    fun instrumentalIntroIsIgnored() {
        val data = samples(0, 2_000, 9_000) + samples(2_000, 10_000, 0)
        assertEquals(0, ScoreCalculator.calculate(data, lines, 2_000)!!.score)
    }

    @Test
    fun tooShortSingingGivesNoScore() {
        assertNull(ScoreCalculator.calculate(samples(2_000, 3_000, 8_000), lines, 2_000))
    }

    @Test
    fun higherSensitivityLowersThreshold() {
        assertTrue(ScoreCalculator.thresholdFor(100) < ScoreCalculator.thresholdFor(0))
        assertEquals(2_250, ScoreCalculator.thresholdFor(50))
    }

    @Test
    fun starsFollowScoreRanges() {
        assertEquals(0, ScoreCalculator.starsFor(10))
        assertEquals(3, ScoreCalculator.starsFor(60))
        assertEquals(5, ScoreCalculator.starsFor(95))
    }
}
