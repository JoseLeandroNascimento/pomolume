package com.joseleandro.pomolume.feature.history.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import com.joseleandro.pomolume.feature.history.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Test fun selectedPeriodIsRestoredAndChangesArePersistedInSavedState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var viewModelJob: Job? = null
        try {
            val savedState = SavedStateHandle(mapOf("history_selected_period" to HistoryFilter.YEAR.name))
            val repository = EmptyHistory()
            val viewModel = HistoryViewModel(GetHistoryUseCase(repository), GetHistoryStatsUseCase(repository), savedState)
            store.put("history", viewModel)
            viewModelJob = viewModel.viewModelScope.coroutineContext[Job]
            val initial = viewModel.uiState.first { !it.isLoading }
            assertEquals(HistoryFilter.YEAR, initial.filter)
            assertEquals(12, initial.stats.buckets.size)
            viewModel.selectFilter(HistoryFilter.MONTH)
            val updated = viewModel.uiState.first { !it.isLoading && it.filter == HistoryFilter.MONTH }
            assertEquals(HistoryFilter.MONTH.name, savedState.get<String>("history_selected_period"))
            val today = LocalDate.now()
            assertEquals(today.lengthOfMonth(), updated.stats.buckets.size)
            val expectedStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(expectedStart, repository.lastStart)
        } finally {
            store.clear()
            viewModelJob?.join()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun unknownPersistedPeriodFallsBackToToday() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var viewModelJob: Job? = null
        try {
            val repository = EmptyHistory()
            val viewModel = HistoryViewModel(GetHistoryUseCase(repository), GetHistoryStatsUseCase(repository),
                SavedStateHandle(mapOf("history_selected_period" to "REMOVED_FILTER")))
            store.put("history", viewModel)
            viewModelJob = viewModel.viewModelScope.coroutineContext[Job]
            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(HistoryFilter.TODAY, state.filter)
            assertTrue(state.groups.isEmpty())
            assertEquals(0, state.stats.completedPomodoros)
        } finally {
            store.clear()
            viewModelJob?.join()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class EmptyHistory : HistoryRepository, HistoryStatsRepository {
        var lastStart: Long? = null
        override fun observeSessions(startDate: Long?, endDate: Long?): Flow<List<PomodoroSession>> =
            flowOf(emptyList<PomodoroSession>()).also { lastStart = startDate }
        override suspend fun save(session: PomodoroSession) = Unit
        override fun observeDailyFocus(startDate: Long?, endDate: Long?) = flowOf(emptyList<DailyFocusStat>())
    }
}
