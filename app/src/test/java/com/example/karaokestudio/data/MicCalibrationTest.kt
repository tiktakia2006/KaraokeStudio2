package com.example.karaokestudio.data

import com.example.karaokestudio.domain.scoring.MicCalibration
import com.example.karaokestudio.domain.scoring.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCalibrationTest {

    @Test
    fun percentileHandlesEmptyAndBounds() {
        assertEquals(0, MicCalibration.percentile(emptyList(), 0.5))
        assertEquals(10, MicCalibration.percentile(listOf(30, 10, 20), 0.0))
        assertEquals(30, MicCalibration.percentile(listOf(30, 10, 20), 1.0))
        assertEquals(20, MicCalibration.percentile(listOf(30, 10, 20), 0.5))
    }

    @Test
    fun quietRoomAndNormalVoiceGiveSensitivityBetweenBoundaries() {
        val noise = List(30) { 200 + it % 5 * 20 }
        val voice = List(30) { 2_000 + it * 40 }

        val result = MicCalibration.recommend(noise, voice)

        assertNotNull(result)
        result!!
        assertTrue(result.threshold > result.noiseLevel)
        assertTrue(result.threshold < result.voiceLevel)
        assertEquals(ScoreCalculator.thresholdFor(result.sensitivity), result.threshold)
        assertTrue(result.sensitivity in 0..100)
    }

    @Test
    fun noisyRoomRaisesThreshold() {
        val voice = List(30) { 5_000 }
        val quiet = MicCalibration.recommend(List(30) { 150 }, voice)!!
        val noisy = MicCalibration.recommend(List(30) { 1_500 }, voice)!!

        assertTrue(noisy.threshold > quiet.threshold)
        assertTrue(noisy.sensitivity < quiet.sensitivity)
    }

    @Test
    fun voiceTooCloseToNoiseIsRejected() {
        assertNull(MicCalibration.recommend(List(30) { 1_000 }, List(30) { 1_200 }))
        assertNull(MicCalibration.recommend(List(30) { 100 }, List(30) { 400 }))
    }

    @Test
    fun emptySamplesAreRejected() {
        assertNull(MicCalibration.recommend(emptyList(), List(5) { 3_000 }))
        assertNull(MicCalibration.recommend(List(5) { 100 }, emptyList()))
    }

    @Test
    fun veryLoudVoiceStaysWithinSensitivityRange() {
        val result = MicCalibration.recommend(List(30) { 100 }, List(30) { 30_000 })!!

        assertTrue(result.sensitivity in 0..100)
        assertTrue(result.threshold in 500..4_000)
    }
}
