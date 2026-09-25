package com.joseleandro.pomolume.feature.pomodoro.data

import com.joseleandro.pomolume.feature.history.domain.HistoryRepository
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import com.joseleandro.pomolume.feature.history.domain.SessionStatus
import com.joseleandro.pomolume.feature.pomodoro.domain.CompletionEvent
import com.joseleandro.pomolume.feature.pomodoro.domain.PendingTransition
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroAction
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroClock
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroRepository
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerStateStore
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import com.joseleandro.pomolume.feature.settings.domain.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Timestamp-based state machine. The service only requests refreshes; it never owns time.
 *
 * Terminal transitions are a small durable outbox: persist intent, insert history by stable
 * session ID, then persist the next state. Replaying after process death is therefore safe.
 */
class PomodoroRepositoryImpl(
    private val store: TimerStateStore,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val clock: PomodoroClock,
    scope: CoroutineScope
) : PomodoroRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(PomodoroState())
    private val mutableCompletions = MutableSharedFlow<CompletionEvent>(extraBufferCapacity = 16)
    override val state = mutableState.asStateFlow()
    override val completions = mutableCompletions.asSharedFlow()
    private var settings = PomodoroSettings()
    private var restored = false
    private var pendingSettingsChange = false

    init {
        scope.launch {
            settingsRepository.observeSettings()
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) throw cause
                    guarded { throw cause }
                    delay(1_000)
                    true
                }
                .collect { updated ->
                    guarded {
                        val validated = updated.validated()
                        pendingSettingsChange = pendingSettingsChange || (restored && settings != validated)
                        settings = validated
                        if (restored) applySettings()
                    }
                }
        }
    }

    override suspend fun restore() = guarded {
        ensureRestored()
        refreshLocked()
    }

    override suspend fun refresh() = guarded {
        ensureRestored()
        refreshLocked()
    }

    override suspend fun execute(action: PomodoroAction) = guarded {
        ensureRestored()
        // An action tapped against an expired session must not reset/skip its successor.
        if (refreshLocked()) return@guarded
        val current = mutableState.value
        when (action) {
            PomodoroAction.START -> if (current.timerState == TimerState.IDLE) {
                val now = clock.now()
                commit(current.copy(
                    sessionId = UUID.randomUUID().toString(),
                    timerState = TimerState.RUNNING,
                    startedAt = now,
                    expectedEndAt = now + current.totalDurationMillis,
                    remainingTimeMillis = current.totalDurationMillis,
                    pausedAt = null,
                    retainDurationOnIdle = false
                ))
            }
            PomodoroAction.PAUSE -> if (current.timerState == TimerState.RUNNING) {
                val now = clock.now()
                val remaining = remaining(current, now)
                if (remaining == 0L) {
                    beginTerminal(PendingTransition.COMPLETE, current.expectedEndAt ?: now)
                } else {
                    commit(current.copy(
                        timerState = TimerState.PAUSED,
                        remainingTimeMillis = remaining,
                        expectedEndAt = null,
                        pausedAt = now
                    ))
                }
            }
            PomodoroAction.RESUME -> if (current.timerState == TimerState.PAUSED) {
                commit(current.copy(
                    timerState = TimerState.RUNNING,
                    expectedEndAt = clock.now() + current.remainingTimeMillis,
                    pausedAt = null
                ))
            }
            PomodoroAction.RESET -> if (current.startedAt != null) {
                beginTerminal(PendingTransition.RESET, clock.now())
            } else {
                commit(fresh(current.sessionType, current.completedFocusCount).copy(
                    totalDurationMillis = current.totalDurationMillis,
                    remainingTimeMillis = current.totalDurationMillis,
                    retainDurationOnIdle = true
                ))
            }
            PomodoroAction.SKIP -> beginTerminal(PendingTransition.SKIP, clock.now())
        }
    }

    private suspend fun ensureRestored() {
        if (restored) return
        settings = settingsRepository.observeSettings().first().validated()
        val persisted = store.read()
        val initial = if (persisted == null) fresh(SessionType.FOCUS, 0) else sanitize(persisted)
        commit(initial)
        restored = true
        applySettings()
    }

    private fun sanitize(saved: PomodoroState): PomodoroState {
        val now = clock.now()
        val duration = saved.totalDurationMillis.coerceIn(60_000L, 120 * 60_000L)
        val active = saved.timerState != TimerState.IDLE || saved.pendingTransition != null
        val terminal = saved.pendingTransition ?: if (saved.timerState == TimerState.COMPLETED) {
            PendingTransition.COMPLETE
        } else null
        val remaining = saved.remainingTimeMillis.coerceIn(0L, duration)
        return saved.copy(
            sessionId = if (active && saved.sessionId.isBlank()) UUID.randomUUID().toString() else saved.sessionId,
            timerState = if (terminal != null) TimerState.COMPLETED else saved.timerState,
            totalDurationMillis = duration,
            remainingTimeMillis = remaining,
            startedAt = if (active) saved.startedAt?.coerceIn(0L, now) ?: now else null,
            expectedEndAt = if (saved.timerState == TimerState.RUNNING) {
                saved.expectedEndAt?.takeIf { it >= 0 }?.coerceAtMost(now + duration) ?: now + remaining
            } else null,
            pausedAt = if (saved.timerState == TimerState.PAUSED) saved.pausedAt ?: now else null,
            completedFocusCount = saved.completedFocusCount.coerceIn(0, 10),
            cyclesUntilLongBreak = saved.cyclesUntilLongBreak.coerceIn(1, 10),
            pendingTransition = terminal,
            transitionAt = if (terminal != null) saved.transitionAt ?: saved.expectedEndAt ?: now else null,
            isLoading = false,
            error = null
        )
    }

    private suspend fun applySettings() {
        val current = mutableState.value
        if (current.pendingTransition != null) return
        val replaceDuration = current.timerState == TimerState.IDLE &&
            (!current.retainDurationOnIdle || pendingSettingsChange)
        val updated = current.copy(
            totalDurationMillis = if (replaceDuration) duration(current.sessionType) else current.totalDurationMillis,
            remainingTimeMillis = if (replaceDuration) duration(current.sessionType) else current.remainingTimeMillis,
            cyclesUntilLongBreak = settings.cyclesBeforeLongBreak,
            retainDurationOnIdle = current.retainDurationOnIdle && !pendingSettingsChange
        )
        if (updated != current) commit(updated)
        pendingSettingsChange = false
    }

    /** Returns true if this refresh advanced a terminal transition. */
    private suspend fun refreshLocked(): Boolean {
        applySettings()
        val current = mutableState.value
        if (current.pendingTransition != null) {
            finishTerminal()
            return true
        }
        if (current.timerState != TimerState.RUNNING) return false
        val remaining = remaining(current, clock.now())
        if (remaining == 0L) {
            beginTerminal(PendingTransition.COMPLETE, current.expectedEndAt ?: clock.now())
            return true
        }
        // This is presentation state only. No database or DataStore work on ticks.
        mutableState.value = current.copy(remainingTimeMillis = remaining)
        return false
    }

    private suspend fun beginTerminal(transition: PendingTransition, endedAt: Long) {
        val current = mutableState.value
        commit(current.copy(
            sessionId = current.sessionId.ifBlank { UUID.randomUUID().toString() },
            startedAt = current.startedAt ?: endedAt,
            timerState = TimerState.COMPLETED,
            remainingTimeMillis = if (transition == PendingTransition.COMPLETE) 0 else remaining(current, endedAt),
            pendingTransition = transition,
            transitionAt = endedAt,
            expectedEndAt = null,
            pausedAt = null
        ))
        finishTerminal()
    }

    private suspend fun finishTerminal() {
        val terminal = mutableState.value
        val transition = terminal.pendingTransition ?: return
        val status = when (transition) {
            PendingTransition.COMPLETE -> SessionStatus.COMPLETED
            PendingTransition.RESET -> SessionStatus.CANCELLED
            PendingTransition.SKIP -> SessionStatus.SKIPPED
        }
        historyRepository.save(PomodoroSession(
            id = terminal.sessionId,
            type = terminal.sessionType,
            status = status,
            startedAt = requireNotNull(terminal.startedAt),
            endedAt = requireNotNull(terminal.transitionAt),
            plannedDurationSeconds = terminal.totalDurationMillis / 1_000,
            actualDurationSeconds = (terminal.totalDurationMillis - terminal.remainingTimeMillis)
                .coerceIn(0, terminal.totalDurationMillis) / 1_000
        ))
        if (transition == PendingTransition.RESET) {
            commit(fresh(terminal.sessionType, terminal.completedFocusCount).copy(
                totalDurationMillis = terminal.totalDurationMillis,
                remainingTimeMillis = terminal.totalDurationMillis,
                retainDurationOnIdle = true
            ))
            return
        }
        val completedFocusCount = when {
            terminal.sessionType == SessionType.LONG_BREAK -> 0
            terminal.sessionType == SessionType.FOCUS && transition == PendingTransition.COMPLETE ->
                terminal.completedFocusCount + 1
            else -> terminal.completedFocusCount
        }
        val nextType = when (terminal.sessionType) {
            SessionType.FOCUS -> if (completedFocusCount >= settings.cyclesBeforeLongBreak) {
                SessionType.LONG_BREAK
            } else SessionType.SHORT_BREAK
            SessionType.SHORT_BREAK, SessionType.LONG_BREAK -> SessionType.FOCUS
        }
        var next = fresh(nextType, completedFocusCount)
        val autoStart = if (nextType == SessionType.FOCUS) settings.autoStartFocus else settings.autoStartBreak
        if (autoStart) {
            val now = clock.now()
            next = next.copy(
                sessionId = UUID.randomUUID().toString(),
                timerState = TimerState.RUNNING,
                startedAt = now,
                expectedEndAt = now + next.totalDurationMillis
            )
        }
        commit(next)
        if (transition == PendingTransition.COMPLETE) {
            mutableCompletions.tryEmit(CompletionEvent(terminal.sessionId, terminal.sessionType, nextType))
        }
    }

    private fun remaining(current: PomodoroState, now: Long): Long =
        if (current.timerState == TimerState.RUNNING) {
            ((current.expectedEndAt ?: now) - now).coerceIn(0, current.totalDurationMillis)
        } else current.remainingTimeMillis.coerceIn(0, current.totalDurationMillis)

    private fun duration(type: SessionType): Long = when (type) {
        SessionType.FOCUS -> settings.focusDurationMinutes
        SessionType.SHORT_BREAK -> settings.shortBreakDurationMinutes
        SessionType.LONG_BREAK -> settings.longBreakDurationMinutes
    } * 60_000L

    private fun fresh(type: SessionType, count: Int) = PomodoroState(
        sessionType = type,
        totalDurationMillis = duration(type),
        remainingTimeMillis = duration(type),
        completedFocusCount = count,
        cyclesUntilLongBreak = settings.cyclesBeforeLongBreak,
        isLoading = false
    )

    private suspend fun commit(candidate: PomodoroState) {
        val clean = candidate.copy(isLoading = false, error = null)
        store.write(clean)
        mutableState.value = clean
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        mutex.withLock {
            try {
                block()
                if (mutableState.value.error != null) {
                    mutableState.value = mutableState.value.copy(error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = "Não foi possível salvar a sessão. Tente novamente."
                )
            }
        }
    }
}
