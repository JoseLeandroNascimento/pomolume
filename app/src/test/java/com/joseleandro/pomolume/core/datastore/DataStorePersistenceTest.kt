package com.joseleandro.pomolume.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import com.joseleandro.pomolume.feature.settings.data.DataStoreSettingsRepository
import com.joseleandro.pomolume.feature.settings.domain.AppTheme
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DataStorePersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultsAreAvailableBeforeAnyWrite() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "defaults.preferences_pb")
        }
        assertEquals(PomodoroSettings(), DataStoreSettingsRepository(dataStore).observeSettings().first())
        assertNull(DataStoreTimerStateStore(dataStore).read())
    }

    @Test fun settingsAndPausedTimerSurviveStoreRecreation() = runTest {
        val file = File(folder.root, "restart.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope) { file }
        val settings = PomodoroSettings(
            focusDurationMinutes = 42,
            shortBreakDurationMinutes = 7,
            longBreakDurationMinutes = 21,
            cyclesBeforeLongBreak = 5,
            autoStartBreak = true,
            autoStartFocus = true,
            notificationsEnabled = false,
            soundEnabled = false,
            vibrationEnabled = false,
            keepScreenOn = true,
            confirmSkip = false,
            confirmReset = false,
            theme = AppTheme.DARK
        )
        val timer = PomodoroState(
            sessionId = "stable-session",
            sessionType = SessionType.FOCUS,
            timerState = TimerState.PAUSED,
            startedAt = 1_000,
            pausedAt = 241_000,
            expectedEndAt = null,
            totalDurationMillis = 42 * 60_000L,
            remainingTimeMillis = 38 * 60_000L,
            completedFocusCount = 2,
            cyclesUntilLongBreak = 5,
            isLoading = false
        )
        DataStoreSettingsRepository(firstStore).updateSettings(settings)
        DataStoreTimerStateStore(firstStore).write(timer)
        firstScope.cancel()
        runCurrent()

        val recreated = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        assertEquals(settings, DataStoreSettingsRepository(recreated).observeSettings().first())
        assertEquals(timer, DataStoreTimerStateStore(recreated).read())
    }

    @Test fun durationsAreValidatedAtStorageBoundary() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "validation.preferences_pb")
        }
        val repository = DataStoreSettingsRepository(store)
        repository.updateSettings(
            PomodoroSettings(focusDurationMinutes = 0, shortBreakDurationMinutes = 100,
                longBreakDurationMinutes = -5, cyclesBeforeLongBreak = 99)
        )
        val settings = repository.observeSettings().first()
        assertEquals(1, settings.focusDurationMinutes)
        assertEquals(60, settings.shortBreakDurationMinutes)
        assertEquals(1, settings.longBreakDurationMinutes)
        assertEquals(10, settings.cyclesBeforeLongBreak)
    }

    @Test fun damagedJsonUsesSafeDefaultsAndDiscardsInvalidTimer() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "damaged.preferences_pb")
        }
        store.edit {
            it[DataStoreSettingsRepository.SETTINGS] = "{broken"
            it[DataStoreTimerStateStore.TIMER_STATE] = "{broken"
        }
        assertEquals(PomodoroSettings(), DataStoreSettingsRepository(store).observeSettings().first())
        assertNull(DataStoreTimerStateStore(store).read())
        assertNull(store.data.first()[DataStoreTimerStateStore.TIMER_STATE])
    }

    @Test fun missingRunningDeadlineRecoversAsPaused() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "deadline.preferences_pb")
        }
        val repository = DataStoreTimerStateStore(store)
        repository.write(
            PomodoroState(sessionId = "valid-id", timerState = TimerState.RUNNING,
                startedAt = 1_000, remainingTimeMillis = 1_200_000, expectedEndAt = null)
        )
        val recovered = repository.read()!!
        assertEquals(TimerState.PAUSED, recovered.timerState)
        assertEquals(1_200_000L, recovered.remainingTimeMillis)
        assertFalse(recovered.isLoading)
    }

    @Test fun activeTimerWithoutStableIdentityIsDiscarded() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "identity.preferences_pb")
        }
        val repository = DataStoreTimerStateStore(store)
        repository.write(PomodoroState(timerState = TimerState.RUNNING, expectedEndAt = 10_000))
        assertNull(repository.read())
    }

    @Test fun ioFailuresReachCallerInsteadOfReportingSuccessfulPersistence() = runTest {
        val brokenStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("Disk unavailable")
        }
        assertTrue(runCatching {
            DataStoreSettingsRepository(brokenStore).observeSettings().first()
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            DataStoreSettingsRepository(brokenStore).updateSettings(PomodoroSettings())
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            DataStoreTimerStateStore(brokenStore).write(PomodoroState())
        }.exceptionOrNull() is IOException)
    }
}
