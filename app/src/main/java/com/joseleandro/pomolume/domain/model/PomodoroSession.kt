package com.joseleandro.pomolume.domain.model

data class PomodoroSession(
    val id: Long = 0,
    val durationInMinutes: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true
)
