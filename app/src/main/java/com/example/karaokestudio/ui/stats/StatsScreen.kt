package com.example.karaokestudio.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.local.model.TopSong
import com.example.karaokestudio.data.local.model.UserStats
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.ScoreChip
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.SectionHeader
import com.example.karaokestudio.ui.components.StatCard
import com.example.karaokestudio.ui.components.UiStateContent
import com.example.karaokestudio.ui.components.formatTotalTime
import com.example.karaokestudio.ui.theme.Sky
import com.example.karaokestudio.ui.theme.TextMid
import com.example.karaokestudio.ui.theme.Violet
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Статистика", onBack = onBack)
        UiStateContent(state, onRetry = viewModel::retry) { stats ->
            if (stats.summary.totalCount == 0) {
                EmptyView(
                    icon = Icons.Rounded.BarChart,
                    title = "Статистики поки немає",
                    subtitle = "Заспівайте першу пісню — і тут з'являться ваші результати."
                )
            } else {
                StatsContent(stats, onOpenSong)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: UserStats, onOpenSong: (Long) -> Unit) {
    val summary = stats.summary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Виконань", summary.totalCount.toString(), Icons.Rounded.Mic, Modifier.weight(1f))
            StatCard("Час співу", formatTotalTime(summary.totalDurationMs), Icons.Rounded.Timer, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Середня оцінка",
                summary.averageScore?.roundToInt()?.toString() ?: "—",
                Icons.Rounded.BarChart,
                Modifier.weight(1f)
            )
            StatCard("Найкраща оцінка", summary.bestScore?.toString() ?: "—", Icons.Rounded.Star, Modifier.weight(1f))
        }

        Column {
            SectionHeader("Останні результати")
            Spacer(Modifier.height(12.dp))
            if (stats.recentScores.isEmpty()) {
                Text(
                    "Оцінки з'являються, коли ви співаєте з увімкненим мікрофоном.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ScoreBars(stats.recentScores)
            }
        }

        Column {
            SectionHeader("Найчастіше виконувані")
            Spacer(Modifier.height(8.dp))
            stats.topSongs.forEachIndexed { index, top -> TopSongRow(index + 1, top, onOpenSong) }
        }
    }
}

@Composable
private fun ScoreBars(scores: List<Int>) {
    Row(
        Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        scores.forEach { score ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(score.toString(), style = MaterialTheme.typography.labelSmall, color = TextMid)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((120f * score / 100f).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(Sky, Violet)))
                )
            }
        }
    }
}

@Composable
private fun TopSongRow(rank: Int, top: TopSong, onOpenSong: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpenSong(top.songId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = TextMid, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(top.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${top.artist}, виконань: ${top.plays}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ScoreChip(top.bestScore)
    }
}
