package com.example.karaokestudio.ui.result

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.domain.scoring.ScoreCalculator
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.CoverArt
import com.example.karaokestudio.ui.components.DemoBadge
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.ScoreRing
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.StarsRow
import com.example.karaokestudio.ui.components.UiStateContent
import com.example.karaokestudio.ui.components.formatDateTime
import com.example.karaokestudio.ui.components.formatDuration
import com.example.karaokestudio.ui.theme.Danger
import com.example.karaokestudio.ui.theme.OutlineColor
import com.example.karaokestudio.ui.theme.Violet
import java.io.File
import java.util.Locale

@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onSingAgain: (Long) -> Unit,
    onOpenCatalog: () -> Unit,
    viewModel: ResultViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val details = (state as? UiState.Success)?.data

    LaunchedEffect(details?.recording?.id) { viewModel.prepareRecording(details?.recording) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Результат",
            onBack = onBack,
            actions = {
                if (details != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Видалити виконання")
                    }
                }
            }
        )
        UiStateContent(state, onRetry = viewModel::retry) { loaded ->
            if (loaded == null) {
                EmptyView(
                    icon = Icons.Rounded.MusicNote,
                    title = "Виконання не знайдено",
                    subtitle = "Можливо, його видалили з історії.",
                    actionText = "До каталогу",
                    onAction = onOpenCatalog
                )
            } else {
                ResultContent(
                    details = loaded,
                    playback = playback,
                    onTogglePlayback = viewModel::togglePlayback,
                    onShare = { loaded.recording?.let { shareRecording(context, it.filePath) } },
                    upload = upload,
                    onUpload = {
                        loaded.recording?.let { viewModel.uploadRecording(loaded.performance.clientId, it.filePath) }
                    },
                    onSingAgain = onSingAgain,
                    onOpenCatalog = onOpenCatalog
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Видалити виконання?",
            text = "Результат і запис голосу (якщо він є) буде видалено без можливості відновлення.",
            confirmText = "Видалити",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ResultContent(
    details: PerformanceDetails,
    playback: PlaybackUi,
    onTogglePlayback: () -> Unit,
    onShare: () -> Unit,
    upload: UploadUi,
    onUpload: () -> Unit,
    onSingAgain: (Long) -> Unit,
    onOpenCatalog: () -> Unit
) {
    val performance = details.performance
    val score = performance.score
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(details.song.coverSource, Modifier.size(48.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(details.song.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    details.song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (score != null) {
            ScoreRing(score)
            StarsRow(ScoreCalculator.starsFor(score))
            Text(ScoreCalculator.titleFor(score), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Оцінка показує, наскільки активно ви співали під час рядків тексту. Точність нот вона не перевіряє.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            InfoCard(
                "Виконання без оцінки",
                "Оцінка рахується за рівнем мікрофона. Цього разу мікрофон не використовувався, тож результату немає."
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Дата", formatDateTime(performance.performedAt))
                DetailRow("Тривалість", formatDuration(performance.durationMs))
                DetailRow(
                    "Запис голосу",
                    details.recording?.let { sizeLabel(it.sizeBytes) } ?: "не збережено"
                )
                if (performance.isDemo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DemoBadge()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Демонстраційне виконання без запису голосу",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (details.recording != null) RecordingPlayer(playback, onTogglePlayback, onShare)
        if (details.recording != null && details.song.slug != null && upload.available) UploadCard(upload, onUpload)

        GradientButton(
            text = "Співати ще раз",
            icon = Icons.Rounded.Mic,
            onClick = { onSingAgain(details.song.id) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) { Text("До каталогу") }
    }
}

@Composable
private fun RecordingPlayer(playback: PlaybackUi, onToggle: () -> Unit, onShare: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ваш запис", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = "Поділитися записом")
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                playback.error != null -> Text(playback.error, color = Danger)
                !playback.available -> Text("Готуємо запис…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = onToggle) {
                        Icon(
                            if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Пауза" else "Відтворити запис"
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val progress = if (playback.durationMs > 0) {
                            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Violet,
                            trackColor = OutlineColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatDuration(playback.positionMs)} / ${formatDuration(playback.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadCard(upload: UploadUi, onUpload: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Запис на сервері", style = MaterialTheme.typography.titleMedium)
            Text(
                "Файл буде збережено у вашому обліковому записі й доступний лише вам. Він зникне з сервера, коли ви видалите виконання.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onUpload, enabled = !upload.busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        upload.busy -> "Надсилаємо…"
                        upload.done -> "Надіслати ще раз"
                        else -> "Надіслати на сервер"
                    }
                )
            }
            upload.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (upload.done) MaterialTheme.colorScheme.onSurface else Danger
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_048_576) String.format(Locale.ROOT, "%.1f МБ", bytes / 1_048_576.0) else "${bytes / 1024} КБ"

private fun shareRecording(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "Файл запису не знайдено", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Поділитися записом"))
    } catch (e: Exception) {
        Toast.makeText(context, "Немає застосунку, через який можна поділитися", Toast.LENGTH_LONG).show()
    }
}
