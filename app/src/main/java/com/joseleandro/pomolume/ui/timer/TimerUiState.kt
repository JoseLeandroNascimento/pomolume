package com.joseleandro.pomolume.ui.timer

data class TimerUiState(
    val timeRemainingInSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val completedSessionsCount: Int = 0
) {
    val formattedTime: String
        get() {
            val minutes = timeRemainingInSeconds / 60
            val seconds = timeRemainingInSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
