package com.joseleandro.pomolume.core.notification

import com.joseleandro.pomolume.feature.pomodoro.domain.CompletionEvent
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CompletionDeliveryTest {
    @Test fun retriesSettingsFailureWithoutReplacingSoundOrVibrationChoices() = runTest {
        var reads = 0
        val delivered = mutableListOf<PomodoroSettings>()
        val preferences = PomodoroSettings(soundEnabled = false, vibrationEnabled = true)
        val success = deliverCompletion(event,
            readSettings = {
                reads++
                check(reads == 3) { "Temporary DataStore failure" }
                preferences
            },
            notify = { _, settings -> delivered += settings }
        )
        assertTrue(success)
        assertEquals(3, reads)
        assertEquals(listOf(preferences), delivered)
    }

    @Test fun transientNotificationFailureRetriesSameSessionIdentity() = runTest {
        val identities = mutableListOf<String>()
        val success = deliverCompletion(event,
            readSettings = { PomodoroSettings(soundEnabled = true, vibrationEnabled = false) },
            notify = { completion, settings ->
                assertTrue(settings.soundEnabled)
                assertFalse(settings.vibrationEnabled)
                identities += completion.sessionId
                check(identities.size > 1) { "Temporary notification Binder failure" }
            }
        )
        assertTrue(success)
        assertEquals(listOf("session", "session"), identities)
    }

    @Test fun permanentFailureHasBoundedRetriesSoIdleServiceCanStop() = runTest {
        var attempts = 0
        val success = deliverCompletion(event,
            readSettings = { PomodoroSettings() },
            notify = { _, _ -> attempts++; error("Notification unavailable") }
        )
        assertFalse(success)
        assertEquals(3, attempts)
    }

    @Test fun cancellationIsPropagatedWithoutRetrying() = runTest {
        var reads = 0
        var cancelled = false
        try {
            deliverCompletion(event,
                readSettings = { reads++; throw CancellationException("Service destroyed") },
                notify = { _, _ -> fail("Must not notify after cancellation") }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, reads)
    }

    private companion object {
        val event = CompletionEvent("session", SessionType.FOCUS, SessionType.SHORT_BREAK)
    }
}
