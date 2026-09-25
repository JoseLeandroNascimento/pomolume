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
                ACTIVE_CHANNEL, "Timer em andamento", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null); enableVibration(false) })
        }
    }

    fun ongoing(state: PomodoroState): Notification {
        val paused = state.timerState == TimerState.PAUSED
        val seconds = (state.remainingTimeMillis.coerceAtLeast(0) + 999) / 1000
        val remaining = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        return NotificationCompat.Builder(context, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(if (paused) "Pomodoro pausado" else state.sessionType.label())
            .setContentText(if (state.isLoading) "Recuperando sessão…" else "$remaining restantes")
            .setContentIntent(openApp())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (paused) "Continuar" else "Pausar",
                action(if (paused) PomodoroAction.RESUME else PomodoroAction.PAUSE))
            .addAction(0, if (paused) "Reiniciar" else "Pular",
                destructiveAction(if (paused) PomodoroAction.RESET else PomodoroAction.SKIP))
            .build()
    }

    fun update(state: PomodoroState) {
        if (canNotify()) manager.notify(ONGOING_ID, ongoing(state))
    }

    fun completed(event: CompletionEvent, settings: PomodoroSettings) {
        if (!settings.notificationsEnabled || !canNotify()) return
        // Channels are immutable after creation. A channel per sound/vibration combination
        // makes both app toggles effective while preserving the user's system overrides.
        val channel = "completion_${settings.soundEnabled}_${settings.vibrationEnabled}"
        val sound = if (settings.soundEnabled) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channel, "Conclusão • ${if (settings.soundEnabled) "som" else "sem som"} • ${if (settings.vibrationEnabled) "vibração" else "sem vibração"}",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(settings.vibrationEnabled)
                if (settings.vibrationEnabled) vibrationPattern = longArrayOf(0, 180, 100, 180)
            })
        }
        val title = when (event.sessionType) {
            SessionType.FOCUS -> "Pomodoro concluído"
            SessionType.SHORT_BREAK -> "Pausa concluída"
            SessionType.LONG_BREAK -> "Pausa longa concluída"
        }
        val message = when (event.nextType) {
            SessionType.FOCUS -> "Hora de voltar ao foco."
            SessionType.SHORT_BREAK -> "Ótimo trabalho. Hora de fazer uma pausa curta."
            SessionType.LONG_BREAK -> "Ótimo trabalho. Aproveite uma pausa longa."
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_timer).setContentTitle(title)
            .setContentText(message).setContentIntent(openApp()).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(sound)
            .setVibrate(if (settings.vibrationEnabled) longArrayOf(0, 180, 100, 180) else longArrayOf(0))
            .build()
        manager.notify(COMPLETION_ID, notification)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp() = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(action: PomodoroAction): PendingIntent {
        val intent = Intent(context, PomodoroService::class.java).setAction(action.name)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, action.ordinal + 1, intent, flags)
        else PendingIntent.getService(context, action.ordinal + 1, intent, flags)
    }

    private fun destructiveAction(action: PomodoroAction): PendingIntent =
        PendingIntent.getActivity(context, 100 + action.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONFIRM_ACTION, action.name)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ONGOING_ID = 1001
        const val COMPLETION_ID = 1002
        const val ACTIVE_CHANNEL = "pomodoro_active"
        const val EXTRA_CONFIRM_ACTION = "pomodoro_notification_action"
    }
}

private fun SessionType.label() = when (this) {
    SessionType.FOCUS -> "Sessão de foco"
    SessionType.SHORT_BREAK -> "Pausa curta"
    SessionType.LONG_BREAK -> "Pausa longa"
}
