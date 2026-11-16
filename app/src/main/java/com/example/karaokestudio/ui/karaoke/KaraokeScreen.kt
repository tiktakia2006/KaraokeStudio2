package com.example.karaokestudio.ui.karaoke

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import kotlin.math.roundToInt
import com.example.karaokestudio.domain.lyrics.LrcParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Slider
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.DemoBadge
import com.example.karaokestudio.ui.components.ErrorView
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.KeepScreenOn
import com.example.karaokestudio.ui.components.LoadingView
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.formatDuration
import com.example.karaokestudio.ui.theme.Amber
import com.example.karaokestudio.ui.theme.Background
import com.example.karaokestudio.ui.theme.Mint
import com.example.karaokestudio.ui.theme.OutlineColor
import com.example.karaokestudio.ui.theme.TextHigh
import com.example.karaokestudio.ui.theme.TextMid
import com.example.karaokestudio.ui.theme.Violet
import com.example.karaokestudio.ui.theme.VioletContainer

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun KaraokeScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    viewModel: KaraokeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(hasMicPermission(context)) }
    var useMic by rememberSaveable { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) {
            viewModel.start(true)
        } else {
            useMic = false
            Toast.makeText(
                context,
                "Дозвіл не надано. Можна співати без мікрофона, але без оцінки.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is KaraokeEvent.Finished -> onFinished(event.performanceId)
                is KaraokeEvent.Discarded -> {
                    if (event.reason.isNotBlank()) Toast.makeText(context, event.reason, Toast.LENGTH_LONG).show()
                    onBack()
                }
            }
        }
    }

    val active = state.phase == KaraokePhase.COUNTDOWN ||
        state.phase == KaraokePhase.SINGING ||
        state.phase == KaraokePhase.PAUSED
    BackHandler(enabled = active) { showExitDialog = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseIfSinging() }
    KeepScreenOn(state.keepScreenOn)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = state.song?.title ?: "Караоке",
            subtitle = state.song?.artist,
            onBack = { if (active) showExitDialog = true else onBack() },
            actions = {
                if (state.song?.isDemo == true) {
                    DemoBadge()
                    Spacer(Modifier.width(12.dp))
                }
            }
        )
        when (state.phase) {
            KaraokePhase.LOADING -> LoadingView(text = "Готуємо пісню…")
            KaraokePhase.ERROR -> ErrorView(
                message = state.message ?: "Сталася помилка",
                onRetry = viewModel::reload,
                title = "Караоке недоступне",
                retryText = "Спробувати ще раз"
            )
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LyricsPane(state)
                    if (state.phase == KaraokePhase.COUNTDOWN) CountdownOverlay(state.countdown)
                }
                ProgressBlock(state)
                ControlsPane(
                    state = state,
                    useMic = useMic,
                    micGranted = micGranted,
                    onUseMicChange = { useMic = it },
                    onStart = {
                        when {
                            !useMic -> viewModel.start(false)
                            micGranted -> viewModel.start(true)
                            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSemitones = viewModel::changeSemitones,
                    onSpeed = viewModel::changeSpeed,
                    onVolume = viewModel::setVolume,
                    onVolumeCommit = viewModel::saveVolume,
                    onTogglePause = viewModel::togglePause,
                    onRestart = viewModel::restart,
                    onFinish = viewModel::finish,
                    onCancelCountdown = viewModel::discard
                )
            }
        }
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = "Вийти з виконання?",
            text = "Прогрес не буде збережено. Щоб зберегти результат, натисніть «Завершити».",
            confirmText = "Вийти без збереження",
            dismissText = "Продовжити",
            onConfirm = {
                showExitDialog = false
                viewModel.discard()
            },
            onDismiss = { showExitDialog = false }
        )
    }
}

@Composable
private fun LyricsPane(state: KaraokeUiState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLine) {
        if (state.currentLine >= 0) listState.animateScrollToItem((state.currentLine - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(state.lines) { index, line ->
            val current = index == state.currentLine
            val past = index < state.currentLine
            val textColor by animateColorAsState(
                targetValue = when {
                    current -> Color.White
                    past -> TextMid.copy(alpha = 0.55f)
                    else -> TextMid
                },
                label = "lyricColor"
            )
            val backColor by animateColorAsState(
                targetValue = if (current) VioletContainer.copy(alpha = 0.7f) else Color.Transparent,
                label = "lyricBackground"
            )
            val displayText = line.text.ifBlank { "♪ ♪ ♪" }
            val fontSize = (state.lyricsSizeSp + if (current) 4 else 0).sp
            val fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
            val boxModifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(backColor)
                .padding(horizontal = 14.dp, vertical = 8.dp)
            if (current && line.text.isNotBlank()) {
                BasicText(
                    text = displayText,
                    modifier = boxModifier,
                    style = TextStyle(
                        brush = wipeBrush(LrcParser.progressAt(state.lines, index, state.positionMs), Color.White, TextMid),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center
                    )
                )
            } else {
                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Center,
                    modifier = boxModifier
                )
            }
        }
    }
}

private fun wipeBrush(progress: Float, sung: Color, upcoming: Color): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader = LinearGradientShader(
        from = Offset(0f, 0f),
        to = Offset(size.width, 0f),
        colors = listOf(sung, sung, upcoming, upcoming),
        colorStops = listOf(0f, progress, progress, 1f)
    )
}

@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(
        Modifier.fillMaxSize().background(Background.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(seconds.toString(), fontSize = 120.sp, fontWeight = FontWeight.ExtraBold, color = TextHigh)
            Text("Приготуйтесь співати", style = MaterialTheme.typography.titleMedium, color = TextMid)
        }
    }
}

@Composable
private fun ProgressBlock(state: KaraokeUiState) {
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.padding(horizontal = 24.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Violet,
            trackColor = OutlineColor
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium, color = TextMid)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = TextMid)
        }
        if (state.phase == KaraokePhase.SINGING || state.phase == KaraokePhase.PAUSED) {
            Spacer(Modifier.height(8.dp))
            if (state.micEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { state.level },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Mint,
                        trackColor = OutlineColor
                    )
                }
            } else {
                Text(
                    "Без мікрофона: оцінка не обчислюється",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMid
                )
            }
        }
        state.message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = Amber)
        }
    }
}

@Composable
private fun ControlsPane(
    state: KaraokeUiState,
    useMic: Boolean,
    micGranted: Boolean,
    onUseMicChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onSemitones: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onVolume: (Int) -> Unit,
    onVolumeCommit: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    onCancelCountdown: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.phase) {
            KaraokePhase.READY -> {
                TuningCard(state, onSemitones, onSpeed, onVolume, onVolumeCommit)
                MicRow(useMic, micGranted, onUseMicChange)
                GradientButton(
                    text = "Почати",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            KaraokePhase.COUNTDOWN -> OutlinedButton(onClick = onCancelCountdown, modifier = Modifier.fillMaxWidth()) {
                Text("Скасувати")
            }
            KaraokePhase.SINGING, KaraokePhase.PAUSED -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onRestart) {
                    Icon(Icons.Rounded.Replay, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Спочатку")
                }
                FilledIconButton(onClick = onTogglePause, modifier = Modifier.size(64.dp)) {
                    val paused = state.phase == KaraokePhase.PAUSED
                    Icon(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (paused) "Продовжити" else "Пауза",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Button(onClick = onFinish) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Завершити")
                }
            }
            KaraokePhase.SAVING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Зберігаємо виконання…", color = TextMid)
            }
            else -> Unit
        }
    }
}

@Composable
private fun TuningCard(
    state: KaraokeUiState,
    onSemitones: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onVolume: (Int) -> Unit,
    onVolumeCommit: (Int) -> Unit
) {
    var localVolume by remember { mutableFloatStateOf(state.volume.toFloat()) }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тональність музики", modifier = Modifier.weight(1f))
                IconButton(onClick = { onSemitones(-1) }) { Icon(Icons.Rounded.Remove, contentDescription = "Нижче") }
                Text(
                    if (state.semitones > 0) "+${state.semitones}" else state.semitones.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { onSemitones(1) }) { Icon(Icons.Rounded.Add, contentDescription = "Вище") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Темп", modifier = Modifier.weight(1f))
                listOf(0.8f, 1f, 1.2f).forEach { speed ->
                    FilterChip(
                        selected = state.speed == speed,
                        onClick = { onSpeed(speed) },
                        label = { Text("${speed}×") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Гучність музики", modifier = Modifier.weight(1f))
                Text("${localVolume.roundToInt()} %", style = MaterialTheme.typography.labelMedium, color = TextMid)
            }
            Slider(
                value = localVolume,
                onValueChange = {
                    localVolume = it
                    onVolume(it.roundToInt())
                },
                onValueChangeFinished = { onVolumeCommit(localVolume.roundToInt()) },
                valueRange = 0f..100f
            )
        }
    }
}

@Composable
private fun MicRow(useMic: Boolean, micGranted: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = if (useMic) Violet else TextMid)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Запис і оцінка через мікрофон")
            Text(
                if (micGranted) "Для чесної оцінки використовуйте навушники" else "Дозвіл буде запитано під час старту",
                style = MaterialTheme.typography.labelMedium,
                color = TextMid
            )
        }
        Switch(checked = useMic, onCheckedChange = onChange)
    }
}
