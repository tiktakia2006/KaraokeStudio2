package com.example.karaokestudio.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.online.OnlineSearchRepository
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.LeaderboardEntryDto
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import com.example.karaokestudio.ui.navigation.ARG_SONG_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SongDetailData(
    val song: Song?,
    val isFavorite: Boolean,
    val performances: List<Performance>
)

sealed interface LeaderboardUi {
    data object Hidden : LeaderboardUi
    data object Loading : LeaderboardUi
    data class Loaded(
        val entries: List<LeaderboardEntryDto>,
        val me: LeaderboardEntryDto?,
        val total: Int,
        val signedIn: Boolean
    ) : LeaderboardUi

    data class Failed(val message: String) : LeaderboardUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val favoriteRepository: FavoriteRepository,
    performanceRepository: PerformanceRepository,
    onlineRepository: OnlineSearchRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val songId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_SONG_ID))
    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<SongDetailData>> = uiStateOf(retryTick) {
        combine(
            songRepository.observe(songId),
            favoriteRepository.observeIsFavorite(songId),
            performanceRepository.observeBySong(songId)
        ) { song, favorite, performances -> SongDetailData(song, favorite, performances) }
    }

    private val leaderboardTick = MutableStateFlow(0)

    val leaderboard: StateFlow<LeaderboardUi> = combine(
        songRepository.observe(songId),
        sessionStore.state,
        leaderboardTick
    ) { song, session, _ -> Triple(song?.slug, session.onlineFeatures, session.signedIn) }
        .flatMapLatest { (slug, online, signedIn) ->
            if (slug == null || !online) {
                flow { emit(LeaderboardUi.Hidden) }
            } else {
                flow {
                    emit(LeaderboardUi.Loading)
                    try {
                        val board = onlineRepository.leaderboard(slug)
                        emit(LeaderboardUi.Loaded(board.items, board.me, board.total, signedIn))
                    } catch (e: ApiException) {
                        emit(LeaderboardUi.Failed(ApiErrorMessages.describe(e)))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUi.Hidden)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun retry() {
        retryTick.value++
    }

    fun reloadLeaderboard() {
        leaderboardTick.value++
    }

    fun toggleFavorite() {
        viewModelScope.launch { favoriteRepository.toggle(songId) }
    }

    fun deleteSong(onDone: () -> Unit) {
        viewModelScope.launch {
            val song = songRepository.get(songId) ?: return@launch
            try {
                songRepository.deleteUserSong(song)
                onDone()
            } catch (e: Exception) {
                _messages.send("Не вдалося видалити пісню: ${e.message ?: "невідома помилка"}")
            }
        }
    }
}
