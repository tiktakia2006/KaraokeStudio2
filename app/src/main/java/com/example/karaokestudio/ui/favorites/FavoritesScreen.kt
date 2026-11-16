package com.example.karaokestudio.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.SongCard
import com.example.karaokestudio.ui.components.UiStateContent
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onOpenSong: (Long) -> Unit,
    onOpenCatalog: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Text(
                "Обране",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            UiStateContent(state, onRetry = viewModel::retry) { songs ->
                if (songs.isEmpty()) {
                    EmptyView(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = "Обраних пісень поки немає",
                        subtitle = "Натисніть на сердечко біля пісні, щоб додати її сюди.",
                        actionText = "До каталогу",
                        onAction = onOpenCatalog
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                isFavorite = true,
                                onClick = { onOpenSong(song.id) },
                                onToggleFavorite = {
                                    viewModel.remove(song.id)
                                    scope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = "«${song.title}» прибрано з обраного",
                                            actionLabel = "Повернути",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(song.id)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
