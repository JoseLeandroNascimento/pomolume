package com.joseleandro.pomolume.data.local.mapper

import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity
import com.joseleandro.pomolume.domain.model.PomodoroSession

fun PomodoroSessionEntity.toDomain(): PomodoroSession {
    return PomodoroSession(
        id = id,
        durationInMinutes = durationInMinutes,
        timestamp = timestamp,
        isCompleted = isCompleted
    )
}

fun PomodoroSession.toEntity(): PomodoroSessionEntity {
    return PomodoroSessionEntity(
        id = id,
        durationInMinutes = durationInMinutes,
        timestamp = timestamp,
        isCompleted = isCompleted
    )
}
