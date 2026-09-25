package com.joseleandro.pomolume.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.joseleandro.pomolume.MainActivity
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.service.PomodoroService
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import java.util.Locale

class PomodoroNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                ACTIVE_CHANNEL, context.getString(R.string.notification_active_channel), NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null); enableVibration(false) })
        }
    }

    fun ongoing(state: PomodoroState): Notification {
        val paused = state.timerState == TimerState.PAUSED
        val seconds = (state.remainingTimeMillis.coerceAtLeast(0) + 999) / 1000
        val remaining = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        return NotificationCompat.Builder(context, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(if (paused) context.getString(R.string.notification_paused) else label(state.sessionType))
            .setContentText(when {
                state.isLoading -> context.getString(R.string.notification_loading)
                state.error != null -> context.getString(R.string.notification_saving)
                else -> context.getString(R.string.notification_remaining, remaining)
            })
            .setContentIntent(openApp())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (state.sessionId.isNotBlank() && (paused || state.timerState == TimerState.RUNNING)) {
                    addAction(0, context.getString(if (paused) R.string.notification_resume else R.string.notification_pause),
                        action(if (paused) PomodoroAction.RESUME else PomodoroAction.PAUSE, state.sessionId))
                    addAction(0, context.getString(if (paused) R.string.notification_reset else R.string.notification_skip),
                        destructiveAction(if (paused) PomodoroAction.RESET else PomodoroAction.SKIP, state.sessionId))
                    addAction(0, context.getString(R.string.notification_cancel),
                        destructiveAction(PomodoroAction.CANCEL, state.sessionId))
                }
            }
            .build()
    }

    fun update(state: PomodoroState) {
        if (canNotify()) {
            try {
                manager.notify(ONGOING_ID, ongoing(state))
            } catch (_: SecurityException) {
                // Notification access can change while the timer continues.
            }
        }
    }

    fun completed(event: CompletionEvent, settings: PomodoroSettings) {
        if (!settings.notificationsEnabled || !canNotify()) return
        // Channels are immutable after creation. A channel per sound/vibration combination
        // makes both app toggles effective while preserving the user's system overrides.
        val channel = "completion_${settings.soundEnabled}_${settings.vibrationEnabled}"
        val sound = if (settings.soundEnabled) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channel, context.getString(R.string.notification_completion_channel,
                    context.getString(if (settings.soundEnabled) R.string.notification_sound_on else R.string.notification_sound_off),
                    context.getString(if (settings.vibrationEnabled) R.string.notification_vibration_on else R.string.notification_vibration_off)),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(settings.vibrationEnabled)
                if (settings.vibrationEnabled) vibrationPattern = longArrayOf(0, 180, 100, 180)
            })
        }
        val title = when (event.sessionType) {
            SessionType.FOCUS -> context.getString(R.string.notification_focus_completed)
            SessionType.SHORT_BREAK -> context.getString(R.string.notification_short_break_completed)
            SessionType.LONG_BREAK -> context.getString(R.string.notification_long_break_completed)
        }
        val message = when (event.nextType) {
            SessionType.FOCUS -> context.getString(R.string.notification_next_focus)
            SessionType.SHORT_BREAK -> context.getString(R.string.notification_next_short_break)
            SessionType.LONG_BREAK -> context.getString(R.string.notification_next_long_break)
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_timer).setContentTitle(title)
            .setContentText(message).setContentIntent(openApp()).setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(sound)
            .setVibrate(if (settings.vibrationEnabled) longArrayOf(0, 180, 100, 180) else longArrayOf(0))
            .build()
        // A retry updates the same session notification without replaying sound/vibration.
        manager.notify(event.sessionId, COMPLETION_ID, notification)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp() = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(action: PomodoroAction, sessionId: String): PendingIntent {
        val intent = Intent(context, PomodoroService::class.java).setAction(action.name)
            .setData("pomolume://timer/$sessionId/${action.name}".toUri())
            .putExtra(EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, action.ordinal + 1, intent, flags)
        else PendingIntent.getService(context, action.ordinal + 1, intent, flags)
    }

    private fun destructiveAction(action: PomodoroAction, sessionId: String): PendingIntent =
        PendingIntent.getActivity(context, 100 + action.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONFIRM_ACTION, action.name)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .setData("pomolume://confirm/$sessionId/${action.name}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ONGOING_ID = 1001
        const val COMPLETION_ID = 1002
        const val ACTIVE_CHANNEL = "pomodoro_active"
        const val EXTRA_CONFIRM_ACTION = "pomodoro_notification_action"
        const val EXTRA_SESSION_ID = "pomodoro_notification_session_id"
    }

    private fun label(type: SessionType) = context.getString(when (type) {
        SessionType.FOCUS -> R.string.notification_focus
        SessionType.SHORT_BREAK -> R.string.notification_short_break
        SessionType.LONG_BREAK -> R.string.notification_long_break
    })
}
