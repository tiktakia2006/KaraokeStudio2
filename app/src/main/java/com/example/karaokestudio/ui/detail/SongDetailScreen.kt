package com.example.karaokestudio.ui.detail

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.domain.lyrics.LrcParser
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.CoverArt
import com.example.karaokestudio.ui.components.DemoBadge
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.ScoreChip
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.SectionHeader
import com.example.karaokestudio.ui.components.UiStateContent
import com.example.karaokestudio.ui.components.formatDateTime
import com.example.karaokestudio.ui.components.formatDuration
import com.example.karaokestudio.ui.theme.Pink

@Composable
fun SongDetailScreen(
    onBack: () -> Unit,
    onSing: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
    viewModel: SongDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val title = ((state as? UiState.Success)?.data?.song?.title) ?: "Пісня"

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = title,
            onBack = onBack,
            actions = {
                val data = (state as? UiState.Success)?.data
                val song = data?.song
                if (data != null && song != null) {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (data.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (data.isFavorite) "Прибрати з обраного" else "Додати до обраного",
                            tint = if (data.isFavorite) Pink else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!song.isDemo && song.slug == null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Видалити пісню")
                        }
                    }
                }
            }
        )
        UiStateContent(state, onRetry = viewModel::retry) { data ->
            val song = data.song
            if (song == null) {
                EmptyView(
                    icon = Icons.Rounded.MusicNote,
                    title = "Пісню не знайдено",
                    subtitle = "Можливо, її більше немає в каталозі. Поверніться назад і оберіть іншу.",
                    actionText = "Назад",
                    onAction = onBack
                )
            } else {
                DetailContent(song, data, leaderboard, viewModel::reloadLeaderboard, onSing, onOpenResult)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Видалити пісню?",
            text = "Пісню, її аудіофайл, виконання та записи голосу буде видалено без можливості відновлення.",
            confirmText = "Видалити",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSong(onBack)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DetailContent(
    song: Song,
    data: SongDetailData,
    leaderboard: LeaderboardUi,
    onReloadLeaderboard: () -> Unit,
    onSing: (Long) -> Unit,
    onOpenResult: (Long) -> Unit
) {
    val previewLines = remember(song.id) {
        LrcParser.parse(song.lyrics, song.durationMs).map { it.text }.filter { it.isNotBlank() }.take(4)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CoverArt(song.coverSource, Modifier.fillMaxWidth().aspectRatio(1.6f), corner = 24.dp)
        Column {
            Text(song.title, style = MaterialTheme.typography.headlineMedium)
            Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                InfoPill(song.genre)
                InfoPill(formatDuration(song.durationMs))
                if (song.isDemo) DemoBadge()
            }
        }
        GradientButton(
            text = "Співати",
            icon = Icons.Rounded.Mic,
            onClick = { onSing(song.id) },
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            SectionHeader("Текст пісні")
            Spacer(Modifier.height(8.dp))
            if (previewLines.isEmpty()) {
                Text("Для цієї пісні текст відсутній.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                previewLines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (song.isDemo) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Тестова пісня: текст і музика створені для демонстрації застосунку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LeaderboardSection(leaderboard, onReloadLeaderboard)

        Column {
            SectionHeader("Мої виконання")
            Spacer(Modifier.height(8.dp))
            if (data.performances.isEmpty()) {
                Text("Ви ще не співали цю пісню.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val best = data.performances.mapNotNull { it.score }.maxOrNull()
                Text(
                    "Спроб: ${data.performances.size}, найкращий результат: ${best ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                data.performances.take(3).forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenResult(p.id) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDateTime(p.performedAt))
                        ScoreChip(p.score)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LeaderboardSection(state: LeaderboardUi, onReload: () -> Unit) {
    if (state is LeaderboardUi.Hidden) return
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader("Рейтинг гравців") }
            IconButton(onClick = onReload) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Оновити рейтинг")
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            is LeaderboardUi.Loading -> Text("Завантажуємо рейтинг…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LeaderboardUi.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onReload) { Text("Повторити") }
            }
            is LeaderboardUi.Loaded -> LeaderboardRows(state)
            is LeaderboardUi.Hidden -> Unit
        }
    }
}

@Composable
private fun LeaderboardRows(state: LeaderboardUi.Loaded) {
    if (state.entries.isEmpty()) {
        Text(
            "Ще ніхто не отримав оцінку за цю пісню. Станьте першим!",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.entries.forEach { entry ->
            LeaderboardRow(entry.rank, entry.nickname, entry.bestScore, entry.attempts, highlighted = state.me?.rank == entry.rank)
        }
        val me = state.me
        if (me != null && state.entries.none { it.rank == me.rank }) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LeaderboardRow(me.rank, me.nickname, me.bestScore, me.attempts, highlighted = true)
        }
        Text(
            if (state.signedIn) "Усього гравців: ${state.total}" else "Усього гравців: ${state.total}. Увійдіть в обліковий запис, щоб потрапити до рейтингу.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, nickname: String, score: Int, attempts: Int, highlighted: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("$rank", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(nickname, style = MaterialTheme.typography.bodyLarge)
                Text("Спроб: $attempts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ScoreChip(score)
        }
    }
}
