package com.example.karaokestudio.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.model.StatsSummary
import com.example.karaokestudio.data.online.AccountRepository
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileData(val nickname: String, val summary: StatsSummary)

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    performanceRepository: PerformanceRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<ProfileData>> = uiStateOf(retryTick) {
        combine(settingsRepository.settings, performanceRepository.observeSummary()) { settings, summary ->
            ProfileData(settings.nickname, summary)
        }
    }

    fun retry() {
        retryTick.value++
    }

    fun validateNickname(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.length < 2 -> "Ім'я має містити щонайменше 2 символи"
            trimmed.length > 24 -> "Ім'я не може бути довшим за 24 символи"
            else -> null
        }
    }

    fun saveNickname(name: String) {
        if (validateNickname(name) != null) return
        viewModelScope.launch {
            settingsRepository.update { it.copy(nickname = name.trim()) }
            accountRepository.pushNicknameIfSignedIn(name.trim())
        }
    }

    companion object {
        fun levelFor(count: Int): String = when {
            count >= 25 -> "Зірка сцени"
            count >= 10 -> "Співак"
            count >= 3 -> "Любитель"
            else -> "Новачок"
        }
    }
}
