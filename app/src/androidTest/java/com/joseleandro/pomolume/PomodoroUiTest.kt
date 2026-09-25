package com.joseleandro.pomolume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.pressBack
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.map
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.core.navigation.PomoLumeApp
import com.joseleandro.pomolume.feature.history.domain.*
import com.joseleandro.pomolume.feature.history.presentation.*
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.pomodoro.presentation.*
import com.joseleandro.pomolume.feature.settings.domain.*
import com.joseleandro.pomolume.feature.settings.presentation.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PomodoroUiTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { compose.runOnIdle { viewModels.clear() } }

    @Test fun startPauseAndResumeDispatchTheExpectedActions() {
        var timer by mutableStateOf(PomodoroState(isLoading = false))
        val actions = mutableListOf<PomodoroAction>()
        compose.setContent {
            PomoTheme {
                PomodoroScreen(PomodoroUiState(timer = timer), onAction = { action ->
                    actions += action
                    timer = timer.copy(
                        startedAt = 1,
                        timerState = if (action == PomodoroAction.PAUSE) TimerState.PAUSED else TimerState.RUNNING
                    )
                }, onRetry = {})
            }
        }
        compose.onNodeWithTag("primary_action").performScrollTo().assertTextContains("Iniciar").performClick()
        compose.onNodeWithTag("primary_action").assertTextContains("Pausar").performClick()
        compose.onNodeWithTag("primary_action").assertTextContains("Continuar").performClick()
        compose.onNodeWithText("Pausar").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(PomodoroAction.START, PomodoroAction.PAUSE, PomodoroAction.RESUME), actions) }
    }

    @Test fun skipRequiresConfirmationAndCancelPreservesTheSession() {
        val actions = mutableListOf<PomodoroAction>()
        compose.setContent { PomoTheme { PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), actions::add, {}) } }
        compose.onNodeWithTag("skip_action").performScrollTo().performClick()
        compose.onNodeWithText("Pular esta sessão?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("skip_action").performScrollTo().performClick()
        compose.onNode(hasText("Pular") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(listOf(PomodoroAction.SKIP), actions) }
    }

    @Test fun resetRequiresConfirmationAfterProgress() {
        var action: PomodoroAction? = null
        compose.setContent { PomoTheme {
            PomodoroScreen(PomodoroUiState(timer = PomodoroState(sessionId = "active", timerState = TimerState.PAUSED,
                startedAt = 1, remainingTimeMillis = 900_000, isLoading = false)), { action = it }, {})
        } }
        compose.onNodeWithTag("reset_action").performScrollTo().performClick()
        compose.onNodeWithText("Reiniciar esta sessão?").assertIsDisplayed()
        compose.runOnIdle { assertNull(action) }
        compose.onNode(hasText("Reiniciar") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(PomodoroAction.RESET, action) }
    }

    @Test fun confirmationClosesWhenTheSessionChanges() {
        var timer by mutableStateOf(PomodoroState(sessionId = "focus", timerState = TimerState.RUNNING, startedAt = 1, isLoading = false))
        val actions = mutableListOf<PomodoroAction>()
        compose.setContent { PomoTheme { PomodoroScreen(PomodoroUiState(timer = timer), actions::add, {}) } }
        compose.onNodeWithTag("skip_action").performScrollTo().performClick()
        compose.onNodeWithText("Pular esta sessão?").assertIsDisplayed()
        compose.runOnIdle { timer = PomodoroState(sessionId = "break", sessionType = SessionType.SHORT_BREAK, isLoading = false) }
        compose.onNodeWithText("Pular esta sessão?").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun notificationSkipOpensTheSameConfirmation() {
        var consumed = false
        compose.setContent { PomoTheme {
            PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), {}, {},
                notificationAction = "SKIP", onNotificationActionConsumed = { consumed = true })
        } }
        compose.onNodeWithText("Pular esta sessão?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(consumed) }
    }

    @Test fun notificationSkipWaitsForRecoveryBeforeDispatchingAndConsuming() {
        var busy by mutableStateOf(true)
        var pendingAction by mutableStateOf<String?>(PomodoroAction.SKIP.name)
        val actions = mutableListOf<PomodoroAction>()
        var consumed = 0
        compose.setContent { PomoTheme {
            PomodoroScreen(
                state = PomodoroUiState(
                    timer = PomodoroState(sessionId = "recovering-focus", timerState = TimerState.RUNNING,
                        startedAt = 1, isLoading = false),
                    settings = PomodoroSettings(confirmSkip = false),
                    isBusy = busy
                ),
                onAction = { actions += it },
                onRetry = {},
                notificationAction = pendingAction,
                notificationSessionId = "recovering-focus",
                onNotificationActionConsumed = { consumed++; pendingAction = null }
            )
        } }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            assertEquals(0, consumed)
            busy = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(PomodoroAction.SKIP), actions)
            assertEquals(1, consumed)
            busy = true
        }
        compose.waitForIdle()
        compose.runOnIdle { busy = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(PomodoroAction.SKIP), actions)
            assertEquals(1, consumed)
        }
    }

    @Test fun durationEditorSavesAndEnforcesMinimum() {
        var settings by mutableStateOf(PomodoroSettings(focusDurationMinutes = 1))
        compose.setContent { PomoTheme {
            SettingsScreen(SettingsUiState(settings = settings, isLoading = false),
                onUpdate = { settings = it(settings) }, onEnableNotifications = {}, onRetry = {})
        } }
        compose.onNodeWithText("Duração do foco").performClick()
        compose.onNodeWithContentDescription("Diminuir duração").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Aumentar duração").performClick()
        compose.onNodeWithTag("duration_value").assertTextEquals("2 min")
        compose.onNodeWithText("Salvar").performClick()
        compose.runOnIdle { assertEquals(2, settings.focusDurationMinutes) }
        compose.onNodeWithText("2 minutos").assertIsDisplayed()
    }

    @Test fun settingsCannotOverwriteDefaultsWhenLoadingFailed() {
        var changed = false
        compose.setContent { PomoTheme {
            SettingsScreen(SettingsUiState(isLoading = false, error = R.string.settings_error_load),
                onUpdate = { changed = true }, onEnableNotifications = {}, onRetry = {})
        } }
        compose.onNodeWithText("Duração do foco").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(changed) }
    }

    @Test fun emptyHistoryExplainsWhatToDoAndFiltersWork() {
        var filter by mutableStateOf(HistoryFilter.TODAY)
        compose.setContent { PomoTheme {
            HistoryScreen(HistoryUiState(filter = filter, isLoading = false), { filter = it }, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("Nenhuma sessão ainda"))
        compose.onNodeWithText("Nenhuma sessão ainda").assertIsDisplayed()
        listOf(HistoryFilter.WEEK, HistoryFilter.MONTH, HistoryFilter.YEAR, HistoryFilter.ALL, HistoryFilter.TODAY).forEach { selected ->
            compose.onNodeWithTag("history_list").performScrollToIndex(0)
            compose.onNodeWithTag("filter_${selected.name}").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(selected, filter) }
        }
    }

    @Test fun completedHistoryShowsTypeDurationStatusAndSummary() {
        val now = System.currentTimeMillis()
        compose.setContent { PomoTheme {
            HistoryScreen(HistoryUiState(isLoading = false, stats = HistoryStats(focusSeconds = 1500, completedPomodoros = 1, activeDays = 1),
                sessions = listOf(PomodoroSession("one", SessionType.FOCUS, SessionStatus.COMPLETED, now - 1_500_000, now, 1500, 1500))), {}, {})
        } }
        compose.onNodeWithText("1 Pomodoro concluído").assertIsDisplayed()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("25 min · Concluído"))
        compose.onNodeWithText("Foco").assertIsDisplayed()
        compose.onNodeWithText("25 min · Concluído").assertIsDisplayed()
    }

    @Test fun navigationOpensSettingsChangesDurationAndOpensHistory() {
        val settings = MemorySettings()
        val timer = MemoryTimer()
        val history = MemoryHistory()
        val timerVm = PomodoroViewModel(GetPomodoroStateUseCase(timer), ControlPomodoroUseCase(timer),
            GetSettingsUseCase(settings), GetPomodoroCompletionsUseCase(timer))
        val historyVm = HistoryViewModel(GetHistoryUseCase(history), GetHistoryStatsUseCase(history), SavedStateHandle())
        val settingsVm = SettingsViewModel(GetSettingsUseCase(settings), UpdateSettingsUseCase(settings))
        viewModels.put("timer", timerVm)
        viewModels.put("history", historyVm)
        viewModels.put("settings", settingsVm)
        compose.setContent { PomoLumeApp(pomodoroViewModel = timerVm, historyViewModel = historyVm, settingsViewModel = settingsVm) }
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Duração do foco").performClick()
        compose.onNodeWithContentDescription("Aumentar duração").performClick()
        compose.onNodeWithText("Salvar").performClick()
        compose.onNodeWithText("26 minutos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithTag("tab_history").performClick()
        compose.waitUntil(5_000) { !historyVm.uiState.value.isLoading }
        compose.waitForIdle()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("Nenhuma sessão ainda"))
        compose.onNodeWithText("Nenhuma sessão ainda").assertIsDisplayed()
        compose.onNodeWithTag("history_list").performScrollToIndex(0)
        compose.onNodeWithTag("filter_WEEK").performScrollTo().performClick().assertIsSelected()
    }

    @Test fun cancelPausedFocusRequiresConfirmationAndDismissDoesNotDispatch() {
        val actions = mutableListOf<PomodoroAction>()
        compose.setContent { PomoTheme {
            PomodoroScreen(PomodoroUiState(timer = PomodoroState(sessionId = "paused-focus",
                timerState = TimerState.PAUSED, startedAt = 1, remainingTimeMillis = 900_000,
                completedFocusCount = 2, isLoading = false)), actions::add, {})
        } }
        compose.onNodeWithTag("cancel_action").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Cancelar Pomodoro?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithText("Continuar sessão").performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("cancel_action").performScrollTo().performClick()
        compose.onNode(hasText("Cancelar Pomodoro") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(listOf(PomodoroAction.CANCEL), actions) }
    }

    @Test fun cancelCanEndAnActiveCycleWhileNextBreakIsIdle() {
        val actions = mutableListOf<PomodoroAction>()
        compose.setContent { PomoTheme {
            PomodoroScreen(PomodoroUiState(timer = PomodoroState(sessionType = SessionType.SHORT_BREAK,
                timerState = TimerState.IDLE, completedFocusCount = 2, isLoading = false)), actions::add, {})
        } }
        compose.onNodeWithTag("cancel_action").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Cancelar Pomodoro?").assertIsDisplayed()
        compose.onNode(hasText("Cancelar Pomodoro") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(listOf(PomodoroAction.CANCEL), actions) }
    }

    @Test fun cancelIsDisabledBeforeAnySessionOrCycleStarts() {
        compose.setContent { PomoTheme {
            PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), {}, {})
        } }
        compose.onNodeWithTag("cancel_action").performScrollTo().assertIsNotEnabled()
    }

    @Test fun monthAndYearSelectionSurviveTabsAndSettingsSystemBack() {
        val settings = MemorySettings()
        val timer = MemoryTimer()
        val history = MemoryHistory()
        val timerVm = PomodoroViewModel(GetPomodoroStateUseCase(timer), ControlPomodoroUseCase(timer),
            GetSettingsUseCase(settings), GetPomodoroCompletionsUseCase(timer))
        val historyVm = HistoryViewModel(GetHistoryUseCase(history), GetHistoryStatsUseCase(history), SavedStateHandle())
        val settingsVm = SettingsViewModel(GetSettingsUseCase(settings), UpdateSettingsUseCase(settings))
        viewModels.put("timer", timerVm)
        viewModels.put("history", historyVm)
        viewModels.put("settings", settingsVm)
        compose.setContent { PomoLumeApp(pomodoroViewModel = timerVm, historyViewModel = historyVm, settingsViewModel = settingsVm) }
        compose.onNodeWithTag("tab_history").performClick()
        compose.waitUntil(5_000) { !historyVm.uiState.value.isLoading }
        listOf(HistoryFilter.MONTH, HistoryFilter.YEAR).forEach { selected ->
            compose.onNodeWithTag("history_list").performScrollToIndex(0)
            compose.onNodeWithTag("filter_${selected.name}").performScrollTo().performClick()
            compose.waitUntil(5_000) { !historyVm.uiState.value.isLoading && historyVm.uiState.value.filter == selected }
            compose.onNodeWithTag("tab_pomodoro").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Configurações").performClick()
            compose.onNodeWithContentDescription("Voltar").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("tab_pomodoro").assertIsSelected()
            compose.onNodeWithTag("tab_history").performClick().assertIsSelected()
            compose.onNodeWithTag("history_list").performScrollToIndex(0)
            compose.onNodeWithTag("filter_${selected.name}").performScrollTo().assertIsSelected()
            compose.runOnIdle { assertEquals(selected, historyVm.uiState.value.filter) }
        }
        pressBack()
        compose.onNodeWithTag("tab_pomodoro").assertIsSelected()
    }

    @Test fun disabledNotificationsStillAllowStartingAnIdleSessionWithoutAnId() {
        val timer = MemoryTimer()
        val timerVm = setAppForCommandTest(timer)
        compose.waitUntil(5_000) { !timerVm.uiState.value.timer.isLoading && !timerVm.uiState.value.isBusy }
        compose.onNodeWithTag("primary_action").performScrollTo().assertTextContains("Iniciar").performClick()
        compose.runOnIdle {
            assertEquals(listOf(PomodoroAction.START), timer.actions)
            assertTrue(timer.scopedActions.isEmpty())
        }
    }

    @Test fun cancellingAnIdleBreakWithoutAnIdReachesTheController() {
        val timer = MemoryTimer(PomodoroState(sessionType = SessionType.SHORT_BREAK,
            timerState = TimerState.IDLE, completedFocusCount = 2, isLoading = false))
        val timerVm = setAppForCommandTest(timer)
        compose.waitUntil(5_000) { !timerVm.uiState.value.timer.isLoading && !timerVm.uiState.value.isBusy }
        compose.onNodeWithTag("cancel_action").performScrollTo().assertIsEnabled().performClick()
        compose.onNode(hasText("Cancelar Pomodoro") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle {
            assertEquals(listOf(PomodoroAction.CANCEL), timer.actions)
            assertTrue(timer.scopedActions.isEmpty())
        }
    }

    private fun setAppForCommandTest(timer: MemoryTimer): PomodoroViewModel {
        val settings = MemorySettings()
        val history = MemoryHistory()
        val timerVm = PomodoroViewModel(GetPomodoroStateUseCase(timer), ControlPomodoroUseCase(timer),
            GetSettingsUseCase(settings), GetPomodoroCompletionsUseCase(timer))
        val historyVm = HistoryViewModel(GetHistoryUseCase(history), GetHistoryStatsUseCase(history), SavedStateHandle())
        val settingsVm = SettingsViewModel(GetSettingsUseCase(settings), UpdateSettingsUseCase(settings))
        viewModels.put("timer", timerVm)
        viewModels.put("history", historyVm)
        viewModels.put("settings", settingsVm)
        compose.setContent { PomoLumeApp(pomodoroViewModel = timerVm, historyViewModel = historyVm, settingsViewModel = settingsVm) }
        return timerVm
    }

    private class MemorySettings : SettingsRepository {
        val state = MutableStateFlow(PomodoroSettings(notificationsEnabled = false))
        override fun observeSettings() = state
        override suspend fun updateSettings(settings: PomodoroSettings) { state.value = settings }
    }
    private class MemoryTimer(initial: PomodoroState = PomodoroState(isLoading = false)) : PomodoroRepository, PomodoroServiceController {
        override val state = MutableStateFlow(initial)
        override val completions = MutableSharedFlow<CompletionEvent>()
        val actions = mutableListOf<PomodoroAction>()
        val scopedActions = mutableListOf<Pair<PomodoroAction, String>>()
        override suspend fun restore() = Unit
        override suspend fun refresh() = Unit
        override suspend fun execute(action: PomodoroAction) = dispatch(action)
        override suspend fun dispatch(action: PomodoroAction) { actions += action }
        override suspend fun dispatchForSession(action: PomodoroAction, sessionId: String) {
            scopedActions += action to sessionId
            if (sessionId.isNotBlank() && sessionId == state.value.sessionId) dispatch(action)
        }
        override suspend fun recover() = Unit
    }
    private class MemoryHistory : HistoryRepository, HistoryStatsRepository {
        val sessions = MutableStateFlow<List<PomodoroSession>>(emptyList())
        override fun observeSessions(startDate: Long?, endDate: Long?) = sessions.map { rows ->
            rows.filter { (startDate == null || it.endedAt >= startDate) && (endDate == null || it.endedAt < endDate) }
        }
        override fun observeDailyFocus(startDate: Long?, endDate: Long?) = observeSessions(startDate, endDate).map { rows ->
            rows.filter { it.type == SessionType.FOCUS && it.status == SessionStatus.COMPLETED }
                .groupBy { Instant.ofEpochMilli(it.endedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                .map { (date, completed) -> DailyFocusStat(date, completed.sumOf { it.actualDurationSeconds }, completed.size) }
        }
        override suspend fun save(session: PomodoroSession) { sessions.value += session }
    }
}
