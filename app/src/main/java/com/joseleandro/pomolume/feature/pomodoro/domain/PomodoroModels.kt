package com.joseleandro.pomolume.feature.pomodoro.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class SessionType { FOCUS, SHORT_BREAK, LONG_BREAK }
@Serializable
enum class TimerState { IDLE, RUNNING, PAUSED, COMPLETED }
@Serializable
enum class PendingTransition { COMPLETE, RESET, SKIP, CANCEL }
enum class PomodoroAction { START, PAUSE, RESUME, RESET, SKIP, CANCEL }

@Serializable
data class PomodoroState(
    val sessionId: String = "",
    val sessionType: SessionType = SessionType.FOCUS,
    val timerState: TimerState = TimerState.IDLE,
    val totalDurationMillis: Long = 25 * 60_000L,
    val remainingTimeMillis: Long = 25 * 60_000L,
    val startedAt: Long? = null,
    val expectedEndAt: Long? = null,
    val pausedAt: Long? = null,
    val completedFocusCount: Int = 0,
    val cyclesUntilLongBreak: Int = 4,
    val pendingTransition: PendingTransition? = null,
    val transitionAt: Long? = null,
    val retainDurationOnIdle: Boolean = false,
    val cancelAfterTransition: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val progress: Float get() = (remainingTimeMillis.toFloat() / totalDurationMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
    val currentCycle: Int get() = if (sessionType == SessionType.FOCUS) (completedFocusCount + 1).coerceAtMost(cyclesUntilLongBreak) else completedFocusCount.coerceAtLeast(1)

    companion object { const val ERROR_STORAGE = "timer_storage" }
}
data class CompletionEvent(val sessionId: String, val sessionType: SessionType, val nextType: SessionType)
interface TimerStateStore {
    suspend fun read(): PomodoroState?
    suspend fun write(state: PomodoroState)
}
fun interface PomodoroClock { fun now(): Long }
interface PomodoroRepository {
    val state: StateFlow<PomodoroState>
    val completions: SharedFlow<CompletionEvent>
    suspend fun restore()
    suspend fun execute(action: PomodoroAction)
    suspend fun executeForSession(action: PomodoroAction, sessionId: String) {
        if (sessionId.isNotBlank() && state.value.sessionId == sessionId) execute(action)
    }
    suspend fun refresh()
}
interface PomodoroServiceController {
    suspend fun dispatch(action: PomodoroAction)
    suspend fun dispatchForSession(action: PomodoroAction, sessionId: String) = dispatch(action)
    suspend fun recover()
}
class GetPomodoroStateUseCase(private val repository: PomodoroRepository) {
    operator fun invoke() = repository.state
}
class ControlPomodoroUseCase(private val controller: PomodoroServiceController) {
    suspend operator fun invoke(action: PomodoroAction) = controller.dispatch(action)
    suspend fun forSession(action: PomodoroAction, sessionId: String) = controller.dispatchForSession(action, sessionId)
    suspend fun recover() = controller.recover()
}
