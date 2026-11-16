package com.example.karaokestudio.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.ErrorView
import com.example.karaokestudio.ui.components.LoadingView
import com.example.karaokestudio.ui.components.SongCard

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("Назва пісні або виконавець") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистити поле")
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
            Spacer(Modifier.width(12.dp))
        }

        when (val state = results) {
            is UiState.Loading -> LoadingView(text = "Шукаємо…")
            is UiState.Error -> ErrorView(state.message, onRetry = { viewModel.onQueryChange(query) })
            is UiState.Success -> {
                val data = state.data
                when {
                    query.isBlank() -> EmptyView(
                        icon = Icons.Rounded.Search,
                        title = "Знайдіть свою пісню",
                        subtitle = "Введіть частину назви або ім'я виконавця."
                    )
                    !viewModel.isUpToDate(query, data) -> LoadingView(text = "Шукаємо…")
                    data.songs.isEmpty() -> EmptyView(
                        icon = Icons.Rounded.MusicNote,
                        title = "Нічого не знайдено",
                        subtitle = "За запитом «${query.trim()}» пісень немає. Перевірте написання або перегляньте весь каталог."
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(data.songs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                isFavorite = song.id in favorites,
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
