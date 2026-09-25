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
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.notification.PomodoroNotifications
import com.joseleandro.pomolume.core.notification.deliverCompletion
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicInteger

/** The persisted deadline is the clock; this service only refreshes its presentation. */
class PomodoroService : Service() {
    private val repository: PomodoroRepository by inject()
    private val controller: PomodoroServiceController by inject()
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
                    deliverCompletion(event,
                        readSettings = { settings.observeSettings().first() },
                        notify = notifications::completed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Notification failures never undo a committed history record.
                } finally {
                    pendingNotifications--
                    requestStopWhenIdle()
                }
            }
        }
        // Registration happens before releasing cold-start command callers.
        activeService = this
        ready.complete(Unit)
        scope.launch {
            while (isActive) {
                delay(250)
                if (initialized) commandMutex.withLock {
                    repository.refresh()
                    reconcile()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        stopJob?.cancel()
        scope.launch {
            val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PomoLume:transition")
            wakeLock.acquire(15_000)
            try {
                commandMutex.withLock {
                    val action = intent?.action?.let { raw -> PomodoroAction.entries.find { it.name == raw } }
                    if (action != null) {
                        val expectedSessionId = intent.getStringExtra(PomodoroNotifications.EXTRA_SESSION_ID)
                        if (expectedSessionId != null) controller.dispatchForSession(action, expectedSessionId)
                        // An old notification without identity must not mutate a new session.
                        else if (action == PomodoroAction.CANCEL) controller.dispatch(action)
                    } else repository.refresh()
                    processedStartId = startId
                    initialized = true
                    reconcile()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                commandMutex.withLock {
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
        val active = state.timerState != TimerState.IDLE || state.pendingTransition != null || state.error != null
        val deadline = state.expectedEndAt.takeIf { state.timerState == TimerState.RUNNING }
        if (deadline != scheduledEnd) {
            scheduleDeadline(this, deadline)
            scheduledEnd = deadline
        }
        if (!active && !state.isLoading) {
            requestStopWhenIdle()
            return
        }
        val key = "${state.sessionId}:${state.sessionType}:${state.timerState}:${state.remainingTimeMillis / 1000}:${state.error}"
        if (key != lastNotificationKey) {
            notifications.update(state)
            lastNotificationKey = key
        }
    }

    private fun requestStopWhenIdle() {
        stopJob?.cancel()
        stopJob = scope.launch {
            // Drain the completion collector before removing foreground execution.
            yield()
            commandMutex.withLock {
                val state = repository.state.value
                if (state.timerState == TimerState.IDLE && !state.isLoading &&
                    state.error == null && pendingNotifications == 0 && activeCommands.get() == 0 &&
                    processedStartId == latestStartId) {
                    scheduleDeadline(this@PomodoroService, null)
                    scheduledEnd = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(processedStartId)
                }
            }
        }
    }

    private fun requestReconciliation() {
        scope.launch {
            commandMutex.withLock { reconcile() }
        }
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
            ready = CompletableDeferred()
        }
        scope.cancel()
        // Keep a running deadline alarm so a system-killed service can recover.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RECOVER = "com.joseleandro.pomolume.RECOVER"
        @Volatile private var activeService: PomodoroService? = null
        @Volatile private var ready = CompletableDeferred<Unit>()
        private val activeCommands = AtomicInteger()

        fun beginCommand() { activeCommands.incrementAndGet() }

        fun endCommand() {
            activeCommands.decrementAndGet()
            activeService?.requestReconciliation()
        }

        suspend fun ensureReady(context: Context) {
            val signal = ready
            ContextCompat.startForegroundService(context,
                Intent(context, PomodoroService::class.java).setAction(ACTION_RECOVER))
            withTimeout(10_000) { signal.await() }
        }

        fun stopWhenIdle(context: Context) {
            scheduleDeadline(context, null)
            val service = activeService
            if (service != null) service.requestReconciliation()
            else context.getSystemService(android.app.NotificationManager::class.java)
                .cancel(PomodoroNotifications.ONGOING_ID)
        }

        private fun scheduleDeadline(context: Context, deadline: Long?) {
            val alarm = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, PomodoroService::class.java).setAction(ACTION_RECOVER)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 500, intent, flags)
            else PendingIntent.getService(context, 500, intent, flags)
            alarm.cancel(pending)
            if (deadline == null) return
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                try {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
                    return
                } catch (_: SecurityException) {
                    // Alarm access can be revoked between checking and scheduling.
                }
            }
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        }
    }
}

class AndroidPomodoroServiceController(
    context: Context,
    repository: PomodoroRepository,
    store: TimerStateStore
) : PomodoroServiceController {
    private val delegate = PomodoroCommandCoordinator(repository, store, object : PomodoroRuntime {
        override suspend fun start() {
            try {
                PomodoroService.ensureReady(context)
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException(context.getString(R.string.notification_start_error), timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                throw IllegalStateException(context.getString(R.string.notification_start_error), exception)
            }
        }

        override fun stop() = PomodoroService.stopWhenIdle(context)
        override fun beginCommand() = PomodoroService.beginCommand()
        override fun endCommand() = PomodoroService.endCommand()
    })

    override suspend fun dispatch(action: PomodoroAction) = delegate.dispatch(action)
    override suspend fun dispatchForSession(action: PomodoroAction, sessionId: String) =
        delegate.dispatchForSession(action, sessionId)
    override suspend fun recover() = delegate.recover()
}
