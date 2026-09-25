package com.joseleandro.pomolume.feature.history.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.history.domain.*
import java.time.LocalDate

@Composable
fun HistoryPeriodChart(filter: HistoryFilter, stats: HistoryStats, modifier: Modifier = Modifier) {
    val title = when (filter) {
        HistoryFilter.WEEK -> R.string.history_week_chart_title
        HistoryFilter.MONTH -> R.string.history_month_chart_title
        HistoryFilter.YEAR -> R.string.history_year_chart_title
        else -> return
    }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.history_chart_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (filter == HistoryFilter.MONTH) HistoryMonthHeatmap(stats)
            else {
                val zero = stringResource(R.string.duration_minutes, 0)
                Text(stringResource(R.string.history_chart_scale, zero,
                    if (stats.maximumBucketSeconds == 0L) zero else formatDuration(stats.maximumBucketSeconds)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HistoryBarChart(stats, yearly = filter == HistoryFilter.YEAR)
            }
        }
    }
}

/** Buckets arrive sorted and zero-filled; Canvas only draws their prepared values. */
@Composable
fun HistoryBarChart(stats: HistoryStats, yearly: Boolean, modifier: Modifier = Modifier) {
    val weekdays = stringArrayResource(R.array.history_weekdays)
    val months = stringArrayResource(R.array.history_months)
    val weekdayNames = stringArrayResource(R.array.history_weekday_names)
    val monthNames = stringArrayResource(R.array.history_month_names)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val gap = if (yearly) 4.dp else AppSpacing.small
    val minimumWidth = (if (yearly) 20.dp else 28.dp) * fontScale * stats.buckets.size +
        gap * (stats.buckets.size - 1).coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().testTag(if (yearly) "history_year_chart" else "history_week_chart")) {
      val chartWidth = maxOf(maxWidth, minimumWidth)
      Row(Modifier.horizontalScroll(rememberScrollState())) {
       Row(Modifier.width(chartWidth),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom) {
        stats.buckets.forEach { bucket ->
            val label = if (yearly) months[bucket.date.monthValue - 1] else weekdays[bucket.date.dayOfWeek.value - 1]
            val spokenLabel = if (yearly) monthNames[bucket.date.monthValue - 1] else weekdayNames[bucket.date.dayOfWeek.value - 1]
            val description = stringResource(R.string.history_chart_point, spokenLabel, formatDuration(bucket.focusSeconds))
            val fraction = if (stats.maximumBucketSeconds <= 0) 0f
                else (bucket.focusSeconds.toFloat() / stats.maximumBucketSeconds).coerceIn(0f, 1f)
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val width = size.width * if (yearly) 0.72f else 0.62f
                    val left = (size.width - width) / 2
                    val baseline = 3.dp.toPx()
                    drawRoundRect(track, Offset(left, size.height - baseline), Size(width, baseline), CornerRadius(baseline))
                    if (fraction > 0f) {
                        val height = ((size.height - 8.dp.toPx()) * fraction).coerceAtLeast(baseline)
                        drawRoundRect(primary.copy(alpha = 0.88f), Offset(left, size.height - height),
                            Size(width, height), CornerRadius(6.dp.toPx()))
                    }
                }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
       }
      }
    }
}

@Composable
fun HistoryMonthHeatmap(stats: HistoryStats, modifier: Modifier = Modifier) {
    val weekdays = stringArrayResource(R.array.history_weekdays)
    val months = stringArrayResource(R.array.history_month_names)
    val rowCount = (stats.leadingEmptyDays + stats.buckets.size + 6) / 7
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val minimumWidth = 32.dp * fontScale * 7 + AppSpacing.small * 6
    BoxWithConstraints(modifier.fillMaxWidth().testTag("history_month_chart")) {
      val gridWidth = maxOf(maxWidth, minimumWidth)
      Row(Modifier.horizontalScroll(rememberScrollState())) {
       Column(Modifier.width(gridWidth), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            weekdays.forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        repeat(rowCount) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                repeat(7) { column ->
                    val index = row * 7 + column - stats.leadingEmptyDays
                    val bucket = stats.buckets.getOrNull(index)
                    if (bucket == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else {
                        val fraction = if (stats.maximumBucketSeconds <= 0) 0f
                            else (bucket.focusSeconds.toFloat() / stats.maximumBucketSeconds).coerceIn(0f, 1f)
                        val date = stringResource(R.string.history_month_day, bucket.date.dayOfMonth, months[bucket.date.monthValue - 1])
                        val description = stringResource(R.string.history_chart_point, date, formatDuration(bucket.focusSeconds))
                        val fill = if (fraction == 0f) MaterialTheme.colorScheme.surfaceContainerHighest
                            else lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary,
                                0.25f + 0.75f * fraction)
                        val foreground = if (fill.luminance() > 0.179f) androidx.compose.ui.graphics.Color.Black
                            else androidx.compose.ui.graphics.Color.White
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .background(fill, RoundedCornerShape(8.dp))
                            .clearAndSetSemantics { contentDescription = description },
                            contentAlignment = Alignment.Center) {
                            Text(bucket.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                color = foreground)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = AppSpacing.tiny),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_heatmap_less), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AppSpacing.small))
            repeat(4) { index ->
                Box(Modifier.padding(horizontal = 2.dp).size(10.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * (index + 1)), RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.width(AppSpacing.small))
            Text(stringResource(R.string.history_heatmap_more), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
       }
      }
    }
}

internal fun previewStats(period: HistoryPeriod): HistoryStats {
    val date = LocalDate.of(2026, 9, 24)
    return calculateHistoryStats(period, date, (0L..23L).map { offset ->
        val day = date.withDayOfMonth(1).plusDays(offset)
        DailyFocusStat(day, if (offset % 4L == 0L) 0 else (offset % 5 + 1) * 1_500, if (offset % 4L == 0L) 0 else (offset % 5 + 1).toInt())
    })
}

@AppThemePreview
@Composable
private fun HistoryWeekChartPreview() = PomoTheme {
    HistoryPeriodChart(HistoryFilter.WEEK, previewStats(HistoryPeriod.WEEK), Modifier.padding(AppSpacing.page))
}

@AppThemePreview
@Composable
private fun HistoryMonthChartPreview() = PomoTheme {
    HistoryPeriodChart(HistoryFilter.MONTH, previewStats(HistoryPeriod.MONTH), Modifier.padding(AppSpacing.page))
}

@AppThemePreview
@Composable
private fun HistoryYearChartPreview() = PomoTheme {
    val daily = (1..12).map { DailyFocusStat(LocalDate.of(2026, it, 10), it * 3_000L, it * 2) }
    HistoryPeriodChart(HistoryFilter.YEAR, calculateHistoryStats(HistoryPeriod.YEAR, LocalDate.of(2026, 9, 24), daily),
        Modifier.padding(AppSpacing.page))
}
