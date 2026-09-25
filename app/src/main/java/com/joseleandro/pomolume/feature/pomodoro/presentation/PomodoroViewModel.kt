package com.joseleandro.pomolume.feature.pomodoro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.feature.pomodoro.domain.ControlPomodoroUseCase
import com.joseleandro.pomolume.feature.pomodoro.domain.GetPomodoroStateUseCase
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroAction
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.settings.domain.GetSettingsUseCase
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

data class PomodoroUiState(
    val timer: PomodoroState = PomodoroState(),
    val settings: PomodoroSettings = PomodoroSettings(),
    val error: String? = null,
    val isBusy: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroViewModel(
    getState: GetPomodoroStateUseCase,
    private val control: ControlPomodoroUseCase,
    getSettings: GetSettingsUseCase,
    getCompletions: com.joseleandro.pomolume.feature.pomodoro.domain.GetPomodoroCompletionsUseCase
) : ViewModel() {
    val completions = getCompletions()
    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val settingsReload = MutableStateFlow(0)
    private val settings = settingsReload.flatMapLatest {
        getSettings().catch { error.value = "settings"; emit(PomodoroSettings()) }
    }
    val uiState = combine(
        getState(),
        settings,
        error,
        busy
    ) { timer, settings, message, isBusy ->
        PomodoroUiState(timer, settings, message ?: timer.error, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PomodoroUiState())

    // Only the readout consumes ticking state; controls and layout change on transitions.
    val layoutState = uiState.map { state ->
        state.copy(timer = state.timer.copy(remainingTimeMillis = state.timer.totalDurationMillis))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PomodoroUiState())

    val keepScreenOn = uiState.map {
        it.settings.keepScreenOn && it.timer.timerState == com.joseleandro.pomolume.feature.pomodoro.domain.TimerState.RUNNING
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { recover() }

    fun recover() {
        settingsReload.update { it + 1 }
        execute { control.recover() }
    }
    fun onAction(action: PomodoroAction) = execute { control(action) }
    fun onActionForSession(action: PomodoroAction, sessionId: String) = execute {
        control.forSession(action, sessionId)
    }
    fun dismissError() { error.value = null }

    private fun execute(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                error.value = null
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = "action"
            } finally {
                busy.value = false
            }
        }
    }
}
