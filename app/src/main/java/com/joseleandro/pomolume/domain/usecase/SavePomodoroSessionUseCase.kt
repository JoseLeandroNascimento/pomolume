package com.joseleandro.pomolume.domain.usecase

import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.repository.PomodoroRepository

class SavePomodoroSessionUseCase(
    private val repository: PomodoroRepository
) {
    suspend operator fun invoke(session: PomodoroSession) {
        repository.saveSession(session)
    }
}
