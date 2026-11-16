package com.example.karaokestudio.ui.home

import androidx.lifecycle.ViewModel
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.data.local.model.StatsSummary
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

data class HomeData(
    val nickname: String,
    val featured: List<Song>,
    val userSongs: List<Song>,
    val recent: List<PerformanceDetails>,
    val summary: StatsSummary
)

class HomeViewModel(
    songRepository: SongRepository,
    performanceRepository: PerformanceRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<HomeData>> = uiStateOf(retryTick) {
        combine(
            songRepository.observeAll(),
            performanceRepository.observeRecent(3),
            performanceRepository.observeSummary(),
            settingsRepository.settings
        ) { songs, recent, summary, settings ->
            HomeData(
                nickname = settings.nickname,
                featured = songs.filter { it.isDemo }.take(6),
                userSongs = songs.filter { !it.isDemo }.take(6),
                recent = recent,
                summary = summary
            )
        }
    }

    fun retry() {
        retryTick.value++
    }
}
