package com.example.karaokestudio.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel(private val favoriteRepository: FavoriteRepository) : ViewModel() {

    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<List<Song>>> = uiStateOf(retryTick) { favoriteRepository.observeSongs() }

    fun retry() {
        retryTick.value++
    }

    fun remove(songId: Long) {
        viewModelScope.launch { favoriteRepository.remove(songId) }
    }

    fun restore(songId: Long) {
        viewModelScope.launch { favoriteRepository.add(songId) }
    }
}
