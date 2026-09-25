package com.joseleandro.pomolume.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_sessions", indices = [Index(value = ["endedAt"])])
data class PomodoroSessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long
)
