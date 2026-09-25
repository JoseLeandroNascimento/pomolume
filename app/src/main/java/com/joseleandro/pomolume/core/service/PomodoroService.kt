package com.joseleandro.pomolume.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.joseleandro.pomolume.core.notification.PomodoroNotifications
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject

/** The persisted deadline is the clock; this service only refreshes its presentation. */
class PomodoroService : Service() {
    private val repository: PomodoroRepository by inject()
    private val settings: SettingsRepository by inject()
    private val notifications: PomodoroNotifications by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var initialized = false
    private var scheduledEnd: Long? = null
    private var lastNotificationKey: String? = null
    private var latestStartId = 0
    private var processedStartId = 0
    private var pendingNotifications = 0
    private var stopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(this, PomodoroNotifications.ONGOING_ID,
            notifications.ongoing(repository.state.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.completions.collect { event ->
                pendingNotifications++
                try {
                    notifications.completed(event, settings.observeSettings().first())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A notification/storage failure must never undo a committed history row.
                } finally {
                    pendingNotifications--
                    requestStopWhenIdle()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(500)
                if (initialized) commandMutex.withLock {
                    repository.refresh()
                    reconcile()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        scope.launch {
            val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PomoLume:transition")
            wakeLock.acquire(15_000)
            try {
                commandMutex.withLock {
                    val action = intent?.action?.let { raw -> PomodoroAction.entries.find { it.name == raw } }
                    if (action != null) repository.execute(action) else repository.refresh()
                    processedStartId = startId
                    initialized = true
                    reconcile()
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
        return START_STICKY
    }

    private fun reconcile() {
        val state = repository.state.value
        val active = state.timerState == TimerState.RUNNING || state.timerState == TimerState.PAUSED ||
            state.timerState == TimerState.COMPLETED || state.error != null
        val deadline = state.expectedEndAt.takeIf { state.timerState == TimerState.RUNNING }
        if (deadline != scheduledEnd) {
            scheduleDeadline(deadline)
            scheduledEnd = deadline
        }
        if (!active && !state.isLoading) {
            requestStopWhenIdle()
            return
        }
        val key = "${state.sessionType}:${state.timerState}:${state.remainingTimeMillis / 1000}:${state.error}"
        if (key != lastNotificationKey) {
            notifications.update(state)
            lastNotificationKey = key
        }
    }

    private fun requestStopWhenIdle() {
        stopJob?.cancel()
        stopJob = scope.launch {
            // Drain the completion collector resumed by the engine before stopping.
            yield()
            commandMutex.withLock {
                val state = repository.state.value
                if (state.timerState == TimerState.IDLE && !state.isLoading &&
                    state.error == null && pendingNotifications == 0 && processedStartId == latestStartId) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(processedStartId)
                }
            }
        }
    }

    private fun scheduleDeadline(deadline: Long?) {
        val alarm = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, PomodoroService::class.java).setAction(ACTION_RECOVER)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(this, 500, intent, flags)
        else PendingIntent.getService(this, 500, intent, flags)
        alarm.cancel(pending)
        if (deadline == null) return
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
                return
            } catch (_: SecurityException) {
                // The user can revoke alarm access between checking and scheduling.
            }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
    }

    override fun onDestroy() {
        scope.cancel()
        // Keep a running deadline alarm: it can recover a system-killed service.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val ACTION_RECOVER = "com.joseleandro.pomolume.RECOVER" }
}

class AndroidPomodoroServiceController(
    private val context: Context,
    private val repository: PomodoroRepository,
    private val store: TimerStateStore
) : PomodoroServiceController {
    override suspend fun dispatch(action: PomodoroAction) {
        startService(action.name)
    }

    override suspend fun recover() {
        val saved = store.read()
        if (saved != null && saved.timerState != TimerState.IDLE) startService(PomodoroService.ACTION_RECOVER)
        else repository.restore()
    }

    private fun startService(action: String) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PomodoroService::class.java).setAction(action))
        } catch (exception: Exception) {
            throw IllegalStateException("Não foi possível iniciar o timer. Abra o aplicativo e tente novamente.", exception)
        }
    }
}
