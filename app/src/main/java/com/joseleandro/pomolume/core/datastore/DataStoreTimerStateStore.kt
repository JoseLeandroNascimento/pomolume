package com.joseleandro.pomolume.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerStateStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists only transitions, never countdown ticks. Storage failures propagate so callers
 * cannot announce a transition as successful before it has been durably saved.
 */
class DataStoreTimerStateStore(
    private val dataStore: DataStore<Preferences>
) : TimerStateStore {
    override suspend fun read(): PomodoroState? {
        val payload = dataStore.data.first()[TIMER_STATE] ?: return null
        val decoded = try {
            json.decodeFromString<PomodoroState>(payload)
        } catch (_: SerializationException) {
            dataStore.edit { it.remove(TIMER_STATE) }
            return null
        } catch (_: IllegalArgumentException) {
            dataStore.edit { it.remove(TIMER_STATE) }
            return null
        }
        val duration = decoded.totalDurationMillis.coerceIn(60_000L, 120 * 60_000L)
        val remaining = decoded.remainingTimeMillis.coerceIn(0L, duration)
        // A partially written or invalid active session must not create phantom history.
        if (decoded.timerState != TimerState.IDLE &&
            (decoded.sessionId.isBlank() || decoded.startedAt == null || decoded.startedAt < 0L)
        ) {
            dataStore.edit { it.remove(TIMER_STATE) }
            return null
        }
        return decoded.copy(
            totalDurationMillis = duration,
            remainingTimeMillis = remaining,
            cyclesUntilLongBreak = decoded.cyclesUntilLongBreak.coerceIn(1, 10),
            completedFocusCount = decoded.completedFocusCount.coerceIn(0, 10),
            timerState = if (decoded.timerState == TimerState.RUNNING &&
                (decoded.expectedEndAt == null || decoded.expectedEndAt < decoded.startedAt!!)
            ) TimerState.PAUSED else decoded.timerState,
            isLoading = false,
            error = null
        )
    }

    override suspend fun write(state: PomodoroState) {
        val payload = json.encodeToString(state.copy(isLoading = false, error = null))
        dataStore.edit { it[TIMER_STATE] = payload }
    }

    companion object {
        internal val TIMER_STATE = stringPreferencesKey("pomodoro_timer_v1")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
