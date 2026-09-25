package com.joseleandro.pomolume.feature.pomodoro.data

import com.joseleandro.pomolume.feature.history.domain.HistoryRepository
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import com.joseleandro.pomolume.feature.history.domain.SessionStatus
import com.joseleandro.pomolume.feature.pomodoro.domain.CompletionEvent
import com.joseleandro.pomolume.feature.pomodoro.domain.PendingTransition
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroAction
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroClock
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerStateStore
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import com.joseleandro.pomolume.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroRepositoryImplTest {
    @Test
    fun firstRestoreLoadsDefaultsAndIsImmediatelyUsable() = runTest {
        val f = fixture()
        f.engine.restore()
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(25 * MINUTE, f.state.remainingTimeMillis)
        assertEquals(4, f.state.cyclesUntilLongBreak)
        assertFalse(f.state.isLoading)
        assertNull(f.state.error)
    }

    @Test
    fun startPersistsIdentityAndAbsoluteDeadline() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertTrue(f.state.sessionId.isNotBlank())
        assertEquals(f.clock.time, f.state.startedAt)
        assertEquals(f.clock.time + 25 * MINUTE, f.state.expectedEndAt)
        assertEquals(f.state, f.store.saved)
    }

    @Test
    fun pauseAndResumeDoNotConsumeTimeSpentPaused() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(5 * MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        assertEquals(20 * MINUTE, f.state.remainingTimeMillis)
        assertNull(f.state.expectedEndAt)
        f.clock.advance(3 * 60 * MINUTE)
        f.engine.refresh()
        assertEquals(20 * MINUTE, f.state.remainingTimeMillis)
        f.engine.execute(PomodoroAction.RESUME)
        f.clock.advance(1_000)
        f.engine.refresh()
        assertEquals(20 * MINUTE - 1_000, f.state.remainingTimeMillis)
    }

    @Test
    fun remainingTimeUsesClockInsteadOfNumberOfTicks() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        val writesAfterStart = f.store.writes
        f.clock.advance(9 * MINUTE + 321)
        f.engine.refresh()
        assertEquals(16 * MINUTE - 321, f.state.remainingTimeMillis)
        repeat(10) { f.engine.refresh() }
        assertEquals(writesAfterStart, f.store.writes)
    }

    @Test
    fun resetRecordsCancelledSessionAndReturnsToOriginalIdleDuration() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(3 * MINUTE)
        f.engine.execute(PomodoroAction.RESET)
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertEquals(25 * MINUTE, f.state.remainingTimeMillis)
        assertEquals(0, f.state.completedFocusCount)
        assertEquals(SessionStatus.CANCELLED, f.history.sessions.single().status)
        assertEquals(180L, f.history.sessions.single().actualDurationSeconds)
    }

    @Test
    fun resetWithoutStartingDoesNotCreateHistory() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.RESET)
        assertTrue(f.history.sessions.isEmpty())
        assertEquals(TimerState.IDLE, f.state.timerState)
    }

    @Test
    fun skipRecordsElapsedTimeAndDoesNotCountFocusAsCompleted() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(2 * MINUTE)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(0, f.state.completedFocusCount)
        assertEquals(SessionStatus.SKIPPED, f.history.sessions.single().status)
        assertEquals(120L, f.history.sessions.single().actualDurationSeconds)
    }

    @Test
    fun skippingIdleSessionRecordsZeroDuration() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(SessionStatus.SKIPPED, f.history.sessions.single().status)
        assertEquals(0L, f.history.sessions.single().actualDurationSeconds)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
    }

    @Test
    fun fourthCompletedFocusSelectsLongBreakAndLongBreakResetsCycle() = runTest {
        val f = fixture()
        repeat(4) { index ->
            assertEquals(SessionType.FOCUS, f.state.sessionType)
            f.complete()
            assertEquals(index + 1, f.state.completedFocusCount)
            val expectedBreak = if (index == 3) SessionType.LONG_BREAK else SessionType.SHORT_BREAK
            assertEquals(expectedBreak, f.state.sessionType)
            assertEquals(if (index == 3) 15 * MINUTE else 5 * MINUTE, f.state.totalDurationMillis)
            f.complete()
        }
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(0, f.state.completedFocusCount)
        assertEquals(1, f.state.currentCycle)
        assertEquals(8, f.history.sessions.size)
        assertEquals(4, f.history.sessions.count { it.type == SessionType.FOCUS })
    }

    @Test
    fun skippedFocusAndBreakDoNotAdvanceCycle() = runTest {
        val f = fixture()
        f.complete()
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(1, f.state.completedFocusCount)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(1, f.state.completedFocusCount)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(2, f.state.currentCycle)
    }

    @Test
    fun skippingLongBreakStartsFreshCycle() = runTest {
        val f = fixture(PomodoroSettings(cyclesBeforeLongBreak = 1))
        f.complete()
        assertEquals(SessionType.LONG_BREAK, f.state.sessionType)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(0, f.state.completedFocusCount)
    }

    @Test
    fun automaticStartsRespectIndependentPreferences() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = false))
        f.complete()
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        f.clock.advance(f.state.totalDurationMillis)
        f.engine.refresh()
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(TimerState.IDLE, f.state.timerState)
    }

    @Test
    fun focusCanStartAutomaticallyWithoutAutomaticallyStartingBreaks() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = false, autoStartFocus = true))
        f.complete()
        assertEquals(TimerState.IDLE, f.state.timerState)
        f.complete()
        assertEquals(SessionType.FOCUS, f.state.sessionType)
        assertEquals(TimerState.RUNNING, f.state.timerState)
    }

    @Test
    fun settingsUpdateIdleDurationButNeverChangeRunningSession() = runTest {
        val f = fixture()
        f.engine.restore()
        runCurrent()
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 30))
        runCurrent()
        assertEquals(30 * MINUTE, f.state.totalDurationMillis)
        f.engine.execute(PomodoroAction.START)
        val deadline = f.state.expectedEndAt
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 45, shortBreakDurationMinutes = 9))
        runCurrent()
        assertEquals(30 * MINUTE, f.state.totalDurationMillis)
        assertEquals(deadline, f.state.expectedEndAt)
        f.clock.advance(30 * MINUTE)
        f.engine.refresh()
        assertEquals(9 * MINUTE, f.state.totalDurationMillis)
        f.complete()
        assertEquals(45 * MINUTE, f.state.totalDurationMillis)
    }

    @Test
    fun resetRetainsOriginalDurationAfterSettingsChangedAndProcessRestarts() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        runCurrent()
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 40))
        runCurrent()
        f.clock.advance(MINUTE)
        f.engine.execute(PomodoroAction.RESET)
        assertEquals(25 * MINUTE, f.state.remainingTimeMillis)
        val restored = f.recreate(this)
        restored.restore()
        assertEquals(25 * MINUTE, restored.state.value.remainingTimeMillis)
        assertEquals(1, f.history.sessions.size)
    }

    @Test
    fun pausedSessionDurationDoesNotChangeWithSettings() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        runCurrent()
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 1))
        runCurrent()
        assertEquals(25 * MINUTE, f.state.totalDurationMillis)
        assertEquals(24 * MINUTE, f.state.remainingTimeMillis)
    }

    @Test
    fun recreatingRepositoryRecoversExactRemainingTimeAndIdentity() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        val id = f.state.sessionId
        f.clock.advance(7 * MINUTE)
        val recreated = f.recreate(this)
        recreated.restore()
        assertEquals(id, recreated.state.value.sessionId)
        assertEquals(18 * MINUTE, recreated.state.value.remainingTimeMillis)
        assertEquals(TimerState.RUNNING, recreated.state.value.timerState)
    }

    @Test
    fun pausedSessionSurvivesRecreationWithoutLosingTime() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(7 * MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        f.clock.advance(24 * 60 * MINUTE)
        val recreated = f.recreate(this)
        recreated.restore()
        assertEquals(18 * MINUTE, recreated.state.value.remainingTimeMillis)
        assertEquals(TimerState.PAUSED, recreated.state.value.timerState)
    }

    @Test
    fun longOfflineGapCompletesOnlyExistingSessionAndStartsSuccessorNow() = runTest {
        val f = fixture(PomodoroSettings(autoStartBreak = true, autoStartFocus = true))
        f.engine.execute(PomodoroAction.START)
        val originalDeadline = f.state.expectedEndAt
        f.clock.advance(7 * 24 * 60 * MINUTE)
        val recreated = f.recreate(this)
        recreated.restore()
        assertEquals(1, f.history.sessions.size)
        assertEquals(originalDeadline, f.history.sessions.single().endedAt)
        assertEquals(SessionType.SHORT_BREAK, recreated.state.value.sessionType)
        assertEquals(f.clock.time, recreated.state.value.startedAt)
        assertEquals(5 * MINUTE, recreated.state.value.remainingTimeMillis)
    }

    @Test
    fun repeatedRestoreCannotDuplicateCompletedHistory() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(25 * MINUTE)
        repeat(5) { f.engine.restore() }
        assertEquals(1, f.history.sessions.size)
        assertEquals(1, f.state.completedFocusCount)
    }

    @Test
    fun historyFailureRetainsDurableIntentAndRetries() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.history.failSaves = true
        f.clock.advance(25 * MINUTE)
        f.engine.refresh()
        assertEquals(PendingTransition.COMPLETE, f.store.saved?.pendingTransition)
        assertNotNull(f.state.error)
        assertTrue(f.history.sessions.isEmpty())
        f.history.failSaves = false
        f.engine.refresh()
        assertEquals(1, f.history.sessions.size)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertNull(f.state.error)
    }

    @Test
    fun failureAfterHistoryInsertIsDeduplicatedAfterProcessDeath() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        // Next write is durable terminal intent, the following is next-session commit.
        f.store.failWriteNumber = f.store.writes + 2
        f.clock.advance(25 * MINUTE)
        f.engine.refresh()
        assertEquals(1, f.history.sessions.size)
        assertNotNull(f.state.error)
        assertEquals(PendingTransition.COMPLETE, f.store.saved?.pendingTransition)
        val recreated = f.recreate(this)
        recreated.restore()
        assertEquals(1, f.history.sessions.size)
        assertEquals(SessionType.SHORT_BREAK, recreated.state.value.sessionType)
        assertEquals(1, recreated.state.value.completedFocusCount)
    }

    @Test
    fun failureBeforeTerminalIntentDoesNotLoseSession() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.store.failWriteNumber = f.store.writes + 1
        f.clock.advance(25 * MINUTE)
        f.engine.refresh()
        assertEquals(TimerState.RUNNING, f.store.saved?.timerState)
        assertTrue(f.history.sessions.isEmpty())
        f.engine.refresh()
        assertEquals(1, f.history.sessions.size)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
    }

    @Test
    fun startWriteFailureKeepsIdleStateAndAllowsRetry() = runTest {
        val f = fixture()
        f.engine.restore()
        f.store.failWriteNumber = f.store.writes + 1
        f.engine.execute(PomodoroAction.START)
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertNotNull(f.state.error)
        f.engine.execute(PomodoroAction.START)
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertNull(f.state.error)
    }

    @Test
    fun storeReadFailureCanBeRetriedWithoutReplacingSavedSession() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        val id = f.state.sessionId
        val recreated = f.recreate(this)
        f.store.failReads = true
        recreated.restore()
        assertNotNull(recreated.state.value.error)
        assertEquals(id, f.store.saved?.sessionId)
        f.store.failReads = false
        recreated.restore()
        assertEquals(id, recreated.state.value.sessionId)
        assertNull(recreated.state.value.error)
    }

    @Test
    fun cancelledTransitionAlsoSurvivesFailureAndDeduplicates() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(2 * MINUTE)
        f.store.failWriteNumber = f.store.writes + 2
        f.engine.execute(PomodoroAction.RESET)
        assertEquals(SessionStatus.CANCELLED, f.history.sessions.single().status)
        val recreated = f.recreate(this)
        recreated.restore()
        assertEquals(1, f.history.sessions.size)
        assertEquals(TimerState.IDLE, recreated.state.value.timerState)
        assertEquals(25 * MINUTE, recreated.state.value.remainingTimeMillis)
    }

    @Test
    fun cancellationIsPropagatedInsteadOfConvertedToStorageError() = runTest {
        val f = fixture()
        f.engine.restore()
        f.store.cancelWrites = true
        var cancelled = false
        try {
            f.engine.execute(PomodoroAction.START)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertNull(f.state.error)
    }

    @Test
    fun concurrentStartsAreSerializedAndKeepOneSession() = runTest {
        val f = fixture()
        f.engine.restore()
        val writes = f.store.writes
        (1..20).map { async { f.engine.execute(PomodoroAction.START) } }.awaitAll()
        assertEquals(writes + 1, f.store.writes)
        assertEquals(TimerState.RUNNING, f.state.timerState)
        assertTrue(f.history.sessions.isEmpty())
    }

    @Test
    fun completionEventIsEmittedOnlyAfterHistoryAndStateCommit() = runTest {
        val f = fixture()
        val events = mutableListOf<CompletionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.engine.completions.collect { events += it }
        }
        f.engine.execute(PomodoroAction.START)
        val originalId = f.state.sessionId
        f.clock.advance(25 * MINUTE)
        f.history.failSaves = true
        f.engine.refresh()
        assertTrue(events.isEmpty())
        f.history.failSaves = false
        repeat(3) { f.engine.refresh() }
        assertEquals(listOf(CompletionEvent(originalId, SessionType.FOCUS, SessionType.SHORT_BREAK)), events)
    }

    @Test
    fun actionAgainstExpiredSessionDoesNotMutateItsSuccessor() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(25 * MINUTE)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(1, f.history.sessions.size)
        assertEquals(SessionStatus.COMPLETED, f.history.sessions.single().status)
        assertEquals(SessionType.SHORT_BREAK, f.state.sessionType)
        assertEquals(TimerState.IDLE, f.state.timerState)
    }

    @Test
    fun historyDurationExcludesPauseEvenAfterManyHours() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.clock.advance(2 * MINUTE)
        f.engine.execute(PomodoroAction.PAUSE)
        f.clock.advance(8 * 60 * MINUTE)
        f.engine.execute(PomodoroAction.RESUME)
        f.clock.advance(MINUTE)
        f.engine.execute(PomodoroAction.SKIP)
        assertEquals(180L, f.history.sessions.single().actualDurationSeconds)
        assertEquals(1_500L, f.history.sessions.single().plannedDurationSeconds)
    }

    @Test
    fun invalidPersistedValuesAreSafelyNormalized() = runTest {
        val f = fixture()
        f.store.saved = PomodoroState(
            timerState = TimerState.PAUSED,
            totalDurationMillis = -1,
            remainingTimeMillis = Long.MAX_VALUE,
            completedFocusCount = -20,
            cyclesUntilLongBreak = 0
        )
        f.engine.restore()
        assertEquals(TimerState.PAUSED, f.state.timerState)
        assertEquals(MINUTE, f.state.totalDurationMillis)
        assertEquals(MINUTE, f.state.remainingTimeMillis)
        assertEquals(0, f.state.completedFocusCount)
        assertNotNull(f.state.startedAt)
        assertTrue(f.state.sessionId.isNotBlank())
        assertNull(f.state.error)
    }

    @Test
    fun everyNewSessionGetsUniqueIdentity() = runTest {
        val f = fixture()
        f.complete()
        f.complete()
        assertNotEquals(f.history.sessions[0].id, f.history.sessions[1].id)
    }

    @Test
    fun settingsStoreFailureIsRetriedOnNextRefreshWithoutAnotherSettingsEmission() = runTest {
        val f = fixture()
        f.engine.restore()
        runCurrent()
        f.store.failWriteNumber = f.store.writes + 1
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 45))
        runCurrent()
        assertEquals(25 * MINUTE, f.state.totalDurationMillis)
        assertNotNull(f.state.error)
        f.engine.refresh()
        assertEquals(45 * MINUTE, f.state.totalDurationMillis)
        assertEquals(45 * MINUTE, f.store.saved?.totalDurationMillis)
        assertNull(f.state.error)
    }

    @Test
    fun successfulIdleRefreshClearsTransientError() = runTest {
        val f = fixture()
        f.engine.restore()
        f.store.failWriteNumber = f.store.writes + 1
        f.engine.execute(PomodoroAction.START)
        assertNotNull(f.state.error)
        f.engine.refresh()
        assertEquals(TimerState.IDLE, f.state.timerState)
        assertNull(f.state.error)
    }

    @Test
    fun settingsChangedAgainAfterResetCanReplaceRetainedDuration() = runTest {
        val f = fixture()
        f.engine.execute(PomodoroAction.START)
        f.engine.execute(PomodoroAction.RESET)
        runCurrent()
        f.store.failWriteNumber = f.store.writes + 1
        f.settings.updateSettings(PomodoroSettings(focusDurationMinutes = 40))
        runCurrent()
        assertEquals(25 * MINUTE, f.state.totalDurationMillis)
        f.engine.refresh()
        assertEquals(40 * MINUTE, f.state.totalDurationMillis)
        assertFalse(f.state.retainDurationOnIdle)
    }

    private fun TestScope.fixture(settings: PomodoroSettings = PomodoroSettings()): Fixture {
        val store = FakeTimerStore()
        val preferences = FakeSettingsRepository(settings)
        val history = FakeHistoryRepository()
        val clock = FakeClock()
        return Fixture(store, preferences, history, clock,
            PomodoroRepositoryImpl(store, preferences, history, clock, backgroundScope))
    }

    private data class Fixture(
        val store: FakeTimerStore,
        val settings: FakeSettingsRepository,
        val history: FakeHistoryRepository,
        val clock: FakeClock,
        val engine: PomodoroRepositoryImpl
    ) {
        val state: PomodoroState get() = engine.state.value
        suspend fun complete() {
            engine.execute(PomodoroAction.START)
            clock.advance(state.remainingTimeMillis)
            engine.refresh()
        }
        fun recreate(scope: TestScope) = PomodoroRepositoryImpl(store, settings, history, clock, scope.backgroundScope)
    }

    private class FakeClock(var time: Long = 1_700_000_000_000L) : PomodoroClock {
        override fun now() = time
        fun advance(millis: Long) { time += millis }
    }

    private class FakeTimerStore : TimerStateStore {
        var saved: PomodoroState? = null
        var writes = 0
        var failWriteNumber = -1
        var failReads = false
        var cancelWrites = false
        override suspend fun read(): PomodoroState? {
            check(!failReads) { "Storage temporarily unavailable" }
            return saved
        }
        override suspend fun write(state: PomodoroState) {
            if (cancelWrites) throw CancellationException("Test cancellation")
            writes++
            check(writes != failWriteNumber) { "Storage temporarily unavailable" }
            saved = state
        }
    }

    private class FakeSettingsRepository(initial: PomodoroSettings) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeSettings() = flow
        override suspend fun updateSettings(settings: PomodoroSettings) { flow.value = settings.validated() }
    }

    private class FakeHistoryRepository : HistoryRepository {
        private val records = linkedMapOf<String, PomodoroSession>()
        private val flow = MutableStateFlow<List<PomodoroSession>>(emptyList())
        val sessions: List<PomodoroSession> get() = records.values.toList()
        var failSaves = false
        override suspend fun save(session: PomodoroSession) {
            check(!failSaves) { "Database temporarily unavailable" }
            records.putIfAbsent(session.id, session)
            flow.value = sessions
        }
        override fun observeSessions(startDate: Long?, endDate: Long?): Flow<List<PomodoroSession>> =
            flow.map { list ->
                list.filter { (startDate == null || it.endedAt >= startDate) && (endDate == null || it.endedAt < endDate) }
            }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
