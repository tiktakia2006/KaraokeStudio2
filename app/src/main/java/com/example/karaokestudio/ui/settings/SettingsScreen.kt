package com.example.karaokestudio.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.theme.Danger
import kotlin.math.roundToInt

private enum class DangerAction { HISTORY, FAVORITES }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.beginCalibration() else viewModel.calibrationPermissionDenied()
    }
    val onCalibrateClick = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.beginCalibration() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<DangerAction?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { ScreenTopBar("Налаштування", onBack = onBack) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsGroup("Співання") {
                ChoiceSetting(
                    title = "Відлік перед стартом",
                    options = listOf(0, 3, 5),
                    selected = settings.countdownSeconds,
                    label = { if (it == 0) "Без відліку" else "$it с" },
                    onSelect = { value -> viewModel.update { it.copy(countdownSeconds = value) } }
                )
                SliderSetting(
                    title = "Розмір тексту пісні",
                    value = settings.lyricsSizeSp,
                    range = 18..36,
                    format = { "$it sp" },
                    onCommit = { value -> viewModel.update { it.copy(lyricsSizeSp = value) } }
                )
                SwitchSetting(
                    title = "Не вимикати екран під час співу",
                    checked = settings.keepScreenOn,
                    onChange = { value -> viewModel.update { it.copy(keepScreenOn = value) } }
                )
            }
            SettingsGroup("Мікрофон і запис") {
                SliderSetting(
                    title = "Чутливість мікрофона",
                    value = settings.micSensitivity,
                    range = 0..100,
                    format = { "$it %" },
                    onCommit = { value -> viewModel.update { it.copy(micSensitivity = value) } }
                )
                OutlinedButton(onClick = onCalibrateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Налаштувати чутливість автоматично")
                }
                SwitchSetting(
                    title = "Зберігати записи голосу",
                    checked = settings.saveRecordings,
                    onChange = { value -> viewModel.update { it.copy(saveRecordings = value) } }
                )
            }
            SettingsGroup("Мережа") {
                SwitchSetting(
                    title = "Онлайн-функції: пошук тексту, метадані, рейтинг",
                    checked = session?.onlineFeatures ?: true,
                    onChange = viewModel::setOnlineFeatures
                )
                OutlinedButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("Обліковий запис і синхронізація")
                }
            }
            SettingsGroup("Дані") {
                OutlinedButton(onClick = viewModel::reloadDemo, modifier = Modifier.fillMaxWidth()) {
                    Text("Додати демонстраційну історію")
                }
                OutlinedButton(onClick = { pending = DangerAction.HISTORY }, modifier = Modifier.fillMaxWidth()) {
                    Text("Очистити історію виконань")
                }
                OutlinedButton(onClick = { pending = DangerAction.FAVORITES }, modifier = Modifier.fillMaxWidth()) {
                    Text("Очистити обране")
                }
            }
        }
    }

    CalibrationDialog(
        state = calibration,
        onStartNoise = viewModel::measureNoise,
        onStartVoice = viewModel::measureVoice,
        onApply = viewModel::applyCalibration,
        onDismiss = viewModel::cancelCalibration
    )

    pending?.let { action ->
        ConfirmDialog(
            title = if (action == DangerAction.HISTORY) "Очистити історію?" else "Очистити обране?",
            text = if (action == DangerAction.HISTORY) {
                "Усі виконання та збережені записи голосу буде видалено без можливості відновлення."
            } else {
                "Усі пісні буде прибрано з обраного. Самі пісні в каталозі залишаться."
            },
            confirmText = "Очистити",
            onConfirm = {
                if (action == DangerAction.HISTORY) viewModel.clearHistory() else viewModel.clearFavorites()
                pending = null
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(title)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    onCommit: (Int) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(format(local.roundToInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = { onCommit(local.roundToInt()) }
        )
    }
}

@Composable
private fun CalibrationDialog(
    state: CalibrationUi,
    onStartNoise: () -> Unit,
    onStartVoice: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is CalibrationUi.Hidden) return
    val measuring = state is CalibrationUi.Measuring
    AlertDialog(
        onDismissRequest = { if (!measuring) onDismiss() },
        title = { Text("Налаштування мікрофона") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is CalibrationUi.Intro -> Text(
                        "Спершу виміряємо шум навколо. Тримайте телефон так, як зазвичай співаєте, і помовчіть три секунди."
                    )
                    is CalibrationUi.Measuring -> {
                        CircularProgressIndicator()
                        Text(if (state.step == 1) "Слухаємо шум… Помовчіть." else "Слухаємо голос… Співайте або говоріть.")
                    }
                    is CalibrationUi.AskVoice -> Text(
                        "Тепер заспівайте будь-що або голосно прочитайте кілька слів протягом трьох секунд."
                    )
                    is CalibrationUi.Done -> {
                        Text("Рекомендована чутливість: ${state.recommendation.sensitivity} %")
                        Text(
                            "Рівень шуму: ${state.recommendation.noiseLevel}, рівень голосу: ${state.recommendation.voiceLevel}. Оцінка зараховуватиме голос, коли рівень перевищує ${state.recommendation.threshold}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is CalibrationUi.Failed -> Text(state.message, color = Danger)
                    is CalibrationUi.Hidden -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is CalibrationUi.Intro -> TextButton(onClick = onStartNoise) { Text("Почати") }
                is CalibrationUi.AskVoice -> TextButton(onClick = onStartVoice) { Text("Готово, співаю") }
                is CalibrationUi.Done -> TextButton(onClick = onApply) { Text("Застосувати") }
                is CalibrationUi.Failed -> TextButton(onClick = onDismiss) { Text("Закрити") }
                is CalibrationUi.Measuring, is CalibrationUi.Hidden -> Unit
            }
        },
        dismissButton = {
            if (!measuring && state !is CalibrationUi.Failed) TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}
