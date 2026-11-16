package com.example.karaokestudio.ui.addsong

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.online.LyricsOffer
import com.example.karaokestudio.data.online.MetadataResult
import com.example.karaokestudio.data.online.MetadataSuggestion
import com.example.karaokestudio.domain.lyrics.LrcParser
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.CoverArt
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.formatDuration
import com.example.karaokestudio.ui.theme.Danger

private val TIMESTAMP = Regex("""\[\d{1,3}:\d{2}""")

@Composable
fun AddSongScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddSongViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onAudioPicked(uri)
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onLyricsFilePicked(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddSongEvent.Saved -> onSaved(event.songId)
            }
        }
    }

    val lyricsHint = remember(state.lyrics, state.audioDurationMs) {
        val lines = LrcParser.parse(state.lyrics, maxOf(state.audioDurationMs, 1L)).count { it.text.isNotBlank() }
        when {
            state.lyrics.isBlank() -> "Без тексту: під час співу рядків не буде."
            TIMESTAMP.containsMatchIn(state.lyrics) -> "Розпізнано рядків із часовими мітками: $lines."
            else -> "Часових міток немає: $lines рядків розподіляться рівномірно за тривалістю пісні."
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar("Нова пісня", onBack = onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Назва") },
                singleLine = true,
                supportingText = { Text("Обов'язково") }
            )
            OutlinedTextField(
                value = state.artist,
                onValueChange = viewModel::onArtistChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Виконавець") },
                singleLine = true
            )
            OutlinedTextField(
                value = state.genre,
                onValueChange = viewModel::onGenreChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Жанр") },
                singleLine = true
            )

            OnlineCard(
                state = state,
                onSuggest = viewModel::suggestMetadata,
                onFindLyrics = viewModel::findLyrics,
                onRemoveCover = viewModel::removeCover
            )

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Аудіофайл", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    when {
                        state.importing -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Копіюємо файл…")
                        }
                        state.audioName != null -> Text(
                            "${state.audioName}, ${formatDuration(state.audioDurationMs)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        else -> Text(
                            "Файл не обрано. Підтримуються mp3, m4a, wav, ogg, flac, до 60 МБ.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }, enabled = !state.importing) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.audioName == null) "Обрати файл" else "Замінити файл")
                    }
                }
            }

            Column {
                OutlinedTextField(
                    value = state.lyrics,
                    onValueChange = viewModel::onLyricsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текст пісні") },
                    placeholder = { Text("[00:05.00]Перший рядок\n[00:10.00]Другий рядок") },
                    minLines = 6,
                    maxLines = 12,
                    supportingText = { Text(lyricsHint) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { lyricsPicker.launch(arrayOf("*/*")) }) {
                    Text("Завантажити текст із файлу (.lrc, .txt)")
                }
            }

            Text(
                "Додавайте лише ті пісні, на які маєте право. Копія файлу зберігається тільки на цьому пристрої.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        }

        state.metadata?.let { result ->
            MetadataDialog(result, onPick = viewModel::applySuggestion, onDismiss = viewModel::dismissMetadata)
        }
        state.lyricsOffer?.let { offer ->
            LyricsDialog(offer, onAccept = viewModel::acceptLyrics, onDismiss = viewModel::dismissLyrics)
        }

        GradientButton(
            text = if (state.saving) "Зберігаємо…" else "Зберегти пісню",
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
private fun OnlineCard(
    state: AddSongUiState,
    onSuggest: () -> Unit,
    onFindLyrics: () -> Unit,
    onRemoveCover: () -> Unit
) {
    val idle = state.onlineBusy == OnlineBusy.NONE
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Дані з інтернету", style = MaterialTheme.typography.titleMedium)
            if (state.onlineHint != null) {
                Text(
                    state.onlineHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.coverUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverArt(state.coverUrl, Modifier.size(72.dp))
                    Text("Обкладинка буде збережена разом із піснею.", modifier = Modifier.weight(1f))
                    TextButton(onClick = onRemoveCover) { Text("Прибрати") }
                }
            }
            OutlinedButton(onClick = onSuggest, enabled = state.onlineAvailable && idle, modifier = Modifier.fillMaxWidth()) {
                Text("Підказати метадані")
            }
            OutlinedButton(onClick = onFindLyrics, enabled = state.onlineAvailable && idle, modifier = Modifier.fillMaxWidth()) {
                Text("Знайти текст онлайн")
            }
            if (!idle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.onlineBusy == OnlineBusy.LYRICS) "Шукаємо текст…" else "Шукаємо метадані…")
                }
            }
            state.onlineError?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun MetadataDialog(
    result: MetadataResult,
    onPick: (MetadataSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оберіть пісню") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(result.items) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CoverArt(item.coverUrl, Modifier.size(48.dp), corner = 8.dp)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(item.artist, item.album, item.genre).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    "Джерело: ${result.sourceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}

@Composable
private fun LyricsDialog(offer: LyricsOffer, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val preview = remember(offer) {
        offer.lyrics.lines()
            .map { line -> line.replace(TIMESTAMP_PREFIX, "").trim() }
            .filter { it.isNotEmpty() }
            .take(6)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Знайдено текст") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${offer.trackName} — ${offer.artistName}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (offer.synced) "З часовими мітками" else "Без часових міток",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                preview.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Джерело: ${offer.sourceName}. Поточний текст у полі буде замінено.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Використати") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

private val TIMESTAMP_PREFIX = Regex("""^(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?])+""")
