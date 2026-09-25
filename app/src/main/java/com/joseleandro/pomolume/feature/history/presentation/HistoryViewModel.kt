package com.joseleandro.pomolume.feature.history.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.feature.history.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

enum class HistoryFilter(@param:StringRes val titleRes: Int, val period: HistoryPeriod) {
    TODAY(R.string.history_period_today, HistoryPeriod.TODAY),
    WEEK(R.string.history_period_week, HistoryPeriod.WEEK),
    MONTH(R.string.history_period_month, HistoryPeriod.MONTH),
    YEAR(R.string.history_period_year, HistoryPeriod.YEAR),
    ALL(R.string.history_period_all, HistoryPeriod.ALL)
}

data class HistorySessionRow(val session: PomodoroSession, val time: String)
data class HistorySessionGroup(val date: LocalDate, val label: String, val rows: List<HistorySessionRow>)

/** Called by the ViewModel on Default, never from a lazy-list item or recomposition. */
fun prepareHistoryGroups(sessions: List<PomodoroSession>, zone: ZoneId = ZoneId.systemDefault()): List<HistorySessionGroup> {
    if (sessions.isEmpty()) return emptyList()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    return sessions.sortedByDescending { it.endedAt }
        .groupBy { Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() }
        .map { (date, items) ->
            HistorySessionGroup(date, date.format(dateFormat),
                items.map { HistorySessionRow(it, Instant.ofEpochMilli(it.startedAt).atZone(zone).format(timeFormat)) })
        }
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val sessions: List<PomodoroSession> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val stats: HistoryStats = HistoryStats(),
    val groups: List<HistorySessionGroup> = prepareHistoryGroups(sessions),
    val periodLabel: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    getHistory: GetHistoryUseCase,
    getStats: GetHistoryStatsUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val filter = savedStateHandle.getStateFlow(PERIOD_KEY, HistoryFilter.TODAY.name)
        .map { name -> HistoryFilter.entries.find { it.name == name } ?: HistoryFilter.TODAY }
    private val reload = MutableStateFlow(0)
    private val calendar = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone) to zone)
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState = combine(filter, calendar, reload) { selected, calendar, _ -> selected to calendar }
        .flatMapLatest { (selected, calendar) ->
            val (today, zone) = calendar
            val range = historyDateRange(selected.period, today)
            combine(
                getHistory(range.startMillis(zone), range.endMillis(zone)),
                getStats(selected.period, today, zone)
            ) { sessions, stats ->
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                val periodLabel = range.firstDate?.let { start ->
                    if (selected == HistoryFilter.TODAY) start.format(formatter)
                    else start.format(formatter) + " – " + requireNotNull(range.endDateExclusive).minusDays(1).format(formatter)
                }.orEmpty()
                HistoryUiState(filter = selected, sessions = sessions,
                    today = today, isLoading = false, stats = stats,
                    groups = prepareHistoryGroups(sessions, zone), periodLabel = periodLabel)
            }.flowOn(Dispatchers.Default)
                .onStart { emit(HistoryUiState(filter = selected, today = today)) }
                .catch { emit(HistoryUiState(filter = selected, today = today, isLoading = false, error = "history_load_failed")) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectFilter(value: HistoryFilter) { savedStateHandle[PERIOD_KEY] = value.name }
    fun retry() { reload.update { it + 1 } }

    companion object { private const val PERIOD_KEY = "history_selected_period" }
}
