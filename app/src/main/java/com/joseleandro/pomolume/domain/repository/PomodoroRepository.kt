package com.joseleandro.pomolume.domain.repository

import com.joseleandro.pomolume.domain.model.PomodoroSession
import kotlinx.coroutines.flow.Flow

interface PomodoroRepository {
    fun getSessions(): Flow<List<PomodoroSession>>
    suspend fun saveSession(session: PomodoroSession)
}
