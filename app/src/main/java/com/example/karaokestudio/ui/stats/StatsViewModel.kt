package com.example.karaokestudio.ui.stats

import androidx.lifecycle.ViewModel
import com.example.karaokestudio.data.local.model.UserStats
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StatsViewModel(performanceRepository: PerformanceRepository) : ViewModel() {

    private val retryTick = MutableStateFlow(0)

    val state: StateFlow<UiState<UserStats>> = uiStateOf(retryTick) { performanceRepository.observeStats() }

    fun retry() {
        retryTick.value++
    }
}
