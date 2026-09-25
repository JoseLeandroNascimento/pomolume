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
import kotlinx.coroutines.launch

data class PomodoroUiState(
    val timer: PomodoroState = PomodoroState(),
    val settings: PomodoroSettings = PomodoroSettings(),
    val error: String? = null,
    val isBusy: Boolean = false
)

class PomodoroViewModel(
    getState: GetPomodoroStateUseCase,
    private val control: ControlPomodoroUseCase,
    getSettings: GetSettingsUseCase,
    getCompletions: com.joseleandro.pomolume.feature.pomodoro.domain.GetPomodoroCompletionsUseCase
) : ViewModel() {
    val completions = getCompletions()
    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    val uiState = combine(
        getState(),
        getSettings().catch { emit(PomodoroSettings()); error.value = "Não foi possível carregar as preferências." },
        error,
        busy
    ) { timer, settings, message, isBusy ->
        PomodoroUiState(timer, settings, message ?: timer.error, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PomodoroUiState())

    val keepScreenOn = kotlinx.coroutines.flow.map(uiState) {
        it.settings.keepScreenOn && it.timer.timerState == com.joseleandro.pomolume.feature.pomodoro.domain.TimerState.RUNNING
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { recover() }

    fun recover() = execute { control.recover() }
    fun onAction(action: PomodoroAction) = execute { control(action) }
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
                error.value = "Não foi possível atualizar o timer. Tente novamente."
            } finally {
                busy.value = false
            }
        }
    }
}
