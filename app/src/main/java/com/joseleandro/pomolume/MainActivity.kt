package com.joseleandro.pomolume

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.pomolume.core.navigation.PomoLumeApp
import com.joseleandro.pomolume.core.notification.PomodoroNotifications
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private data class NotificationRequest(val action: String, val sessionId: String?)
    private val notificationRequest = MutableStateFlow<NotificationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.getString("pendingAction")?.let {
            notificationRequest.value = NotificationRequest(it, savedInstanceState.getString("pendingSession"))
        }
        consumeNotificationIntent(intent)
        setContent {
            val pending by notificationRequest.collectAsStateWithLifecycle()
            PomoLumeApp(notificationAction = pending?.action, notificationSessionId = pending?.sessionId,
                onNotificationActionConsumed = { notificationRequest.value = null })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        notificationRequest.value?.let {
            outState.putString("pendingAction", it.action)
            outState.putString("pendingSession", it.sessionId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        intent?.getStringExtra(PomodoroNotifications.EXTRA_CONFIRM_ACTION)?.let {
            notificationRequest.value = NotificationRequest(it, intent.getStringExtra(PomodoroNotifications.EXTRA_SESSION_ID))
            intent.removeExtra(PomodoroNotifications.EXTRA_CONFIRM_ACTION)
            intent.removeExtra(PomodoroNotifications.EXTRA_SESSION_ID)
        }
    }
}
