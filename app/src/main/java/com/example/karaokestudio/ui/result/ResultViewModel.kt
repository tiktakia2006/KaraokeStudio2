package com.example.karaokestudio.ui.result

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.audio.KaraokePlayer
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.data.online.RecordingUploader
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import com.example.karaokestudio.ui.navigation.ARG_PERFORMANCE_ID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUi(
    val available: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null
)

data class UploadUi(
    val available: Boolean = false,
    val busy: Boolean = false,
    val done: Boolean = false,
    val message: String? = null
)

class ResultViewModel(
    savedStateHandle: SavedStateHandle,
    private val performanceRepository: PerformanceRepository,
    private val player: KaraokePlayer,
    private val uploader: RecordingUploader,
    sessionStore: SessionStore
) : ViewModel() {

    private val performanceId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_PERFORMANCE_ID))
    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<PerformanceDetails?>> =
        uiStateOf(retryTick) { performanceRepository.observeDetails(performanceId) }

    private val _playback = MutableStateFlow(PlaybackUi())
    val playback: StateFlow<PlaybackUi> = _playback.asStateFlow()

    private val _upload = MutableStateFlow(UploadUi())
    val upload: StateFlow<UploadUi> = _upload.asStateFlow()

    private var prepared = false
    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                _upload.update { it.copy(available = session.signedIn && session.onlineFeatures) }
            }
        }
    }

    fun uploadRecording(clientId: String, filePath: String) {
        if (_upload.value.busy) return
        _upload.update { it.copy(busy = true, done = false, message = null) }
        viewModelScope.launch {
            try {
                uploader.upload(clientId, filePath)
                _upload.update { it.copy(busy = false, done = true, message = "Запис надіслано на сервер.") }
            } catch (e: ApiException) {
                _upload.update { it.copy(busy = false, done = false, message = ApiErrorMessages.describe(e)) }
            }
        }
    }

    fun retry() {
        retryTick.value++
    }

    fun prepareRecording(recording: Recording?) {
        if (prepared || recording == null) return
        prepared = true
        val file = File(recording.filePath)
        if (!file.exists()) {
            _playback.value = PlaybackUi(error = "Файл запису не знайдено на пристрої.")
            return
        }
        player.load(Uri.fromFile(file))
        _playback.value = PlaybackUi(available = true, durationMs = recording.durationMs)
    }

    fun togglePlayback() {
        val current = _playback.value
        if (!current.available) return
        if (current.isPlaying) {
            player.pause()
            tickerJob?.cancel()
            _playback.update { it.copy(isPlaying = false) }
            return
        }
        if (player.state.value.ended) player.seekTo(0)
        player.play()
        _playback.update { it.copy(isPlaying = true, error = null) }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val playerState = player.state.value
                if (playerState.error != null) {
                    _playback.update { it.copy(isPlaying = false, error = playerState.error) }
                    break
                }
                if (playerState.ended) {
                    _playback.update { it.copy(isPlaying = false, positionMs = 0) }
                    break
                }
                _playback.update {
                    it.copy(
                        positionMs = player.currentPositionMs(),
                        durationMs = playerState.durationMs.takeIf { d -> d > 0 } ?: it.durationMs
                    )
                }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            player.pause()
            performanceRepository.delete(performanceId)
            onDone()
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        player.release()
        super.onCleared()
    }
}
