package com.example.karaokestudio.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.ui.common.UiState
import com.example.karaokestudio.ui.common.uiStateOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) {
    ALL("Усі"),
    SCORED("З оцінкою"),
    RECORDED("З записом")
}

data class HistoryData(
    val items: List<PerformanceDetails>,
    val filter: HistoryFilter,
    val total: Int
)

class HistoryViewModel(private val performanceRepository: PerformanceRepository) : ViewModel() {

    private val retryTick = MutableStateFlow(0)
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<UiState<HistoryData>> = uiStateOf(retryTick) {
        combine(performanceRepository.observeHistory(), filter) { all, selected ->
            val items = when (selected) {
                HistoryFilter.ALL -> all
                HistoryFilter.SCORED -> all.filter { it.performance.score != null }
                HistoryFilter.RECORDED -> all.filter { it.recording != null }
            }
            HistoryData(items, selected, all.size)
        }
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun retry() {
        retryTick.value++
    }

    fun selectFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun delete(performanceId: Long) {
        viewModelScope.launch {
            try {
                performanceRepository.delete(performanceId)
                _messages.send("Виконання видалено")
            } catch (e: Exception) {
                _messages.send("Не вдалося видалити: ${e.message ?: "невідома помилка"}")
            }
        }
    }
}
