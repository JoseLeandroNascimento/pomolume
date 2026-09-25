package com.joseleandro.pomolume.feature.pomodoro.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.AppTheme

@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    onAction: (PomodoroAction) -> Unit,
    onRetry: () -> Unit,
    notificationAction: String? = null,
    onNotificationActionConsumed: () -> Unit = {}
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    val timer = state.timer
    val actionEnabled = !timer.isLoading && !state.isBusy && state.error == null
    val requestAction: (PomodoroAction) -> Unit = { action ->
        val confirm = when (action) {
            PomodoroAction.SKIP -> state.settings.confirmSkip
            PomodoroAction.RESET -> state.settings.confirmReset && timer.startedAt != null
            else -> false
        }
        if (confirm) confirmation = action.name else onAction(action)
    }
    LaunchedEffect(notificationAction, timer.isLoading) {
        if (notificationAction != null && !timer.isLoading) {
            PomodoroAction.entries.firstOrNull { it.name == notificationAction }?.let(requestAction)
            onNotificationActionConsumed()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page, vertical = AppSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.page, Alignment.CenterVertically)
        ) {
            Text(timer.sessionType.label(), style = MaterialTheme.typography.titleMedium,
                color = sessionColor(timer.sessionType))
            PomodoroTimer(timer.remainingTimeMillis, timer.progress, timer.sessionType, timer.timerState == TimerState.PAUSED)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
                Text("Ciclo ${timer.currentCycle} de ${timer.cyclesUntilLongBreak}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.semantics {
                    contentDescription = "${timer.completedFocusCount} Pomodoros concluídos neste ciclo"
                }) {
                    repeat(timer.cyclesUntilLongBreak.coerceIn(1, 10)) { index ->
                        Box(Modifier.size(AppSpacing.small).background(
                            if (index < timer.completedFocusCount) sessionColor(timer.sessionType) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ))
                    }
                }
            }
            if (timer.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(AppSpacing.page), strokeWidth = androidx.compose.ui.unit.Dp(2f))
                Text("Recuperando seu timer…", style = MaterialTheme.typography.bodyMedium)
            } else {
                val primaryAction = when (timer.timerState) {
                    TimerState.RUNNING -> PomodoroAction.PAUSE
                    TimerState.PAUSED -> PomodoroAction.RESUME
                    else -> PomodoroAction.START
                }
                PomodoroPrimaryButton(
                    label = when (timer.timerState) {
                        TimerState.RUNNING -> "Pausar"
                        TimerState.PAUSED -> "Continuar"
                        else -> "Iniciar"
                    },
                    icon = if (timer.timerState == TimerState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    enabled = actionEnabled,
                    onClick = { onAction(primaryAction) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    PomodoroSecondaryButton("Reiniciar", Icons.Outlined.RestartAlt, "reset_action", actionEnabled && timer.startedAt != null) {
                        requestAction(PomodoroAction.RESET)
                    }
                    PomodoroSecondaryButton("Pular", Icons.Outlined.SkipNext, "skip_action", actionEnabled) {
                        requestAction(PomodoroAction.SKIP)
                    }
                }
            }
            state.error?.let { PomoError(it, onRetry) }
        }
    }
    confirmation?.let { pending ->
        val reset = pending == PomodoroAction.RESET.name
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (reset) "Reiniciar esta sessão?" else "Pular esta sessão?") },
            text = { Text(if (reset) "O progresso atual será encerrado e o timer voltará à duração original." else "A sessão atual não será contabilizada como concluída.") },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                onAction(if (reset) PomodoroAction.RESET else PomodoroAction.SKIP)
            }) { Text(if (reset) "Reiniciar" else "Pular") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PomodoroScreenPreview() = PomoTheme {
    PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), {}, {})
}

@Preview(showBackground = true)
@Composable
private fun PomodoroRunningPreview() = PomoTheme {
    PomodoroScreen(PomodoroUiState(timer = PomodoroState(timerState = TimerState.RUNNING, remainingTimeMillis = 1_137_000, startedAt = 1, isLoading = false)), {}, {})
}

@Preview(showBackground = true)
@Composable
private fun PomodoroPausedPreview() = PomoTheme {
    PomodoroScreen(PomodoroUiState(timer = PomodoroState(timerState = TimerState.PAUSED, remainingTimeMillis = 900_000, startedAt = 1, isLoading = false)), {}, {})
}

@Preview(showBackground = true)
@Composable
private fun DarkThemePreview() = PomoTheme(AppTheme.DARK) {
    Surface { PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), {}, {}) }
}
