package com.example.karaokestudio.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow

class ExoKaraokePlayer(context: Context) : KaraokePlayer {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
    }

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var semitones = 0
    private var speed = 1f

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        isReady = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED,
                        ended = playbackState == Player.STATE_ENDED,
                        durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(error = "Помилка відтворення (${error.errorCodeName})") }
            }
        })
    }

    override fun load(uri: Uri) {
        _state.value = PlayerState()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun currentPositionMs(): Long = player.currentPosition

    override fun setTranspose(semitones: Int) {
        this.semitones = semitones
        applyParameters()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        applyParameters()
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    private fun applyParameters() {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }

    override fun release() = player.release()
}
