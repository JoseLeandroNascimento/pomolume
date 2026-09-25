package com.joseleandro.pomolume.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import com.joseleandro.pomolume.domain.usecase.SavePomodoroSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel(
    private val getSessionsUseCase: GetPomodoroSessionsUseCase,
    private val saveSessionUseCase: SavePomodoroSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            getSessionsUseCase().collect { sessions ->
                _uiState.update { currentState ->
                    currentState.copy(completedSessionsCount = sessions.size)
                }
            }
        }
    }

    fun toggleTimer() {
        _uiState.update { currentState ->
            currentState.copy(isRunning = !currentState.isRunning)
        }
    }

    fun finishSession() {
        viewModelScope.launch {
            saveSessionUseCase(
                PomodoroSession(
                    durationInMinutes = 25,
                    isCompleted = true
                )
            )
            _uiState.update { currentState ->
                currentState.copy(
                    isRunning = false,
                    timeRemainingInSeconds = 25 * 60
                )
            }
        }
    }
}
