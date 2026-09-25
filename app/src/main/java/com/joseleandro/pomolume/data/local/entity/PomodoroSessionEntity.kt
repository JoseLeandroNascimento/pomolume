package com.joseleandro.pomolume.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val durationInMinutes: Int,
    val timestamp: Long,
    val isCompleted: Boolean
)
