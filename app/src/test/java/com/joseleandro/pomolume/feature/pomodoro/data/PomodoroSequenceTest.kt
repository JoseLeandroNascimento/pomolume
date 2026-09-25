package com.joseleandro.pomolume.feature.pomodoro.data

import com.joseleandro.pomolume.feature.history.domain.*
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroSequenceTest {
    @Test fun completeFourFocusSequenceForEveryAutomationCombination() = runTest {
        for (autoBreak in listOf(false, true)) for (autoFocus in listOf(false, true)) {
            val f = fixture(PomodoroSettings(autoStartBreak = autoBreak, autoStartFocus = autoFocus))
            f.engine.restore()
            repeat(4) { cycle ->
                assertEquals(SessionType.FOCUS, f.state.sessionType)
                assertEquals(cycle, f.state.completedFocusCount)
                assertEquals(cycle + 1, f.state.currentCycle)
                assertEquals(25 * MINUTE, f.state.remainingTimeMillis)
                val focusId = f.complete()
                val expectedBreak = if (cycle == 3) SessionType.LONG_BREAK else SessionType.SHORT_BREAK
                val breakMinutes = if (cycle == 3) 15L else 5L
                assertEquals(expectedBreak, f.state.sessionType)
                assertEquals(cycle + 1, f.state.completedFocusCount)
                assertEquals(breakMinutes * MINUTE, f.state.totalDurationMillis)
                assertEquals(if (autoBreak) TimerState.RUNNING else TimerState.IDLE, f.state.timerState)
                assertEquals(CompletionEvent(focusId, SessionType.FOCUS, expectedBreak), f.events.last())
                assertHistory(f.history.rows.last(), focusId, SessionType.FOCUS, 25 * MINUTE)

                val breakId = f.complete()
                assertEquals(SessionType.FOCUS, f.state.sessionType)
                assertEquals(if (cycle == 3) 0 else cycle + 1, f.state.completedFocusCount)
                assertEquals(25 * MINUTE, f.state.remainingTimeMillis)
                assertEquals(if (autoFocus) TimerState.RUNNING else TimerState.IDLE, f.state.timerState)
                assertEquals(CompletionEvent(breakId, expectedBreak, SessionType.FOCUS), f.events.last())
                assertHistory(f.history.rows.last(), breakId, expectedBreak, breakMinutes * MINUTE)
            }
            assertEquals(8, f.history.rows.size)
            assertEquals(8, f.events.size)
            assertEquals(8, f.history.rows.map { it.id }.toSet().size)
            assertEquals(4, f.history.rows.count { it.type == SessionType.FOCUS && it.status == SessionStatus.COMPLETED })
            assertEquals(1, f.state.currentCycle)
        }
    }

    @Test fun minimumAndMaximumDurationsAndCyclesCompleteCorrectly() = runTest {
        for (focusMinutes in listOf(1, 120)) for (cycles in listOf(1, 10)) {
            val f = fixture(PomodoroSettings(
                focusDurationMinutes = focusMinutes,
                shortBreakDurationMinutes = if (focusMinutes == 1) 1 else 60,
                longBreakDurationMinutes = focusMinutes,
                cyclesBeforeLongBreak = cycles,
                autoStartBreak = true, autoStartFocus = true
            ))
            f.engine.restore()
            repeat(cycles) { cycle ->
                assertEquals(focusMinutes * MINUTE, f.state.remainingTimeMillis)
                f.complete()
                assertEquals(cycle + 1, f.state.completedFocusCount)
                assertEquals(if (cycle + 1 == cycles) SessionType.LONG_BREAK else SessionType.SHORT_BREAK, f.state.sessionType)
                val expectedMinutes = if (cycle + 1 == cycles) focusMinutes else if (focusMinutes == 1) 1 else 60
                assertEquals(expectedMinutes * MINUTE, f.state.remainingTimeMillis)
                f.complete()
            }
            assertEquals(0, f.state.completedFocusCount)
            assertEquals(SessionType.FOCUS, f.state.sessionType)
            assertEquals(cycles * 2, f.history.rows.size)
            assertEquals(cycles * 2, f.events.size)
            assertTrue(f.history.rows.all { it.status == SessionStatus.COMPLETED })
        }
    }

    @Test fun cancelRunningFocusUsesLatestDurationAndNeverAutoStarts() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        runCurrent()
        f.settings.value = f.settings.value.copy(focusDurationMinutes = 40)
        runCurrent()
        f.clock.advance(2 * MINUTE)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f, 40 * MINUTE)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
        assertEquals(120L, f.history.rows.single().actualDurationSeconds)
        assertEquals(1_500L, f.history.rows.single().plannedDurationSeconds)
        assertTrue(f.events.isEmpty())
        repeat(3) { f.engine.refresh() }
        assertCancelledSequence(f, 40 * MINUTE)
    }

    @Test fun cancelPausedFocusExcludesHoursSpentPaused() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(2 * MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        f.clock.advance(8 * 60 * MINUTE)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(120L, f.history.rows.single().actualDurationSeconds)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
    }

    @Test fun cancelBeforeStartingCreatesNoHistory() = runTest {
        val f = fixture(PomodoroSettings(focusDurationMinutes = 60, autoStartFocus = true, autoStartBreak = true))
        repeat(3) { f.engine.execute(PomodoroAction.CANCEL) }
        assertCancelledSequence(f, 60 * MINUTE)
        assertTrue(f.history.rows.isEmpty())
        assertTrue(f.events.isEmpty())
    }

    @Test fun cancelIdleBreakClearsSequenceWithoutFakeAttempt() = runTest {
        val f = fixture()
        f.complete()
        val completed = f.history.rows.single()
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(listOf(completed), f.history.rows)
        assertEquals(1, f.events.size)
    }

    @Test fun cancelStartedShortBreakKeepsPreviouslyCompletedFocus() = runTest {
        val f = fixture()
        f.complete()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(MINUTE)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(listOf(SessionStatus.COMPLETED, SessionStatus.CANCELLED), f.history.rows.map { it.status })
        assertEquals(SessionType.SHORT_BREAK, f.history.rows.last().type)
        assertEquals(60L, f.history.rows.last().actualDurationSeconds)
        assertEquals(1, f.events.size)
    }

    @Test fun cancelLongBreakReturnsToFirstFocus() = runTest {
        val f = fixture(PomodoroSettings(cyclesBeforeLongBreak = 1, autoStartBreak = true, autoStartFocus = true))
        f.complete()
        assertEquals(SessionType.LONG_BREAK, f.state.sessionType)
        f.clock.advance(3 * MINUTE)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(SessionType.LONG_BREAK, f.history.rows.last().type)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.last().status)
        assertEquals(180L, f.history.rows.last().actualDurationSeconds)
    }

    @Test fun resetBreakRetainsSessionCycleAndOriginalDurationWithoutAutoStart() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.complete()
        f.clock.advance(MINUTE)
        runCurrent()
        f.settings.value = f.settings.value.copy(shortBreakDurationMinutes = 12)
        runCurrent()
        f.engine.execute(PomodoroAction.RESET)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(1, f.state.completedFocusCount)
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertEquals(5 * MINUTE, f.state.remainingTimeMillis)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.last().status)
        assertEquals(60L, f.history.rows.last().actualDurationSeconds)
        f.engine.execute(PomodoroAction.RESET)
        assertEquals(2, f.history.rows.size)
    }

    @Test fun unrelatedSettingsChangeDoesNotReplaceResetDuration() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        runCurrent()
        f.settings.value = f.settings.value.copy(focusDurationMinutes = 40)
        runCurrent()
        f.engine.execute(PomodoroAction.RESET)
        f.settings.value = f.settings.value.copy(theme = AppTheme.DARK, soundEnabled = false)
        runCurrent()
        assertEquals(25 * MINUTE, f.state.totalDurationMillis)
        assertTrue(f.state.retainDurationOnIdle)
    }

    @Test fun cancelAtExactDeadlineWinsOverAutomation() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(25 * MINUTE)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
        assertEquals(1_500L, f.history.rows.single().actualDurationSeconds)
        assertTrue(f.events.isEmpty())
    }

    @Test fun completionHappensExactlyAtDeadlineAndOnlyOnce() = runTest {
        val f = fixture(PomodoroSettings(focusDurationMinutes = 1))
        f.engine.execute(PomodoroAction.START)
        val deadline = f.state.expectedEndAt
        f.clock.advance(MINUTE - 1)
        f.engine.refresh()
        assertEquals(1L, f.state.remainingTimeMillis)
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertTrue(f.history.rows.isEmpty())
        f.clock.advance(1)
        repeat(10) { f.engine.refresh() }
        assertEquals(1, f.history.rows.size)
        assertEquals(deadline, f.history.rows.single().endedAt)
        assertEquals(1, f.events.size)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
    }

    @Test fun pauseAtExactDeadlineCompletesInsteadOfPausingNextSession() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(25 * MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertEquals(SessionStatus.COMPLETED, f.history.rows.single().status)
    }

    @Test fun interruptedCancelRecoversWithoutDuplicateOrAutomaticRestart() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(MINUTE)
        f.store.failWrite = f.store.writes + 2
        f.engine.execute(PomodoroAction.CANCEL)
        assertEquals(PendingTransition.CANCEL, f.store.value?.pendingTransition)
        val recovered = f.recreate(this)
        recovered.restore()
        assertEquals(TimerState.IDLE, recovered.state.value.timerState)
        assertEquals(SessionType.FOCUS, recovered.state.value.sessionType)
        assertEquals(0, recovered.state.value.completedFocusCount)
        assertEquals(1, f.history.rows.size)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
    }

    @Test fun cancelPendingCompletedHistoryPreservesOutcomeButSuppressesSuccessor() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        f.store.failWrite = f.store.writes + 2
        f.clock.advance(25 * MINUTE)
        f.engine.refresh()
        assertEquals(PendingTransition.COMPLETE, f.state.pendingTransition)
        assertEquals(1, f.history.rows.size)
        f.engine.execute(PomodoroAction.CANCEL)
        assertCancelledSequence(f)
        assertEquals(1, f.history.rows.size)
        assertEquals(SessionStatus.COMPLETED, f.history.rows.single().status)
        assertTrue(f.events.isEmpty())
    }

    @Test fun cancelPendingHistoryFailureIsDurableAcrossProcessDeath() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(25 * MINUTE)
        f.history.fail = true
        f.engine.refresh()
        f.engine.execute(PomodoroAction.CANCEL)
        assertTrue(f.store.value!!.cancelAfterTransition)
        f.history.fail = false
        val recovered = f.recreate(this)
        recovered.restore()
        assertEquals(TimerState.IDLE, recovered.state.value.timerState)
        assertEquals(SessionType.FOCUS, recovered.state.value.sessionType)
        assertEquals(0, recovered.state.value.completedFocusCount)
        assertEquals(1, f.history.rows.size)
    }

    @Test fun rapidStartPauseResumeCancelCommandsRemainSerialized() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.restore()
        runCurrent()
        f.store.gate = CompletableDeferred()
        val start = async { f.engine.execute(PomodoroAction.START) }
        runCurrent()
        val later = listOf(PomodoroAction.PAUSE, PomodoroAction.RESUME, PomodoroAction.CANCEL)
            .map { action -> async { f.engine.execute(action) } }
        runCurrent()
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertTrue(later.none { it.isCompleted })
        f.store.gate!!.complete(Unit)
        start.await()
        later.awaitAll()
        assertCancelledSequence(f)
        assertEquals(1, f.history.rows.size)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
        assertTrue(f.events.isEmpty())
    }

    @Test fun malformedCompletedSnapshotUsesFullPlannedDuration() = runTest {
        val f = fixture()
        f.store.value = PomodoroState(
            sessionId = "recovered", timerState = TimerState.COMPLETED,
            totalDurationMillis = 25 * MINUTE, remainingTimeMillis = 25 * MINUTE,
            startedAt = f.clock.time - 25 * MINUTE
        )
        f.engine.restore()
        assertEquals(SessionStatus.COMPLETED, f.history.rows.single().status)
        assertEquals(1_500L, f.history.rows.single().actualDurationSeconds)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
    }

    @Test fun staleSessionCommandCannotAffectAutomaticallyStartedSuccessor() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true))
        f.engine.execute(PomodoroAction.START)
        val oldId = f.state.sessionId
        f.clock.advance(25 * MINUTE)
        f.engine.refresh()
        val breakId = f.state.sessionId
        f.engine.executeForSession(PomodoroAction.SKIP, oldId)
        f.engine.executeForSession(PomodoroAction.CANCEL, oldId)
        assertEquals(breakId, f.state.sessionId)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertEquals(1, f.history.rows.size)
    }

    @Test fun coldCancelWithMatchingIdentityDoesNotAutoAdvanceExpiredSession() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        val originalId = f.state.sessionId
        f.clock.advance(25 * MINUTE)
        val recovered = f.recreate(this)
        recovered.executeForSession(PomodoroAction.CANCEL, originalId)
        assertEquals(TimerState.IDLE, recovered.state.value.timerState)
        assertEquals(SessionType.FOCUS, recovered.state.value.sessionType)
        assertEquals(0, recovered.state.value.completedFocusCount)
        assertEquals(SessionStatus.CANCELLED, f.history.rows.single().status)
    }

    @Test fun cancellingCallerDuringStartWriteStillPublishesPersistedRunningState() = runTest {
        val f = fixture()
        f.engine.restore()
        runCurrent()
        f.store.gate = CompletableDeferred()
        val command = launch { f.engine.execute(PomodoroAction.START) }
        runCurrent()
        command.cancel()
        runCurrent()
        assertFalse(command.isCompleted)
        f.store.gate!!.complete(Unit)
        command.join()
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertEquals(f.store.value, f.state)
        assertNotNull(f.state.expectedEndAt)
        assertNull(f.state.error)
    }

    @Test fun cancellingCallerDuringCompletionCommitDoesNotLoseCompletionEvent() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        val sessionId = f.state.sessionId
        f.clock.advance(25 * MINUTE)
        f.store.gate = CompletableDeferred()
        f.store.gateWriteNumber = f.store.writes + 2
        val completion = launch { f.engine.refresh() }
        runCurrent()
        assertEquals(PendingTransition.COMPLETE, f.state.pendingTransition)
        completion.cancel()
        runCurrent()
        assertFalse(completion.isCompleted)
        f.store.gate!!.complete(Unit)
        completion.join()
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(f.store.value, f.state)
        assertEquals(listOf(CompletionEvent(sessionId, SessionType.FOCUS, SessionType.SHORT_BREAK)), f.events)
        assertEquals(1, f.history.rows.size)
    }

    @Test fun slowCompletionConsumerCannotOverflowAndSilentlyDropEvents() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        val deliveryGate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.engine.completions.collect {
                deliveryGate.await()
                delivered += it.sessionId
            }
        }
        val producer = launch { repeat(24) { f.complete() } }
        runCurrent()
        assertFalse(producer.isCompleted)
        deliveryGate.complete(Unit)
        producer.join()
        runCurrent()
        assertEquals(24, f.history.rows.size)
        assertEquals(24, f.events.size)
        assertEquals(24, delivered.size)
        assertEquals(f.history.rows.map { it.id }, delivered)
    }

    private fun assertHistory(session: PomodoroSession, id: String, type: SessionType, duration: Long) {
        assertEquals(id, session.id)
        assertEquals(type, session.type)
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(duration / 1_000, session.plannedDurationSeconds)
        assertEquals(duration / 1_000, session.actualDurationSeconds)
        assertEquals(duration, session.endedAt - session.startedAt)
    }

    private fun assertCancelledSequence(f: Fixture, duration: Long = 25 * MINUTE) {
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(0, f.state.completedFocusCount)
        assertEquals(1, f.state.currentCycle)
        assertEquals(duration, f.state.totalDurationMillis)
        assertEquals(duration, f.state.remainingTimeMillis)
        assertNull(f.state.startedAt)
        assertNull(f.state.expectedEndAt)
        assertNull(f.state.pendingTransition)
        assertNull(f.state.error)
    }

    private fun TestScope.fixture(settings: PomodoroSettings = PomodoroSettings()): Fixture {
        val store = FakeStore()
        val preferences = MutableStateFlow(settings)
        val settingsRepository = object : SettingsRepository {
            override fun observeSettings() = preferences
            override suspend fun updateSettings(settings: PomodoroSettings) { preferences.value = settings.validated() }
        }
        val clock = FakeClock()
        val history = FakeHistory()
        val engine = PomodoroRepositoryImpl(store, settingsRepository, history, clock, backgroundScope)
        val events = mutableListOf<CompletionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.completions.collect { events += it } }
        return Fixture(store, preferences, settingsRepository, clock, history, engine, events)
    }

    private data class Fixture(
        val store: FakeStore,
        val settings: MutableStateFlow<PomodoroSettings>,
        val settingsRepository: SettingsRepository,
        val clock: FakeClock,
        val history: FakeHistory,
        val engine: PomodoroRepositoryImpl,
        val events: MutableList<CompletionEvent>
    ) {
        val state get() = engine.state.value
        suspend fun complete(): String {
            if (state.timerState != TimerState.RUNNING) engine.execute(PomodoroAction.START)
            val id = state.sessionId
            clock.advance(state.remainingTimeMillis)
            engine.refresh()
            return id
        }
        fun recreate(scope: TestScope) = PomodoroRepositoryImpl(store, settingsRepository, history, clock, scope.backgroundScope)
    }

    private class FakeClock(var time: Long = 1_700_000_000_000L) : PomodoroClock {
        override fun now() = time
        fun advance(duration: Long) { time += duration }
    }

    private class FakeStore : TimerStateStore {
        var value: PomodoroState? = null
        var writes = 0
        var failWrite = -1
        var gate: CompletableDeferred<Unit>? = null
        var gateWriteNumber: Int? = null
        override suspend fun read() = value
        override suspend fun write(state: PomodoroState) {
            writes++
            check(writes != failWrite)
            if (gateWriteNumber == null || writes == gateWriteNumber) gate?.await()
            value = state
        }
    }

    private class FakeHistory : HistoryRepository {
        private val records = linkedMapOf<String, PomodoroSession>()
        val rows get() = records.values.toList()
        var fail = false
        override fun observeSessions(startDate: Long?, endDate: Long?) = MutableStateFlow(rows)
        override suspend fun save(session: PomodoroSession) {
            check(!fail)
            records.putIfAbsent(session.id, session)
        }
    }

    private companion object { const val MINUTE = 60_000L }
}
