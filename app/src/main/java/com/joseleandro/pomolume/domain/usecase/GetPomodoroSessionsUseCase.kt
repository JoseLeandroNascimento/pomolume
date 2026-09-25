package com.joseleandro.pomolume.domain.usecase

import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.repository.PomodoroRepository
import kotlinx.coroutines.flow.Flow

class GetPomodoroSessionsUseCase(
    private val repository: PomodoroRepository
) {
    operator fun invoke(): Flow<List<PomodoroSession>> = repository.getSessions()
}
