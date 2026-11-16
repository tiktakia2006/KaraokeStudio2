package com.example.karaokestudio.audio

import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

class MicCalibrator(
    private val recorderFactory: () -> VoiceRecorder,
    private val cacheDir: File
) {

    suspend fun sample(durationMs: Long = SAMPLE_MS): List<Int> {
        val recorder = recorderFactory()
        val file = File.createTempFile("calibration", ".m4a", cacheDir)
        if (!recorder.start(file)) {
            file.delete()
            throw IOException("Не вдалося увімкнути мікрофон")
        }
        val levels = ArrayList<Int>()
        try {
            delay(WARM_UP_MS)
            recorder.maxAmplitude()
            repeat((durationMs / TICK_MS).toInt()) {
                delay(TICK_MS)
                levels.add(recorder.maxAmplitude())
            }
        } finally {
            recorder.cancel()
            file.delete()
        }
        return levels
    }

    companion object {
        const val SAMPLE_MS = 3_000L
        private const val WARM_UP_MS = 200L
        private const val TICK_MS = 100L
    }
}
