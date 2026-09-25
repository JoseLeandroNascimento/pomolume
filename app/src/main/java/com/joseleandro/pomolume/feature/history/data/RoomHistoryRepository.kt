package com.joseleandro.pomolume.feature.history.data

import com.joseleandro.pomolume.core.database.PomodoroSessionDao
import com.joseleandro.pomolume.core.database.PomodoroSessionEntity
import com.joseleandro.pomolume.feature.history.domain.HistoryRepository
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import com.joseleandro.pomolume.feature.history.domain.SessionStatus
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(
    private val dao: PomodoroSessionDao
) : HistoryRepository {
    override fun observeSessions(startDate: Long?, endDate: Long?): Flow<List<PomodoroSession>> =
        dao.observePeriod(startDate, endDate).map { entities ->
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
        }

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
