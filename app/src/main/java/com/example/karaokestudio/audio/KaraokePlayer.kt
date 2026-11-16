package com.example.karaokestudio.audio

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val ended: Boolean = false,
    val durationMs: Long = 0,
    val error: String? = null
)

interface KaraokePlayer {
    val state: StateFlow<PlayerState>
    fun load(uri: Uri)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun currentPositionMs(): Long

    fun setTranspose(semitones: Int)

    fun setSpeed(speed: Float)

    fun setVolume(volume: Float)
    fun release()
}
