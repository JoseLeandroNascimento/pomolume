package com.joseleandro.pomolume.feature.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.history.domain.*
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(state: HistoryUiState, onFilter: (HistoryFilter) -> Unit, onRetry: () -> Unit) {
    val groups = remember(state.sessions) {
        state.sessions.groupBy { Instant.ofEpochMilli(it.endedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("history_list"), contentPadding = PaddingValues(bottom = AppSpacing.page)) {
        item {
            Column(Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
                Text("Seu foco hoje", style = MaterialTheme.typography.titleMedium)
                Text(if (state.todaySummary.focusSeconds == 0L) "0 min" else formatDuration(state.todaySummary.focusSeconds),
                    style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("${state.todaySummary.completedPomodoros} ${if (state.todaySummary.completedPomodoros == 1) "Pomodoro concluído" else "Pomodoros concluídos"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) },
                        label = { Text(filter.label) }, modifier = Modifier.testTag("filter_${filter.name}"))
                }
            }
        }
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(AppSpacing.spacious), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error != null -> item { PomoError(state.error, onRetry, Modifier.fillMaxWidth()) }
            state.sessions.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.spacious),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Nenhuma sessão ainda", style = MaterialTheme.typography.titleMedium)
                    Text("Conclua seu primeiro Pomodoro para começar seu histórico.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (state.filter != HistoryFilter.ALL) {
                        TextButton(onClick = { onFilter(HistoryFilter.ALL) }) { Text("Ver todo o histórico") }
                    }
                }
            }
            else -> groups.forEach { (date, sessions) ->
                item(key = "date_$date") {
                    Text(dateLabel(date, state.today), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium))
                }
                items(sessions, key = { it.id }) { session -> PomodoroHistoryItem(session) }
            }
        }
    }
}

@Composable
fun PomodoroHistoryItem(session: PomodoroSession) {
    val time = remember(session.startedAt) {
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val icon = when (session.type) {
        SessionType.FOCUS -> Icons.Outlined.Timer
        SessionType.SHORT_BREAK -> Icons.Outlined.FreeBreakfast
        SessionType.LONG_BREAK -> Icons.Outlined.NightsStay
    }
    val status = when (session.status) {
        SessionStatus.COMPLETED -> "Concluído"
        SessionStatus.CANCELLED -> "Cancelado"
        SessionStatus.SKIPPED -> "Pulado"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Box(Modifier.size(44.dp).background(sessionColor(session.type).copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = sessionColor(session.type), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
            Text(session.type.label(), style = MaterialTheme.typography.titleMedium)
            Text("${formatDuration(session.actualDurationSeconds)} · $status",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.minusDays(1) -> "Ontem"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d 'de' MMMM" else "d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")))
}

@Preview(showBackground = true)
@Composable
private fun HistoryEmptyPreview() = PomoTheme { HistoryScreen(HistoryUiState(isLoading = false), {}, {}) }

@Preview(showBackground = true)
@Composable
private fun HistoryPreview() = PomoTheme {
    HistoryScreen(HistoryUiState(isLoading = false,
        todaySummary = FocusSummary(1500, 1),
        sessions = listOf(PomodoroSession("preview", SessionType.FOCUS, SessionStatus.COMPLETED, 0, 1_500_000, 1500, 1500))
    ), {}, {})
}
