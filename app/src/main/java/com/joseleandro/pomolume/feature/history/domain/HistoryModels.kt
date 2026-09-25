package com.joseleandro.pomolume.feature.history.domain

import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import kotlinx.coroutines.flow.Flow

enum class SessionStatus { COMPLETED, CANCELLED, SKIPPED }
data class PomodoroSession(
    val id: String,
    val type: SessionType,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long
)
interface HistoryRepository {
    fun observeSessions(startDate: Long? = null, endDate: Long? = null): Flow<List<PomodoroSession>>
    suspend fun save(session: PomodoroSession)
}
class GetHistoryUseCase(private val repository: HistoryRepository) {
    operator fun invoke(startDate: Long? = null, endDate: Long? = null) = repository.observeSessions(startDate, endDate)
}
