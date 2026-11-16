package com.example.karaokestudio.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.PerformanceRow
import com.example.karaokestudio.ui.components.SectionHeader
import com.example.karaokestudio.ui.components.SongTile
import com.example.karaokestudio.ui.components.StatCard
import com.example.karaokestudio.ui.components.UiStateContent
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenSong: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenResult: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, onRetry = viewModel::retry) { data ->
        HomeContent(data, onOpenSong, onOpenSearch, onOpenCatalog, onOpenHistory, onOpenResult)
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onOpenSong: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenResult: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text("Привіт, ${data.nickname}!", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Оберіть пісню та заспівайте",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { SearchPill(onOpenSearch) }
        item {
            val summary = data.summary
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Виконань", summary.totalCount.toString(), Icons.Rounded.Mic, Modifier.weight(1f))
                StatCard("Найкраща", summary.bestScore?.toString() ?: "—", Icons.Rounded.Star, Modifier.weight(1f))
                StatCard(
                    "Середня",
                    summary.averageScore?.roundToInt()?.toString() ?: "—",
                    Icons.Rounded.BarChart,
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Column {
                SectionHeader("Рекомендовані пісні", actionText = "Усі пісні", onAction = onOpenCatalog)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.featured, key = { it.id }) { song ->
                        SongTile(song, onClick = { onOpenSong(song.id) })
                    }
                }
            }
        }
        if (data.userSongs.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Мої пісні")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.userSongs, key = { it.id }) { song ->
                            SongTile(song, onClick = { onOpenSong(song.id) })
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(
                "Нещодавні виконання",
                actionText = if (data.recent.isNotEmpty()) "Уся історія" else null,
                onAction = onOpenHistory
            )
        }
        if (data.recent.isEmpty()) {
            item { EmptyRecent(onOpenCatalog) }
        } else {
            items(data.recent, key = { it.performance.id }) { details ->
                PerformanceRow(
                    details,
                    onClick = { onOpenResult(details.performance.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text("Знайти пісню або виконавця", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRecent(onOpenCatalog: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Історія поки порожня", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Заспівайте першу пісню — результат з'явиться тут.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenCatalog) { Text("Обрати пісню") }
        }
    }
}
