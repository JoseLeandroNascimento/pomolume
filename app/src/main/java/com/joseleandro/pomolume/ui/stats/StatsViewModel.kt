package com.joseleandro.pomolume.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    getSessionsUseCase: GetPomodoroSessionsUseCase
) : ViewModel() {

    val sessions: StateFlow<List<PomodoroSession>> = getSessionsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
