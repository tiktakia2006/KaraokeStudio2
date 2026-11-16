package com.example.karaokestudio.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.online.RankingRepository
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.PlayerRankDto
import com.example.karaokestudio.data.remote.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RankingUi {
    data object Loading : RankingUi
    data object Disabled : RankingUi
    data class Loaded(
        val players: List<PlayerRankDto>,
        val me: PlayerRankDto?,
        val total: Int,
        val signedIn: Boolean
    ) : RankingUi

    data class Failed(val message: String) : RankingUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class RankingViewModel(
    repository: RankingRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val tick = MutableStateFlow(0)

    val state: StateFlow<RankingUi> = combine(
        sessionStore.state.map { it.onlineFeatures to it.signedIn }.distinctUntilChanged(),
        tick
    ) { flags, _ -> flags }
        .flatMapLatest { (online, signedIn) ->
            if (!online) {
                flow { emit(RankingUi.Disabled) }
            } else {
                flow {
                    emit(RankingUi.Loading)
                    try {
                        val board = repository.global()
                        emit(RankingUi.Loaded(board.items, board.me, board.total, signedIn))
                    } catch (e: ApiException) {
                        emit(RankingUi.Failed(ApiErrorMessages.describe(e)))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RankingUi.Loading)

    fun reload() {
        tick.value++
    }
}
