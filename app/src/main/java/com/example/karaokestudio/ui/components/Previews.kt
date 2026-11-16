package com.example.karaokestudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.ui.theme.KaraokeTheme

private val previewSong = Song(
    id = 1,
    title = "Ранкова хвиля",
    artist = "Demo Band",
    durationMs = 48_000,
    lyrics = "",
    audioSource = "demo:morning-wave",
    coverSource = "gradient:0",
    genre = "Поп",
    isDemo = true
)

private const val DARK = 0xFF0E0B1A

@Preview(name = "Картки пісень", showBackground = true, backgroundColor = DARK)
@Composable
private fun SongCardPreview() {
    KaraokeTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SongCard(previewSong, isFavorite = true, onClick = {}, onToggleFavorite = {}, modifier = Modifier.fillMaxWidth())
            SongCard(
                previewSong.copy(title = "Дуже довга назва пісні, яка не вміщується в один рядок", isDemo = false),
                isFavorite = false,
                onClick = {},
                onToggleFavorite = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Результат: оцінка", showBackground = true, backgroundColor = DARK)
@Composable
private fun ScorePreview() {
    KaraokeTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreRing(score = 86)
            StarsRow(stars = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreChip(86)
                ScoreChip(60)
                ScoreChip(30)
                ScoreChip(null)
            }
        }
    }
}

@Preview(name = "Картки статистики", showBackground = true, backgroundColor = DARK)
@Composable
private fun StatCardsPreview() {
    KaraokeTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Виконань", "12", Icons.Rounded.Mic, Modifier.weight(1f))
            StatCard("Найкраща", "92", Icons.Rounded.Star, Modifier.weight(1f))
        }
    }
}

@Preview(name = "Кнопки", showBackground = true, backgroundColor = DARK)
@Composable
private fun ButtonsPreview() {
    KaraokeTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton("Співати", onClick = {}, icon = Icons.Rounded.Mic, modifier = Modifier.fillMaxWidth())
            GradientButton("Недоступно", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Порожній стан і помилка", showBackground = true, backgroundColor = DARK, heightDp = 640)
@Composable
private fun StatesPreview() {
    KaraokeTheme {
        Column {
            EmptyView(
                icon = Icons.Rounded.History,
                title = "Історія порожня",
                subtitle = "Після першого виконання тут з'являться результати.",
                actionText = "Обрати пісню",
                onAction = {},
                modifier = Modifier.weight(1f)
            )
            ErrorView("Не вдалося прочитати базу даних", onRetry = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Рядок історії", showBackground = true, backgroundColor = DARK)
@Composable
private fun PerformanceRowPreview() {
    KaraokeTheme {
        val performance = Performance(id = 1, songId = 1, performedAt = 1_700_000_000_000, durationMs = 48_000, score = 86)
        val recording = Recording(id = 1, performanceId = 1, filePath = "/x.m4a", sizeBytes = 400_000, durationMs = 48_000, createdAt = 1)
        Column(Modifier.padding(16.dp)) {
            PerformanceRow(PerformanceDetails(performance, previewSong, recording), onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
