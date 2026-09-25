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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.history.domain.*
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(state: HistoryUiState, onFilter: (HistoryFilter) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("history_list"),
        contentPadding = PaddingValues(bottom = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        item(key = "periods") { HistoryPeriodSelector(state.filter, onFilter) }
        if (state.isLoading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(AppSpacing.spacious), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.error != null) {
            item(key = "error") { PomoError(stringResource(R.string.history_load_error), onRetry, Modifier.fillMaxWidth()) }
        } else {
            item(key = "summary") { HistorySummary(state.stats, state.periodLabel) }
            if (state.filter in listOf(HistoryFilter.WEEK, HistoryFilter.MONTH, HistoryFilter.YEAR)) {
                item(key = "chart") {
                    HistoryPeriodChart(state.filter, state.stats,
                        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact))
                }
            }
            if (state.sessions.isEmpty()) {
                item(key = "empty") { HistoryEmptyState(state.filter != HistoryFilter.ALL) { onFilter(HistoryFilter.ALL) } }
            } else {
                item(key = "sessions_title") {
                    Text(stringResource(R.string.history_sessions_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium).semantics { heading() })
                }
                state.groups.forEach { group ->
                    item(key = "date_${group.date}") {
                        val title = when (group.date) {
                            state.today -> stringResource(R.string.history_period_today)
                            state.today.minusDays(1) -> stringResource(R.string.history_yesterday)
                            else -> group.label
                        }
                        Text(title, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.small).semantics { heading() })
                    }
                    items(group.rows, key = { "session_${it.session.id}" }) { row -> PomodoroHistoryItem(row) }
                }
            }
        }
    }
}

@Composable
fun HistoryPeriodSelector(selected: HistoryFilter, onFilter: (HistoryFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(selected = selected == filter, onClick = { onFilter(filter) },
                label = { Text(stringResource(filter.titleRes)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("filter_${filter.name}"))
        }
    }
}

@Composable
fun HistorySummary(stats: HistoryStats, periodLabel: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text(stringResource(R.string.history_your_progress), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (periodLabel.isNotEmpty()) Text(periodLabel, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(stats.focusSeconds), style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.history_focus_time), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.small))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.page)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
                Text(stats.completedPomodoros.toString(), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clearAndSetSemantics {})
                Text(pluralStringResource(R.plurals.history_completed_count, stats.completedPomodoros, stats.completedPomodoros),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
                Text(stats.activeDays.toString(), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clearAndSetSemantics {})
                Text(pluralStringResource(R.plurals.history_active_days, stats.activeDays, stats.activeDays),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HistoryEmptyState(showAll: Boolean, onShowAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.spacious),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.history_no_sessions), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.history_empty_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (showAll) TextButton(onClick = onShowAll) { Text(stringResource(R.string.history_show_all)) }
    }
}

@Composable
fun PomodoroHistoryItem(row: HistorySessionRow) {
    val session = row.session
    val icon = when (session.type) {
        SessionType.FOCUS -> Icons.Outlined.Timer
        SessionType.SHORT_BREAK -> Icons.Outlined.FreeBreakfast
        SessionType.LONG_BREAK -> Icons.Outlined.NightsStay
    }
    val status = stringResource(when (session.status) {
        SessionStatus.COMPLETED -> R.string.history_completed
        SessionStatus.CANCELLED -> R.string.history_cancelled
        SessionStatus.SKIPPED -> R.string.history_skipped
    })
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
        .padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Box(Modifier.size(44.dp).background(sessionColor(session.type).copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = sessionColor(session.type), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
            Text(session.type.label(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_item_detail, formatDuration(session.actualDurationSeconds), status),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(row.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun previewHistoryState(filter: HistoryFilter = HistoryFilter.WEEK): HistoryUiState {
    val today = LocalDate.of(2026, 9, 24)
    val start = today.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val sessions = listOf(
        PomodoroSession("preview-one", SessionType.FOCUS, SessionStatus.COMPLETED, start, start + 1_500_000, 1500, 1500),
        PomodoroSession("preview-two", SessionType.SHORT_BREAK, SessionStatus.COMPLETED, start + 1_500_000, start + 1_800_000, 300, 300)
    )
    return HistoryUiState(filter = filter, sessions = sessions, today = today, isLoading = false,
        stats = previewStats(filter.period))
}

@AppThemePreview
@Composable
private fun HistoryPreview() = PomoTheme { HistoryScreen(previewHistoryState(), {}, {}) }

@AppThemePreview
@Composable
private fun HistoryTodayPreview() = PomoTheme { HistoryScreen(previewHistoryState(HistoryFilter.TODAY), {}, {}) }

@AppThemePreview
@Composable
private fun HistoryMonthPreview() = PomoTheme { HistoryScreen(previewHistoryState(HistoryFilter.MONTH), {}, {}) }

@AppThemePreview
@Composable
private fun HistoryYearPreview() = PomoTheme { HistoryScreen(previewHistoryState(HistoryFilter.YEAR), {}, {}) }

@AppThemePreview
@Composable
private fun HistoryPeriodSelectorPreview() = PomoTheme { HistoryPeriodSelector(HistoryFilter.MONTH) {} }

@AppThemePreview
@Composable
private fun HistoryEmptyPreview() = PomoTheme { HistoryScreen(HistoryUiState(isLoading = false), {}, {}) }

@AppThemePreview
@Composable
private fun HistorySummaryPreview() = PomoTheme { HistorySummary(HistoryStats(7_500, 5, 3), "") }

@AppThemePreview
@Composable
private fun HistoryItemPreview() = PomoTheme {
    PomodoroHistoryItem(previewHistoryState().groups.first().rows.first())
}
