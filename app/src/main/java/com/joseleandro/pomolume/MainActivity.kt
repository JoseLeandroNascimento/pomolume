package com.joseleandro.pomolume

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.pomolume.core.navigation.PomoLumeApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notificationAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeNotificationIntent(intent)
        setContent {
            val pendingAction by notificationAction.collectAsStateWithLifecycle()
            PomoLumeApp(notificationAction = pendingAction, onNotificationActionConsumed = { notificationAction.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        intent?.getStringExtra("pomodoro_notification_action")?.let { action ->
            notificationAction.value = action
            intent.removeExtra("pomodoro_notification_action")
        }
    }
}
