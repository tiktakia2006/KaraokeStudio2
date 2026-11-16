package com.example.karaokestudio.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.SongCard
import com.example.karaokestudio.ui.components.SyncBanner
import com.example.karaokestudio.ui.components.UiStateContent

@Composable
fun CatalogScreen(
    onOpenSong: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onAddSong: () -> Unit,
    viewModel: CatalogViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val currentSort = (state as? UiState.Success)?.data?.sort

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Каталог", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::refreshCatalog) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Оновити каталог з сервера")
            }
            IconButton(onClick = onAddSong) { Icon(Icons.Rounded.Add, contentDescription = "Додати власну пісню") }
            IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, contentDescription = "Пошук") }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Сортування")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                viewModel.changeSort(order)
                                menuOpen = false
                            },
                            trailingIcon = {
                                if (currentSort == order) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Обрано")
                                }
                            }
                        )
                    }
                }
            }
        }
        SyncBanner(status = syncStatus, offline = offline, onRetry = viewModel::refreshCatalog)
        UiStateContent(state, onRetry = viewModel::retry) { data ->
            Column(Modifier.fillMaxSize()) {
                GenreChips(
                    genres = data.genres,
                    selected = data.selectedGenre,
                    onSelect = viewModel::selectGenre
                )
                if (data.songs.isEmpty()) {
                    EmptyView(
                        icon = Icons.Rounded.MusicNote,
                        title = "У цьому жанрі пісень немає",
                        subtitle = "Оберіть інший жанр або покажіть усі пісні.",
                        actionText = "Показати всі",
                        onAction = { viewModel.selectGenre(null) }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(data.songs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                isFavorite = song.id in data.favoriteIds,
                                onClick = { onOpenSong(song.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChips(genres: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Усі") })
        }
        items(genres) { genre ->
            FilterChip(selected = selected == genre, onClick = { onSelect(genre) }, label = { Text(genre) })
        }
    }
}
