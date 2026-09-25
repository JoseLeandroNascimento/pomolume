package com.joseleandro.pomolume.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.feature.history.domain.FocusSummary
import com.joseleandro.pomolume.feature.history.domain.GetHistoryUseCase
import com.joseleandro.pomolume.feature.history.domain.GetTodaySummaryUseCase
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryFilter(val label: String, val days: Long?) {
    TODAY("Hoje", 1), WEEK("7 dias", 7), MONTH("30 dias", 30), ALL("Todos", null)
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val sessions: List<PomodoroSession> = emptyList(),
    val todaySummary: FocusSummary = FocusSummary(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    getHistory: GetHistoryUseCase,
    getTodaySummary: GetTodaySummaryUseCase
) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.TODAY)
    private val reload = MutableStateFlow(0)
    private val date = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }
    val uiState = combine(filter, date, reload) { selected, today, _ -> selected to today }
        .flatMapLatest { (selected, today) ->
            val zone = ZoneId.systemDefault()
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val from = selected.days?.let { today.minusDays(it - 1).atStartOfDay(zone).toInstant().toEpochMilli() }
            combine(getHistory(from, null), getTodaySummary(todayStart, tomorrowStart)) { sessions, summary ->
                HistoryUiState(selected, sessions.sortedByDescending { it.endedAt }, summary, today, false)
            }.onStart { emit(HistoryUiState(filter = selected, today = today)) }
                .catch { emit(HistoryUiState(filter = selected, today = today, isLoading = false, error = "Não foi possível carregar o histórico.")) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectFilter(value: HistoryFilter) { filter.value = value }
    fun retry() { reload.update { it + 1 } }
}
