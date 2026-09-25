package com.joseleandro.pomolume.feature.history.domain

import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
data class FocusSummary(val focusSeconds: Long = 0, val completedPomodoros: Int = 0)
interface HistoryRepository {
    fun observeSessions(startDate: Long? = null, endDate: Long? = null): Flow<List<PomodoroSession>>
    suspend fun save(session: PomodoroSession)
}
class GetHistoryUseCase(private val repository: HistoryRepository) {
    operator fun invoke(startDate: Long? = null, endDate: Long? = null) = repository.observeSessions(startDate, endDate)
}
class GetTodaySummaryUseCase(private val repository: HistoryRepository) {
    operator fun invoke(startOfDay: Long, endOfDay: Long) = repository.observeSessions(startOfDay, endOfDay).map { sessions ->
        val focus = sessions.filter { it.type == SessionType.FOCUS && it.status == SessionStatus.COMPLETED }
        FocusSummary(focus.sumOf { it.actualDurationSeconds }, focus.size)
    }
}
