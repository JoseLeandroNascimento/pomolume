package com.joseleandro.pomolume.core.notification

import com.joseleandro.pomolume.feature.pomodoro.domain.CompletionEvent
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Retry transient settings/Binder failures without substituting default sound preferences.
 * Delivery is best effort across an abrupt process kill; session history is already durable.
 */
internal suspend fun deliverCompletion(
    event: CompletionEvent,
    readSettings: suspend () -> PomodoroSettings,
    notify: (CompletionEvent, PomodoroSettings) -> Unit
): Boolean {
    repeat(3) { attempt ->
        try {
            notify(event, readSettings())
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (attempt < 2) delay(250L * (attempt + 1))
        }
    }
    return false
}
