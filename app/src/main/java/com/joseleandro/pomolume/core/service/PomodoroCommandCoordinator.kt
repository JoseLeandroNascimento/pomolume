package com.joseleandro.pomolume.core.service

import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroAction
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroRepository
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroServiceController
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** start() returns only after the service's completion collector is registered. */
interface PomodoroRuntime {
    suspend fun start()
    fun stop()
    fun beginCommand() = Unit
    fun endCommand() = Unit
}

/**
 * Serializes user/notification commands together with Android runtime changes. A delayed
 * service launch from an older START cannot overtake a newer CANCEL.
 */
class PomodoroCommandCoordinator(
    private val repository: PomodoroRepository,
    private val store: TimerStateStore,
    private val runtime: PomodoroRuntime
) : PomodoroServiceController {
    private val mutex = Mutex()

    override suspend fun dispatch(action: PomodoroAction) {
        dispatchChecked(action, expectedSessionId = null)
    }

    override suspend fun dispatchForSession(action: PomodoroAction, sessionId: String) {
        dispatchChecked(action, expectedSessionId = sessionId)
    }

    private suspend fun dispatchChecked(action: PomodoroAction, expectedSessionId: String?) {
        withCommand {
            val before = repository.state.value
            val needsCollector = action != PomodoroAction.CANCEL &&
                (action != PomodoroAction.RESET || before.requiresService ||
                    (before.isLoading && store.read()?.requiresService == true))
            if (needsCollector) runtime.start()
            if (expectedSessionId != null && action == PomodoroAction.CANCEL && repository.state.value.isLoading) {
                repository.executeForSession(action, expectedSessionId)
                reconcileRuntime(alreadyStarted = needsCollector)
                return@withCommand
            }
            if (expectedSessionId != null && repository.state.value.isLoading) repository.restore()
            if (expectedSessionId != null &&
                (expectedSessionId.isBlank() || expectedSessionId != repository.state.value.sessionId)) {
                reconcileRuntime(alreadyStarted = needsCollector)
                return@withCommand
            }
            if (expectedSessionId == null) repository.execute(action)
            else repository.executeForSession(action, expectedSessionId)
            reconcileRuntime(alreadyStarted = needsCollector)
        }
    }

    override suspend fun recover() {
        withCommand {
            val saved = store.read()
            val needsCollector = saved?.requiresService == true || repository.state.value.requiresService
            if (needsCollector) runtime.start()
            repository.restore()
            reconcileRuntime(alreadyStarted = needsCollector)
        }
    }

    private suspend fun withCommand(block: suspend () -> Unit) {
        mutex.withLock {
            runtime.beginCommand()
            try {
                block()
            } finally {
                runtime.endCommand()
            }
        }
    }

    private suspend fun reconcileRuntime(alreadyStarted: Boolean) {
        if (repository.state.value.requiresService) {
            if (!alreadyStarted) runtime.start()
        } else if (!repository.state.value.isLoading) {
            runtime.stop()
        }
    }
}

private val PomodoroState.requiresService: Boolean
    get() = timerState != TimerState.IDLE || pendingTransition != null || error != null
