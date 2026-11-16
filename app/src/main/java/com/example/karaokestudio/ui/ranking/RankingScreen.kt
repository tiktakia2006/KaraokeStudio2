package com.example.karaokestudio.ui.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.remote.PlayerRankDto
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.ScreenTopBar

@Composable
fun RankingScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: RankingViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Рейтинг гравців",
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::reload) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Оновити рейтинг")
                }
            }
        )
        when (val current = state) {
            is RankingUi.Loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            is RankingUi.Disabled -> EmptyView(
                icon = Icons.Rounded.CloudOff,
                title = "Онлайн-функції вимкнено",
                subtitle = "Увімкніть їх у налаштуваннях, щоб бачити рейтинг гравців.",
                actionText = null,
                onAction = {}
            )
            is RankingUi.Failed -> EmptyView(
                icon = Icons.Rounded.CloudOff,
                title = "Не вдалося завантажити рейтинг",
                subtitle = current.message,
                actionText = "Повторити",
                onAction = viewModel::reload
            )
            is RankingUi.Loaded -> RankingList(current, onOpenAccount)
        }
    }
}

@Composable
private fun RankingList(state: RankingUi.Loaded, onOpenAccount: () -> Unit) {
    if (state.players.isEmpty()) {
        EmptyView(
            icon = Icons.Rounded.EmojiEvents,
            title = "Рейтинг поки порожній",
            subtitle = "Заспівайте пісню з каталогу сервера й отримайте оцінку, щоб стати першим.",
            actionText = if (state.signedIn) null else "Увійти",
            onAction = onOpenAccount
        )
        return
    }
    val me = state.me
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Очки гравця: сума найкращих результатів по різних піснях.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.players) { player ->
            PlayerRow(player, highlighted = me != null && me.rank == player.rank && me.nickname == player.nickname)
        }
        if (me != null && state.players.none { it.rank == me.rank && it.nickname == me.nickname }) {
            item { Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { PlayerRow(me, highlighted = true) }
        }
        item {
            Text(
                if (state.signedIn) "Усього гравців: ${state.total}" else "Усього гравців: ${state.total}. Увійдіть в обліковий запис, щоб потрапити до рейтингу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerRow(player: PlayerRankDto, highlighted: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${player.rank}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(player.nickname, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Пісень: ${player.songsCount} · найкращий результат: ${player.bestScore} · спроб: ${player.attempts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${player.totalScore}", style = MaterialTheme.typography.titleLarge)
        }
    }
}
