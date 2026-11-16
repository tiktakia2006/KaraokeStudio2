package com.example.karaokestudio.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class DemoPreset(
    val slug: String,
    val bpm: Int,
    val rootMidi: Int,
    val minor: Boolean,
    val seconds: Int
)

object DemoAudioGenerator {

    private const val SAMPLE_RATE = 22_050

    val presets: List<DemoPreset> = listOf(
        DemoPreset("morning-wave", bpm = 96, rootMidi = 57, minor = false, seconds = 48),
        DemoPreset("night-city", bpm = 84, rootMidi = 52, minor = true, seconds = 50),
        DemoPreset("summer-road", bpm = 112, rootMidi = 55, minor = false, seconds = 44),
        DemoPreset("rain-window", bpm = 72, rootMidi = 50, minor = true, seconds = 52),
        DemoPreset("neon-dance", bpm = 124, rootMidi = 57, minor = true, seconds = 40),
        DemoPreset("quiet-river", bpm = 78, rootMidi = 53, minor = false, seconds = 48),
        DemoPreset("star-path", bpm = 100, rootMidi = 55, minor = false, seconds = 46),
        DemoPreset("mountain-wind", bpm = 108, rootMidi = 52, minor = true, seconds = 44),
        DemoPreset("autumn-waltz", bpm = 76, rootMidi = 57, minor = false, seconds = 52),
        DemoPreset("electric-heart", bpm = 120, rootMidi = 55, minor = true, seconds = 40)
    )

    fun presetFor(slug: String): DemoPreset? = presets.firstOrNull { it.slug == slug }

    private val MAJOR = arrayOf(intArrayOf(0, 4, 7), intArrayOf(7, 11, 14), intArrayOf(9, 12, 16), intArrayOf(5, 9, 12))
    private val MINOR = arrayOf(intArrayOf(0, 3, 7), intArrayOf(8, 12, 15), intArrayOf(3, 7, 10), intArrayOf(10, 14, 17))
    private val ARPEGGIO = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)

    fun render(preset: DemoPreset, target: File) {
        target.parentFile?.mkdirs()
        val pcm = synthesize(preset)
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(wavHeader(pcm.size * 2))
            out.write(toBytes(pcm))
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun synthesize(p: DemoPreset): ShortArray {
        val total = SAMPLE_RATE * p.seconds
        val mix = FloatArray(total)
        val beat = SAMPLE_RATE * 60.0 / p.bpm
        val step = beat / 2
        val chords = if (p.minor) MINOR else MAJOR
        val steps = (total / step).toInt()
        val noise = Random(p.slug.hashCode().toLong())

        for (i in 0 until steps) {
            val start = (i * step).toInt()
            val chord = chords[(i / 8) % chords.size]
            val inBar = i % 8
            addTone(mix, start, midiToHz(p.rootMidi + 12 + chord[ARPEGGIO[inBar]]), 0.20f, (step * 2).toInt(), 5.0)
            if (inBar == 0 || inBar == 4) {
                addTone(mix, start, midiToHz(p.rootMidi - 12 + chord[0]), 0.32f, (beat * 2).toInt(), 2.0)
            }
            if (inBar % 2 == 0) addTick(mix, start, if (inBar == 0) 0.16f else 0.09f, noise)
        }

        var peak = 0f
        for (v in mix) peak = maxOf(peak, abs(v))
        val gain = if (peak > 0.85f) 0.85f / peak else 1f
        val fade = SAMPLE_RATE * 3 / 2
        val out = ShortArray(total)
        for (i in 0 until total) {
            val fadeOut = if (i > total - fade) (total - i).toFloat() / fade else 1f
            val v = (mix[i] * gain * fadeOut).coerceIn(-1f, 1f)
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun addTone(buf: FloatArray, start: Int, freq: Double, amp: Float, length: Int, decay: Double) {
        val end = min(buf.size, start + length)
        val w = 2 * PI * freq / SAMPLE_RATE
        for (i in start until end) {
            val n = i - start
            val t = n.toDouble() / SAMPLE_RATE
            val envelope = min(1.0, t / 0.004) * exp(-decay * t)
            val wave = sin(w * n) + 0.35 * sin(2 * w * n) + 0.12 * sin(3 * w * n)
            buf[i] += (amp * envelope * wave).toFloat()
        }
    }

    private fun addTick(buf: FloatArray, start: Int, amp: Float, rnd: Random) {
        val n = SAMPLE_RATE * 12 / 1000
        for (j in 0 until n) {
            val idx = start + j
            if (idx >= buf.size) break
            buf[idx] += amp * (rnd.nextFloat() * 2f - 1f) * (1f - j.toFloat() / n)
        }
    }

    private fun wavHeader(dataBytes: Int): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        return buf.array()
    }

    private fun toBytes(pcm: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.asShortBuffer().put(pcm)
        return buf.array()
    }
}
