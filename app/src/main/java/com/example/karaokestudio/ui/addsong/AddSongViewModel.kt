package com.example.karaokestudio.ui.addsong

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.audio.AudioFileManager
import com.example.karaokestudio.audio.ImportedAudio
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.online.LyricsOffer
import com.example.karaokestudio.data.online.MetadataResult
import com.example.karaokestudio.data.online.MetadataSuggestion
import com.example.karaokestudio.data.online.OnlineSearchRepository
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.repository.SongRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEFAULT_GENRE = "Власні"
private const val MAX_LYRICS_CHARS = 20_000

enum class OnlineBusy { NONE, LYRICS, METADATA }

data class AddSongUiState(
    val title: String = "",
    val artist: String = "",
    val genre: String = DEFAULT_GENRE,
    val lyrics: String = "",
    val audioName: String? = null,
    val audioDurationMs: Long = 0,
    val importing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val coverUrl: String? = null,
    val onlineAvailable: Boolean = false,
    val onlineHint: String? = null,
    val onlineBusy: OnlineBusy = OnlineBusy.NONE,
    val onlineError: String? = null,
    val metadata: MetadataResult? = null,
    val lyricsOffer: LyricsOffer? = null
) {
    val canSave: Boolean get() = title.isNotBlank() && audioName != null && !importing && !saving
}

sealed interface AddSongEvent {
    data class Saved(val songId: Long) : AddSongEvent
}

class AddSongViewModel(
    private val songRepository: SongRepository,
    private val audioFiles: AudioFileManager,
    private val onlineRepository: OnlineSearchRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private val _events = Channel<AddSongEvent>(Channel.BUFFERED)
    val events: Flow<AddSongEvent> = _events.receiveAsFlow()

    private var pending: ImportedAudio? = null
    private var saved = false

    init {
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                val available = session.signedIn && session.onlineFeatures
                val hint = when {
                    !session.onlineFeatures -> "Онлайн-функції вимкнено в налаштуваннях."
                    !session.signedIn -> "Увійдіть в обліковий запис, щоб користуватися онлайн-пошуком."
                    else -> null
                }
                _state.update { it.copy(onlineAvailable = available, onlineHint = hint) }
            }
        }
    }

    fun onTitleChange(value: String) =
        _state.update { it.copy(title = value.take(SongRepository.MAX_TITLE), error = null) }

    fun onArtistChange(value: String) = _state.update { it.copy(artist = value.take(60)) }

    fun onGenreChange(value: String) = _state.update { it.copy(genre = value.take(30)) }

    fun onLyricsChange(value: String) = _state.update { it.copy(lyrics = value.take(MAX_LYRICS_CHARS)) }

    fun onAudioPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            try {
                val imported = audioFiles.importAudio(uri)
                pending?.file?.delete()
                pending = imported
                _state.update {
                    it.copy(
                        importing = false,
                        audioName = imported.displayName,
                        audioDurationMs = imported.durationMs,
                        title = it.title.ifBlank {
                            imported.displayName.substringBeforeLast('.').take(SongRepository.MAX_TITLE)
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, error = e.message ?: "Не вдалося імпортувати файл") }
            }
        }
    }

    fun onLyricsFilePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = audioFiles.readText(uri)
                _state.update { it.copy(lyrics = text.take(MAX_LYRICS_CHARS), error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не вдалося прочитати текстовий файл") }
            }
        }
    }

    fun suggestMetadata() {
        val current = _state.value
        val query = "${current.title} ${current.artist}".trim()
        if (query.isEmpty()) {
            _state.update { it.copy(onlineError = "Спочатку введіть назву пісні") }
            return
        }
        if (!current.onlineAvailable || current.onlineBusy != OnlineBusy.NONE) return
        _state.update { it.copy(onlineBusy = OnlineBusy.METADATA, onlineError = null) }
        viewModelScope.launch {
            try {
                val result = onlineRepository.suggestMetadata(query)
                _state.update {
                    if (result.items.isEmpty()) {
                        it.copy(onlineBusy = OnlineBusy.NONE, onlineError = "За цим запитом нічого не знайдено")
                    } else {
                        it.copy(onlineBusy = OnlineBusy.NONE, metadata = result)
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(onlineBusy = OnlineBusy.NONE, onlineError = ApiErrorMessages.describe(e)) }
            }
        }
    }

    fun applySuggestion(suggestion: MetadataSuggestion) {
        _state.update {
            it.copy(
                title = suggestion.title.take(SongRepository.MAX_TITLE),
                artist = suggestion.artist.take(60),
                genre = if (it.genre == DEFAULT_GENRE) (suggestion.genre ?: it.genre).take(30) else it.genre,
                coverUrl = suggestion.coverUrl ?: it.coverUrl,
                metadata = null,
                onlineError = null
            )
        }
    }

    fun dismissMetadata() = _state.update { it.copy(metadata = null) }

    fun removeCover() = _state.update { it.copy(coverUrl = null) }

    fun findLyrics() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(onlineError = "Спочатку введіть назву пісні") }
            return
        }
        if (!current.onlineAvailable || current.onlineBusy != OnlineBusy.NONE) return
        _state.update { it.copy(onlineBusy = OnlineBusy.LYRICS, onlineError = null) }
        viewModelScope.launch {
            try {
                val offer = onlineRepository.findLyrics(current.title, current.artist, current.audioDurationMs)
                _state.update {
                    if (offer == null) {
                        it.copy(onlineBusy = OnlineBusy.NONE, onlineError = "Текст для цієї пісні не знайдено")
                    } else {
                        it.copy(onlineBusy = OnlineBusy.NONE, lyricsOffer = offer)
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(onlineBusy = OnlineBusy.NONE, onlineError = ApiErrorMessages.describe(e)) }
            }
        }
    }

    fun acceptLyrics() {
        val offer = _state.value.lyricsOffer ?: return
        _state.update { it.copy(lyrics = offer.lyrics.take(MAX_LYRICS_CHARS), lyricsOffer = null, onlineError = null) }
    }

    fun dismissLyrics() = _state.update { it.copy(lyricsOffer = null) }

    fun save() {
        val current = _state.value
        val audio = pending ?: return
        if (!current.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val song = Song(
                    title = current.title.trim(),
                    artist = current.artist.trim().ifBlank { "Невідомий виконавець" },
                    durationMs = audio.durationMs,
                    lyrics = current.lyrics.trim(),
                    audioSource = Uri.fromFile(audio.file).toString(),
                    coverSource = current.coverUrl ?: "gradient:${Math.floorMod(current.title.hashCode(), 6)}",
                    genre = current.genre.trim().ifBlank { DEFAULT_GENRE },
                    isDemo = false
                )
                val id = songRepository.add(song)
                saved = true
                _events.send(AddSongEvent.Saved(id))
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Не вдалося зберегти пісню") }
            }
        }
    }

    override fun onCleared() {
        if (!saved) pending?.file?.delete()
        super.onCleared()
    }
}
