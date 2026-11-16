package com.example.karaokestudio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.audio.MicCalibrator
import com.example.karaokestudio.data.remote.SessionState
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.seed.DatabaseSeeder
import com.example.karaokestudio.data.settings.AppSettings
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.domain.scoring.MicCalibration
import com.example.karaokestudio.domain.scoring.MicRecommendation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CalibrationUi {
    data object Hidden : CalibrationUi
    data object Intro : CalibrationUi
    data class Measuring(val step: Int) : CalibrationUi
    data object AskVoice : CalibrationUi
    data class Done(val recommendation: MicRecommendation) : CalibrationUi
    data class Failed(val message: String) : CalibrationUi
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val performanceRepository: PerformanceRepository,
    private val favoriteRepository: FavoriteRepository,
    private val seeder: DatabaseSeeder,
    private val sessionStore: SessionStore,
    private val calibrator: MicCalibrator
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val session: StateFlow<SessionState?> = sessionStore.state
        .map<SessionState, SessionState?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _calibration = MutableStateFlow<CalibrationUi>(CalibrationUi.Hidden)
    val calibration: StateFlow<CalibrationUi> = _calibration.asStateFlow()

    private var noiseSamples: List<Int> = emptyList()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun update(transform: (AppSettings) -> AppSettings) =
        launchAction(null, "Не вдалося зберегти налаштування") { settingsRepository.update(transform) }

    fun setOnlineFeatures(enabled: Boolean) =
        launchAction(null, "Не вдалося змінити режим") { sessionStore.setOnlineFeatures(enabled) }

    fun beginCalibration() {
        noiseSamples = emptyList()
        _calibration.value = CalibrationUi.Intro
    }

    fun calibrationPermissionDenied() {
        _calibration.value = CalibrationUi.Failed("Без дозволу на мікрофон налаштувати чутливість неможливо.")
    }

    fun cancelCalibration() {
        _calibration.value = CalibrationUi.Hidden
    }

    fun measureNoise() {
        _calibration.value = CalibrationUi.Measuring(1)
        viewModelScope.launch {
            try {
                noiseSamples = calibrator.sample()
                _calibration.value = CalibrationUi.AskVoice
            } catch (e: Exception) {
                _calibration.value = CalibrationUi.Failed("Не вдалося виміряти шум. Перевірте, чи мікрофон не зайнятий іншим застосунком.")
            }
        }
    }

    fun measureVoice() {
        _calibration.value = CalibrationUi.Measuring(2)
        viewModelScope.launch {
            try {
                val voice = calibrator.sample()
                val result = MicCalibration.recommend(noiseSamples, voice)
                _calibration.value = if (result == null) {
                    CalibrationUi.Failed("Голос ледь відрізняється від шуму. Наблизьте телефон до себе, заспівайте голосніше або знайдіть тихіше місце.")
                } else {
                    CalibrationUi.Done(result)
                }
            } catch (e: Exception) {
                _calibration.value = CalibrationUi.Failed("Не вдалося виміряти голос. Спробуйте ще раз.")
            }
        }
    }

    fun applyCalibration() {
        val done = _calibration.value as? CalibrationUi.Done ?: return
        update { it.copy(micSensitivity = done.recommendation.sensitivity) }
        _calibration.value = CalibrationUi.Hidden
    }

    fun clearHistory() =
        launchAction("Історію виконань очищено", "Не вдалося очистити історію") { performanceRepository.clearHistory() }

    fun clearFavorites() =
        launchAction("Обране очищено", "Не вдалося очистити обране") { favoriteRepository.clear() }

    fun reloadDemo() =
        launchAction("Демонстраційну історію додано", "Не вдалося додати демо-історію") { seeder.reloadDemoPerformances() }

    private fun launchAction(okMessage: String?, failMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (okMessage != null) _messages.send(okMessage)
            } catch (e: Exception) {
                _messages.send("$failMessage: ${e.message ?: "невідома помилка"}")
            }
        }
    }
}
