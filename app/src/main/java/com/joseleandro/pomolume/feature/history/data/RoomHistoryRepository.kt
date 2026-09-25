package com.joseleandro.pomolume.feature.history.data

import com.joseleandro.pomolume.core.database.PomodoroSessionDao
import com.joseleandro.pomolume.core.database.PomodoroSessionEntity
import com.joseleandro.pomolume.feature.history.domain.HistoryRepository
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import com.joseleandro.pomolume.feature.history.domain.SessionStatus
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import com.joseleandro.pomolume.feature.history.domain.DailyFocusStat
import com.joseleandro.pomolume.feature.history.domain.HistoryStatsRepository

class RoomHistoryRepository(
    private val dao: PomodoroSessionDao
) : HistoryRepository, HistoryStatsRepository {
    override fun observeDailyFocus(startDate: Long?, endDate: Long?): Flow<List<DailyFocusStat>> =
        (if (startDate == null && endDate == null) dao.observeAllDailyFocus()
        else dao.observeDailyFocus(startDate ?: Long.MIN_VALUE, endDate ?: Long.MAX_VALUE)).map { rows ->
            rows.map { DailyFocusStat(LocalDate.parse(it.localDate), it.focusSeconds, it.completedPomodoros) }
        }.flowOn(Dispatchers.Default)

    override fun observeSessions(startDate: Long?, endDate: Long?): Flow<List<PomodoroSession>> =
        (if (startDate == null && endDate == null) dao.observeAll()
        else dao.observePeriod(startDate ?: Long.MIN_VALUE, endDate ?: Long.MAX_VALUE)).map { entities ->
            entities.mapNotNull { entity ->
                val type = SessionType.entries.find { it.name == entity.type } ?: return@mapNotNull null
                val status = SessionStatus.entries.find { it.name == entity.status } ?: return@mapNotNull null
                PomodoroSession(
                    id = entity.id,
                    type = type,
                    status = status,
                    startedAt = entity.startedAt,
                    endedAt = entity.endedAt,
                    plannedDurationSeconds = entity.plannedDurationSeconds.coerceAtLeast(0),
                    actualDurationSeconds = entity.actualDurationSeconds.coerceIn(
                        0, entity.plannedDurationSeconds.coerceAtLeast(0)
                    )
                )
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun save(session: PomodoroSession) {
        require(session.id.isNotBlank()) { "Uma sessão precisa de um identificador estável." }
        dao.insert(
            PomodoroSessionEntity(
                id = session.id,
                type = session.type.name,
                status = session.status.name,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                plannedDurationSeconds = session.plannedDurationSeconds,
                actualDurationSeconds = session.actualDurationSeconds
            )
        )
    }
}
