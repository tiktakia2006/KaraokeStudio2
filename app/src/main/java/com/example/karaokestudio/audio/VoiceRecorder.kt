package com.example.karaokestudio.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    fun start(file: File): Boolean {
        cancel()
        var candidate: MediaRecorder? = null
        return try {
            candidate = newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = candidate
            outputFile = file
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося почати запис", e)
            candidate?.release()
            file.delete()
            false
        }
    }

    fun maxAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (e: Exception) {
        0
    }

    fun pause() {
        try {
            recorder?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "pause() не вдалося", e)
        }
    }

    fun resume() {
        try {
            recorder?.resume()
        } catch (e: Exception) {
            Log.w(TAG, "resume() не вдалося", e)
        }
    }

    fun stop(): File? {
        val active = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        return try {
            active.stop()
            file
        } catch (e: RuntimeException) {
            Log.w(TAG, "Запис закороткий або пошкоджений", e)
            file?.delete()
            null
        } finally {
            active.release()
        }
    }

    fun cancel() {
        stop()?.delete()
    }

    private companion object {
        const val TAG = "VoiceRecorder"
    }
}
