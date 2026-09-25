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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.settings.domain.TimerAppearance

@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    onAction: (PomodoroAction) -> Unit,
    onRetry: () -> Unit,
    notificationAction: String? = null,
    onNotificationActionConsumed: () -> Unit = {},
    notificationSessionId: String? = null,
    timerContent: (@Composable (Dp) -> Unit)? = null
) {
    val timer = state.timer
    val sessionKey = "${timer.sessionId}:${timer.sessionType}"
    var confirmation by rememberSaveable(sessionKey) { mutableStateOf<PomodoroAction?>(null) }
    var confirmedSession by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = !timer.isLoading && !state.isBusy && state.error == null && timer.timerState != TimerState.COMPLETED
    val requestAction: (PomodoroAction) -> Unit = { action ->
        val needsConfirmation = when (action) {
            PomodoroAction.CANCEL -> true
            PomodoroAction.SKIP -> state.settings.confirmSkip
            PomodoroAction.RESET -> state.settings.confirmReset && timer.startedAt != null
            else -> false
        }
        if (needsConfirmation) {
            confirmedSession = sessionKey
            confirmation = action
        } else onAction(action)
    }
    LaunchedEffect(notificationAction, notificationSessionId, timer.isLoading, state.isBusy, state.error) {
        if (notificationAction != null && !timer.isLoading && !state.isBusy && state.error == null) {
            if (notificationSessionId == null || notificationSessionId == timer.sessionId) {
                PomodoroAction.entries.firstOrNull { it.name == notificationAction }?.let(requestAction)
            }
            onNotificationActionConsumed()
        }
    }
    val colors = timerColors(state.settings.timerAppearance)
    val accent = colors.first()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = if (maxHeight < 600.dp) AppSpacing.compact else AppSpacing.page
        val diameter = (maxHeight - if (maxHeight < 600.dp) 300.dp else 350.dp)
            .coerceAtLeast(176.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
            .coerceAtMost(310.dp).coerceAtMost(maxWidth - AppSpacing.spacious)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
        ) {
            SessionIndicator(timer.sessionType, accent)
            if (timerContent != null) timerContent(diameter)
            else PomodoroTimer(timer.remainingTimeMillis, timer.progress, timer.sessionType,
                timer.timerState == TimerState.PAUSED, diameter, state.settings.timerAppearance)
            SessionProgress(timer.currentCycle, timer.completedFocusCount, timer.cyclesUntilLongBreak, accent)
            if (timer.isLoading) {
                CircularProgressIndicator(Modifier.size(AppSpacing.page), strokeWidth = 2.dp)
                Text(stringResource(R.string.pomodoro_loading), style = MaterialTheme.typography.bodyMedium)
            } else {
                PomodoroControls(
                    timerState = timer.timerState, enabled = enabled,
                    canCancel = timer.startedAt != null || timer.completedFocusCount > 0 || timer.sessionType != SessionType.FOCUS,
                    accent = accent, onAction = requestAction
                )
            }
            if (state.error != null) {
                PomoError(stringResource(when (state.error) {
                    "settings" -> R.string.pomodoro_error_settings
                    PomodoroState.ERROR_STORAGE -> R.string.pomodoro_error_storage
                    else -> R.string.pomodoro_error_action
                }), onRetry)
            }
        }
    }
    confirmation?.let { action ->
        val cancel = action == PomodoroAction.CANCEL
        val reset = action == PomodoroAction.RESET
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (cancel) R.string.pomodoro_confirm_cancel_title else if (reset) R.string.pomodoro_confirm_reset_title else R.string.pomodoro_confirm_skip_title)) },
            text = { Text(stringResource(if (cancel) R.string.pomodoro_confirm_cancel_body else if (reset) R.string.pomodoro_confirm_reset_body else R.string.pomodoro_confirm_skip_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (confirmedSession == sessionKey) onAction(action)
                }) { Text(stringResource(if (cancel) R.string.pomodoro_cancel else if (reset) R.string.pomodoro_reset else R.string.pomodoro_skip)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(stringResource(if (cancel) R.string.pomodoro_keep_session else R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun SessionIndicator(type: SessionType, accent: Color = sessionColor(type)) {
    Surface(color = accent.copy(alpha = 0.08f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Text(type.label(), color = accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SessionProgress(cycle: Int, completed: Int, total: Int, accent: Color) {
    val description = pluralStringResource(R.plurals.pomodoro_completed_in_cycle, completed, completed)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text(stringResource(R.string.pomodoro_cycle, cycle, total), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), modifier = Modifier.semantics { contentDescription = description }) {
            repeat(total.coerceIn(1, 10)) { index ->
                Box(Modifier.size(5.dp).background(if (index < completed) accent else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
    }
}

@Composable
fun PomodoroControls(
    timerState: TimerState, enabled: Boolean, canCancel: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary, onAction: (PomodoroAction) -> Unit
) {
    val primary = when (timerState) {
        TimerState.RUNNING -> PomodoroAction.PAUSE
        TimerState.PAUSED -> PomodoroAction.RESUME
        else -> PomodoroAction.START
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PomodoroPrimaryButton(
            stringResource(when (primary) {
                PomodoroAction.PAUSE -> R.string.pomodoro_pause
                PomodoroAction.RESUME -> R.string.pomodoro_resume
                else -> R.string.pomodoro_start
            }),
            if (primary == PomodoroAction.PAUSE) Icons.Default.Pause else Icons.Default.PlayArrow,
            enabled, accent
        ) { onAction(primary) }
        Spacer(Modifier.height(AppSpacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.page, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()) {
            PomodoroSecondaryButton(stringResource(R.string.pomodoro_reset), Icons.Outlined.RestartAlt, "reset_action", enabled) { onAction(PomodoroAction.RESET) }
            PomodoroSecondaryButton(stringResource(R.string.pomodoro_skip), Icons.Outlined.SkipNext, "skip_action", enabled) { onAction(PomodoroAction.SKIP) }
        }
        TextButton(onClick = { onAction(PomodoroAction.CANCEL) }, enabled = enabled && canCancel,
            modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_action"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(stringResource(R.string.pomodoro_cancel), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun sample(type: SessionType = SessionType.FOCUS, timerState: TimerState = TimerState.IDLE) =
    PomodoroUiState(timer = PomodoroState(
        sessionId = if (timerState == TimerState.IDLE) "" else "preview",
        sessionType = type, timerState = timerState, totalDurationMillis = if (type == SessionType.FOCUS) 1_500_000 else 300_000,
        remainingTimeMillis = if (type == SessionType.FOCUS) 1_080_000 else 210_000,
        startedAt = if (timerState == TimerState.IDLE) null else 1L,
        completedFocusCount = 1, isLoading = false
    ))

@AppThemePreview @Composable
private fun PomodoroScreenPreview() = PomoTheme { PomodoroScreen(PomodoroUiState(timer = PomodoroState(isLoading = false)), {}, {}) }
@AppThemePreview @Composable
private fun PomodoroRunningPreview() = PomoTheme { PomodoroScreen(sample(timerState = TimerState.RUNNING), {}, {}) }
@AppThemePreview @Composable
private fun PomodoroPausedPreview() = PomoTheme { PomodoroScreen(sample(timerState = TimerState.PAUSED), {}, {}) }
@AppThemePreview @Composable
private fun ShortBreakPreview() = PomoTheme { PomodoroScreen(sample(SessionType.SHORT_BREAK), {}, {}) }
@AppThemePreview @Composable
private fun LongBreakPreview() = PomoTheme { PomodoroScreen(sample(SessionType.LONG_BREAK), {}, {}) }
@AppThemePreview @Composable
private fun PomodoroControlsPreview() = PomoTheme { PomodoroControls(TimerState.RUNNING, true, true) {} }
@AppThemePreview @Composable
private fun SessionIndicatorPreview() = PomoTheme { SessionIndicator(SessionType.FOCUS) }
@AppThemePreview @Composable
private fun SessionProgressPreview() = PomoTheme { SessionProgress(2, 1, 4, MaterialTheme.colorScheme.primary) }
@Preview(name = "Small phone", widthDp = 320, heightDp = 480, showBackground = true)
@Preview(name = "Large type", widthDp = 360, heightDp = 560, fontScale = 1.8f, showBackground = true)
@Preview(name = "Large type dark", widthDp = 360, heightDp = 560, fontScale = 1.8f, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResponsiveTimerPreview() = PomoTheme { PomodoroScreen(sample(timerState = TimerState.RUNNING), {}, {}) }

@Preview(name = "Dark timer", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun DarkTimerPreview() = PomoTheme(com.joseleandro.pomolume.feature.settings.domain.AppTheme.DARK) {
    PomodoroScreen(sample(timerState = TimerState.PAUSED), {}, {})
}

@Preview(name = "Large font", widthDp = 320, heightDp = 480, fontScale = 2f, showBackground = true)
@Composable
private fun LargeFontTimerPreview() = PomoTheme {
    PomodoroScreen(sample(timerState = TimerState.RUNNING), {}, {})
}
