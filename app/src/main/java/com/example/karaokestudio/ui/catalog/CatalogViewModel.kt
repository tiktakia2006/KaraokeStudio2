package com.example.karaokestudio.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.network.NetworkMonitor
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.data.sync.SyncCoordinator
import com.example.karaokestudio.data.sync.SyncStatus
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

enum class SortOrder(val label: String) {
    TITLE("За назвою"),
    ARTIST("За виконавцем"),
    DURATION("За тривалістю")
}

data class CatalogData(
    val songs: List<Song>,
    val genres: List<String>,
    val selectedGenre: String?,
    val sort: SortOrder,
    val favoriteIds: Set<Long>
)

class CatalogViewModel(
    songRepository: SongRepository,
    private val favoriteRepository: FavoriteRepository,
    private val syncCoordinator: SyncCoordinator,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val retryTick = MutableStateFlow(0)
    private val genre = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(SortOrder.TITLE)

    val state: StateFlow<UiState<CatalogData>> = uiStateOf(retryTick) {
        combine(
            songRepository.observeAll(),
            favoriteRepository.observeIds(),
            genre,
            sort
        ) { songs, favorites, selectedGenre, order ->
            val collator = Collator.getInstance(Locale.forLanguageTag("uk-UA"))
            val filtered = if (selectedGenre == null) songs else songs.filter { it.genre == selectedGenre }
            val sorted = when (order) {
                SortOrder.TITLE -> filtered.sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
                SortOrder.ARTIST -> filtered.sortedWith(Comparator { a, b -> collator.compare(a.artist, b.artist) })
                SortOrder.DURATION -> filtered.sortedBy { it.durationMs }
            }
            CatalogData(sorted, songs.map { it.genre }.distinct().sorted(), selectedGenre, order, favorites)
        }
    }

    val syncStatus: StateFlow<SyncStatus> = syncCoordinator.status

    val offline: StateFlow<Boolean> = networkMonitor.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refreshCatalog() {
        syncCoordinator.syncNow()
    }

    fun retry() {
        retryTick.value++
    }

    fun selectGenre(value: String?) {
        genre.value = value
    }

    fun changeSort(order: SortOrder) {
        sort.value = order
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { favoriteRepository.toggle(songId) }
    }
}
