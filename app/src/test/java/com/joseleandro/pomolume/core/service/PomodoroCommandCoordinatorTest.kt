package com.joseleandro.pomolume.core.service

import com.joseleandro.pomolume.feature.pomodoro.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroCommandCoordinatorTest {
    @Test fun idleResetAndCancelNeverStartForegroundRuntime() = runTest {
        for (action in listOf(PomodoroAction.RESET, PomodoroAction.CANCEL)) {
            val f = Fixture()
            f.coordinator.dispatch(action)
            assertEquals(listOf("execute:${action.name}", "stop"), f.operations)
            assertEquals(TimerState.IDLE, f.repository.state.value.timerState)
        }
    }

    @Test fun runningCancelStopsOnlyAfterRepositoryCommittedIdle() = runTest {
        val f = Fixture(running())
        f.repository.executeGate = CompletableDeferred()
        val cancellation = async { f.coordinator.dispatch(PomodoroAction.CANCEL) }
        runCurrent()
        assertEquals(listOf("execute:CANCEL"), f.operations)
        assertEquals(TimerState.RUNNING, f.repository.state.value.timerState)
        f.repository.executeGate!!.complete(Unit)
        cancellation.await()
        assertEquals(listOf("execute:CANCEL", "stop"), f.operations)
        assertEquals(TimerState.IDLE, f.repository.state.value.timerState)
    }

    @Test fun failedCancelKeepsServiceAliveToRetryPendingDurableTransition() = runTest {
        val f = Fixture(running())
        f.repository.afterExecute = {
            f.repository.state.value = running().copy(
                timerState = TimerState.COMPLETED,
                pendingTransition = PendingTransition.CANCEL,
                error = PomodoroState.ERROR_STORAGE
            )
        }
        f.coordinator.dispatch(PomodoroAction.CANCEL)
        assertEquals(listOf("execute:CANCEL", "start"), f.operations)
        assertEquals(PendingTransition.CANCEL, f.repository.state.value.pendingTransition)
    }

    @Test fun runningResetHasCollectorReadyBeforeItCanReachDeadline() = runTest {
        val f = Fixture(running())
        f.coordinator.dispatch(PomodoroAction.RESET)
        assertEquals(listOf("start", "execute:RESET", "stop"), f.operations)
    }

    @Test fun startWaitsForForegroundCollectorReadiness() = runTest {
        val f = Fixture()
        f.runtime.startGate = CompletableDeferred()
        val start = async { f.coordinator.dispatch(PomodoroAction.START) }
        runCurrent()
        assertEquals(listOf("start"), f.operations)
        assertEquals(1, f.runtime.commandLeases)
        assertEquals(TimerState.IDLE, f.repository.state.value.timerState)
        f.runtime.startGate!!.complete(Unit)
        start.await()
        assertEquals(listOf("start", "execute:START"), f.operations)
        assertEquals(0, f.runtime.commandLeases)
        assertEquals(TimerState.RUNNING, f.repository.state.value.timerState)
    }

    @Test fun delayedStartCannotOvertakeLaterCancel() = runTest {
        val f = Fixture()
        f.runtime.startGate = CompletableDeferred()
        val start = async { f.coordinator.dispatch(PomodoroAction.START) }
        runCurrent()
        val cancel = async { f.coordinator.dispatch(PomodoroAction.CANCEL) }
        runCurrent()
        assertEquals(listOf("start"), f.operations)
        assertFalse(cancel.isCompleted)
        f.runtime.startGate!!.complete(Unit)
        start.await()
        cancel.await()
        assertEquals(listOf("start", "execute:START", "execute:CANCEL", "stop"), f.operations)
        assertEquals(TimerState.IDLE, f.repository.state.value.timerState)
    }

    @Test fun staleNotificationCannotSkipSuccessor() = runTest {
        val f = Fixture(running().copy(sessionId = "new-session"))
        f.coordinator.dispatchForSession(PomodoroAction.SKIP, "old-session")
        assertEquals(listOf("start"), f.operations)
        assertEquals("new-session", f.repository.state.value.sessionId)
    }

    @Test fun notificationWithMatchingIdentityCanPause() = runTest {
        val f = Fixture(running())
        f.coordinator.dispatchForSession(PomodoroAction.PAUSE, "session")
        assertEquals(listOf("start", "execute:PAUSE"), f.operations)
        assertEquals(TimerState.PAUSED, f.repository.state.value.timerState)
    }

    @Test fun staleCancelNotificationDoesNotCancelAnotherSequence() = runTest {
        val f = Fixture(running().copy(sessionId = "new-session"))
        f.coordinator.dispatchForSession(PomodoroAction.CANCEL, "old-session")
        assertFalse(f.operations.any { it.startsWith("execute:") })
        assertEquals(TimerState.RUNNING, f.repository.state.value.timerState)
    }

    @Test fun recoveredCompletionIsObservedBeforeRuntimeStops() = runTest {
        val f = Fixture(PomodoroState(), saved = running())
        val observed = mutableListOf<CompletionEvent>()
        f.runtime.onReady = {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                f.repository.completions.collect { observed += it }
            }
        }
        f.repository.onRestore = {
            f.repository.completions.emit(CompletionEvent("session", SessionType.FOCUS, SessionType.SHORT_BREAK))
            f.repository.state.value = PomodoroState(sessionType = SessionType.SHORT_BREAK, isLoading = false)
        }
        f.coordinator.recover()
        assertEquals(listOf("start", "restore", "stop"), f.operations)
        assertEquals(1, observed.size)
        assertEquals("session", observed.single().sessionId)
    }

    @Test fun coldNotificationIsCheckedAfterRestoreAndCannotActOnNextSession() = runTest {
        val f = Fixture(PomodoroState(), saved = running())
        f.repository.onRestore = {
            f.repository.state.value = PomodoroState(sessionType = SessionType.SHORT_BREAK, isLoading = false)
        }
        f.coordinator.dispatchForSession(PomodoroAction.SKIP, "session")
        assertEquals(listOf("start", "restore", "stop"), f.operations)
        assertEquals(SessionType.SHORT_BREAK, f.repository.state.value.sessionType)
    }

    @Test fun idleRecoveryDoesNotStartForegroundService() = runTest {
        val f = Fixture()
        f.coordinator.recover()
        assertEquals(listOf("restore", "stop"), f.operations)
    }

    @Test fun foregroundStartupFailureDoesNotStartTimer() = runTest {
        val f = Fixture()
        f.runtime.failStart = true
        var failure: Exception? = null
        try {
            f.coordinator.dispatch(PomodoroAction.START)
        } catch (exception: IllegalStateException) {
            failure = exception
        }
        assertNotNull(failure)
        assertEquals(0, f.runtime.commandLeases)
        assertEquals(listOf("start"), f.operations)
        assertEquals(TimerState.IDLE, f.repository.state.value.timerState)
    }

    private class Fixture(
        initial: PomodoroState = PomodoroState(isLoading = false),
        saved: PomodoroState? = initial
    ) {
        val operations = mutableListOf<String>()
        val repository = FakeRepository(initial, operations)
        val runtime = FakeRuntime(operations)
        private val store = object : TimerStateStore {
            override suspend fun read() = saved
            override suspend fun write(state: PomodoroState) = Unit
        }
        val coordinator = PomodoroCommandCoordinator(repository, store, runtime)
    }

    private class FakeRuntime(private val operations: MutableList<String>) : PomodoroRuntime {
        var startGate: CompletableDeferred<Unit>? = null
        var failStart = false
        var commandLeases = 0
        override fun beginCommand() { commandLeases++ }
        override fun endCommand() { commandLeases-- }
        var onReady: () -> Unit = {}
        override suspend fun start() {
            operations += "start"
            check(commandLeases == 1)
            check(!failStart)
            startGate?.await()
            onReady()
        }
        override fun stop() { operations += "stop" }
    }

    private class FakeRepository(initial: PomodoroState, private val operations: MutableList<String>) : PomodoroRepository {
        override val state = MutableStateFlow(initial)
        override val completions = MutableSharedFlow<CompletionEvent>()
        var executeGate: CompletableDeferred<Unit>? = null
        var afterExecute: (() -> Unit)? = null
        var onRestore: suspend () -> Unit = { state.value = state.value.copy(isLoading = false) }
        override suspend fun restore() { operations += "restore"; onRestore() }
        override suspend fun refresh() = Unit
        override suspend fun execute(action: PomodoroAction) {
            operations += "execute:${action.name}"
            executeGate?.await()
            state.value = when (action) {
                PomodoroAction.START, PomodoroAction.RESUME -> running()
                PomodoroAction.PAUSE -> running().copy(timerState = TimerState.PAUSED)
                PomodoroAction.RESET, PomodoroAction.CANCEL -> PomodoroState(isLoading = false)
                PomodoroAction.SKIP -> PomodoroState(sessionType = SessionType.SHORT_BREAK, isLoading = false)
            }
            afterExecute?.invoke()
        }
    }

    private companion object {
        fun running() = PomodoroState(
            sessionId = "session", timerState = TimerState.RUNNING,
            startedAt = 1_000L, expectedEndAt = 1_501_000L, isLoading = false
        )
    }
}
