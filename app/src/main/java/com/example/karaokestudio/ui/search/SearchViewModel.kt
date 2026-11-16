package com.example.karaokestudio.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(val query: String, val songs: List<Song>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    songRepository: SongRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<UiState<SearchResults>> = _query
        .debounce(250)
        .map { SongRepository.normalizeQuery(it) }
        .distinctUntilChanged()
        .flatMapLatest { normalized ->
            songRepository.search(normalized).map<List<Song>, UiState<SearchResults>> {
                UiState.Success(SearchResults(normalized, it))
            }
        }
        .catch { emit(UiState.Error(it.message ?: "Невідома помилка")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Success(SearchResults("", emptyList())))

    val favoriteIds: StateFlow<Set<Long>> = favoriteRepository.observeIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun onQueryChange(value: String) {
        _query.value = value.take(60)
    }

    fun isUpToDate(currentQuery: String, results: SearchResults): Boolean =
        results.query == SongRepository.normalizeQuery(currentQuery)

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { favoriteRepository.toggle(songId) }
    }
}
