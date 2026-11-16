package com.example.karaokestudio.ui.karaoke

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.audio.AudioFileManager
import com.example.karaokestudio.audio.KaraokePlayer
import com.example.karaokestudio.audio.VoiceRecorder
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.data.settings.AppSettings
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.domain.lyrics.LrcParser
import com.example.karaokestudio.domain.lyrics.LyricLine
import com.example.karaokestudio.domain.scoring.ScoreCalculator
import com.example.karaokestudio.domain.scoring.VoiceSample
import com.example.karaokestudio.ui.navigation.ARG_SONG_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class KaraokePhase { LOADING, READY, COUNTDOWN, SINGING, PAUSED, SAVING, ERROR }

data class KaraokeUiState(
    val phase: KaraokePhase = KaraokePhase.LOADING,
    val song: Song? = null,
    val lines: List<LyricLine> = emptyList(),
    val currentLine: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val countdown: Int = 0,
    val semitones: Int = 0,
    val speed: Float = 1f,
    val volume: Int = 100,
    val micEnabled: Boolean = false,
    val level: Float = 0f,
    val lyricsSizeSp: Int = 26,
    val keepScreenOn: Boolean = true,
    val message: String? = null
)

sealed interface KaraokeEvent {
    data class Finished(val performanceId: Long) : KaraokeEvent
    data class Discarded(val reason: String) : KaraokeEvent
}

class KaraokeViewModel(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val performanceRepository: PerformanceRepository,
    private val settingsRepository: SettingsRepository,
    private val audioFiles: AudioFileManager,
    private val player: KaraokePlayer,
    private val recorder: VoiceRecorder
) : ViewModel() {

    private val songId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_SONG_ID))

    private val _state = MutableStateFlow(KaraokeUiState())
    val state: StateFlow<KaraokeUiState> = _state.asStateFlow()

    private val _events = Channel<KaraokeEvent>(Channel.BUFFERED)
    val events: Flow<KaraokeEvent> = _events.receiveAsFlow()

    private var settings = AppSettings()
    private val samples = ArrayList<VoiceSample>()
    private var tickerJob: Job? = null
    private var countdownJob: Job? = null
    private var startedAt = 0L

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            settings = settingsRepository.settings.first()
            val song = songRepository.get(songId)
            if (song == null) {
                fail("Пісню не знайдено в базі даних. Поверніться до каталогу й оберіть іншу.")
                return@launch
            }
            val uri = try {
                audioFiles.resolveAudio(song)
            } catch (e: Exception) {
                null
            }
            if (uri == null) {
                fail("Для цієї пісні немає доступного аудіо.")
                return@launch
            }
            val lines = LrcParser.parse(song.lyrics, song.durationMs)
            _state.update {
                it.copy(
                    song = song,
                    lines = lines,
                    durationMs = song.durationMs,
                    lyricsSizeSp = settings.lyricsSizeSp,
                    keepScreenOn = settings.keepScreenOn,
                    volume = settings.musicVolume
                )
            }
            player.setVolume(settings.musicVolume / 100f)
            player.load(uri)
            val ready = withTimeoutOrNull(15_000) { player.state.first { it.isReady || it.error != null } }
            when {
                ready == null -> fail("Аудіо не встигло завантажитися. Спробуйте ще раз.")
                ready.error != null -> fail(ready.error)
                else -> _state.update {
                    it.copy(
                        phase = KaraokePhase.READY,
                        durationMs = ready.durationMs.takeIf { d -> d > 0 } ?: song.durationMs
                    )
                }
            }
        }
    }

    fun reload() {
        tickerJob?.cancel()
        countdownJob?.cancel()
        player.pause()
        recorder.cancel()
        _state.value = KaraokeUiState()
        load()
    }

    fun changeSemitones(delta: Int) {
        val value = (_state.value.semitones + delta).coerceIn(-6, 6)
        player.setTranspose(value)
        _state.update { it.copy(semitones = value) }
    }

    fun setVolume(percent: Int) {
        val value = percent.coerceIn(0, 100)
        player.setVolume(value / 100f)
        _state.update { it.copy(volume = value) }
    }

    fun saveVolume(percent: Int) {
        viewModelScope.launch { settingsRepository.update { it.copy(musicVolume = percent.coerceIn(0, 100)) } }
    }

    fun changeSpeed(speed: Float) {
        player.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun start(useMic: Boolean) {
        if (_state.value.phase != KaraokePhase.READY) return
        countdownJob = viewModelScope.launch {
            for (n in settings.countdownSeconds downTo 1) {
                _state.update { it.copy(phase = KaraokePhase.COUNTDOWN, countdown = n) }
                delay(1_000)
            }
            beginSinging(useMic)
        }
    }

    private fun beginSinging(useMic: Boolean) {
        var micOk = useMic
        var note: String? = null
        if (useMic && !recorder.start(audioFiles.newRecordingFile())) {
            micOk = false
            note = "Мікрофон недоступний, тому виконання буде без оцінки."
        }
        samples.clear()
        startedAt = System.currentTimeMillis()
        player.seekTo(0)
        player.play()
        _state.update {
            it.copy(
                phase = KaraokePhase.SINGING,
                countdown = 0,
                micEnabled = micOk,
                message = note,
                positionMs = 0,
                currentLine = -1
            )
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(ScoreCalculator.FRAME_MS)
                val playerState = player.state.value
                if (playerState.error != null) {
                    fail(playerState.error)
                    break
                }
                val current = _state.value
                val position = player.currentPositionMs()
                var level = 0f
                if (current.phase == KaraokePhase.SINGING && current.micEnabled) {
                    val amplitude = recorder.maxAmplitude()
                    samples += VoiceSample(position, amplitude)
                    level = (amplitude / 12_000f).coerceIn(0f, 1f)
                }
                _state.update {
                    it.copy(
                        positionMs = position,
                        currentLine = LrcParser.indexAt(it.lines, position),
                        level = level
                    )
                }
                if (playerState.ended) {
                    finish()
                    break
                }
            }
        }
    }

    fun togglePause() {
        val current = _state.value
        when (current.phase) {
            KaraokePhase.SINGING -> {
                player.pause()
                if (current.micEnabled) recorder.pause()
                _state.update { it.copy(phase = KaraokePhase.PAUSED, level = 0f) }
            }
            KaraokePhase.PAUSED -> {
                player.play()
                if (current.micEnabled) recorder.resume()
                _state.update { it.copy(phase = KaraokePhase.SINGING) }
            }
            else -> Unit
        }
    }

    fun pauseIfSinging() {
        if (_state.value.phase == KaraokePhase.SINGING) togglePause()
    }

    fun restart() {
        tickerJob?.cancel()
        countdownJob?.cancel()
        player.pause()
        player.seekTo(0)
        recorder.cancel()
        samples.clear()
        _state.update {
            it.copy(phase = KaraokePhase.READY, positionMs = 0, currentLine = -1, level = 0f, countdown = 0, message = null)
        }
    }

    fun finish() {
        val current = _state.value
        val song = current.song ?: return
        if (current.phase != KaraokePhase.SINGING && current.phase != KaraokePhase.PAUSED) return
        tickerJob?.cancel()
        player.pause()
        val playedMs = maxOf(player.currentPositionMs(), current.positionMs)
        _state.update { it.copy(phase = KaraokePhase.SAVING, level = 0f) }
        viewModelScope.launch { savePerformance(song, playedMs, current.lines, current.micEnabled) }
    }

    fun discard() {
        tickerJob?.cancel()
        countdownJob?.cancel()
        player.pause()
        recorder.cancel()
        _events.trySend(KaraokeEvent.Discarded(""))
    }

    private suspend fun savePerformance(song: Song, playedMs: Long, lines: List<LyricLine>, micUsed: Boolean) {
        val recordedFile = if (micUsed) recorder.stop() else null
        if (playedMs < MIN_SAVE_MS) {
            recordedFile?.delete()
            _events.send(KaraokeEvent.Discarded("Виконання коротше за ${MIN_SAVE_MS / 1000} с, тому його не збережено."))
            return
        }
        val result = if (micUsed && samples.isNotEmpty()) {
            val threshold = ScoreCalculator.thresholdFor(settings.micSensitivity)
            val snapshot = samples.toList()
            withContext(Dispatchers.Default) { ScoreCalculator.calculate(snapshot, lines, threshold) }
        } else {
            null
        }
        val keepFile = recordedFile != null && settings.saveRecordings
        if (!keepFile) recordedFile?.delete()

        val performance = Performance(
            songId = song.id,
            performedAt = startedAt,
            durationMs = playedMs,
            score = result?.score
        )
        val recording = if (keepFile && recordedFile != null) {
            Recording(
                performanceId = 0,
                filePath = recordedFile.absolutePath,
                sizeBytes = recordedFile.length(),
                durationMs = playedMs,
                createdAt = System.currentTimeMillis()
            )
        } else {
            null
        }
        try {
            _events.send(KaraokeEvent.Finished(performanceRepository.save(performance, recording)))
        } catch (e: Exception) {
            recordedFile?.delete()
            fail("Не вдалося зберегти виконання: ${e.message ?: "невідома помилка"}")
        }
    }

    private fun fail(message: String) {
        tickerJob?.cancel()
        countdownJob?.cancel()
        player.pause()
        recorder.cancel()
        _state.update { it.copy(phase = KaraokePhase.ERROR, message = message, level = 0f) }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        countdownJob?.cancel()
        recorder.cancel()
        player.release()
        super.onCleared()
    }

    private companion object {
        const val MIN_SAVE_MS = 5_000L
    }
}
