package com.joseleandro.pomolume.feature.history.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

enum class HistoryPeriod { TODAY, WEEK, MONTH, YEAR, ALL }

/** Half-open local calendar interval; never approximate a day with 24 hours. */
data class HistoryDateRange(val firstDate: LocalDate?, val endDateExclusive: LocalDate?) {
    fun startMillis(zone: ZoneId): Long? = firstDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    fun endMillis(zone: ZoneId): Long? = endDateExclusive?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
}

fun historyDateRange(period: HistoryPeriod, today: LocalDate): HistoryDateRange = when (period) {
    HistoryPeriod.TODAY -> HistoryDateRange(today, today.plusDays(1))
    HistoryPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let {
        HistoryDateRange(it, it.plusWeeks(1))
    }
    HistoryPeriod.MONTH -> today.withDayOfMonth(1).let { HistoryDateRange(it, it.plusMonths(1)) }
    HistoryPeriod.YEAR -> today.withDayOfYear(1).let { HistoryDateRange(it, it.plusYears(1)) }
    HistoryPeriod.ALL -> HistoryDateRange(null, null)
}

data class DailyFocusStat(val date: LocalDate, val focusSeconds: Long, val completedPomodoros: Int)
data class FocusChartBucket(val date: LocalDate, val focusSeconds: Long, val completedPomodoros: Int)
data class HistoryStats(
    val focusSeconds: Long = 0,
    val completedPomodoros: Int = 0,
    val activeDays: Int = 0,
    val buckets: List<FocusChartBucket> = emptyList(),
    val maximumBucketSeconds: Long = buckets.maxOfOrNull { it.focusSeconds } ?: 0,
    val leadingEmptyDays: Int = buckets.firstOrNull()?.date?.dayOfWeek?.value?.minus(1) ?: 0
)

interface HistoryStatsRepository {
    /** SQL aggregates completed focus only, with one compact result per local day. */
    fun observeDailyFocus(startDate: Long?, endDate: Long?): Flow<List<DailyFocusStat>>
}

fun calculateHistoryStats(
    period: HistoryPeriod,
    today: LocalDate,
    daily: List<DailyFocusStat>
): HistoryStats {
    val range = historyDateRange(period, today)
    val filtered = daily.filter {
        (range.firstDate == null || !it.date.isBefore(range.firstDate)) &&
            (range.endDateExclusive == null || it.date.isBefore(range.endDateExclusive))
    }
    val grouped = filtered.groupBy {
        if (period == HistoryPeriod.YEAR) it.date.withDayOfMonth(1) else it.date
    }
    val dates = when (period) {
        HistoryPeriod.ALL -> emptyList()
        HistoryPeriod.YEAR -> (0L..11L).map { today.withDayOfYear(1).plusMonths(it) }
        else -> generateSequence(requireNotNull(range.firstDate)) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endDateExclusive) }.toList()
    }
    return HistoryStats(
        focusSeconds = filtered.sumOf { it.focusSeconds.coerceAtLeast(0) },
        completedPomodoros = filtered.sumOf { it.completedPomodoros.coerceAtLeast(0) },
        activeDays = filtered.filter { it.completedPomodoros > 0 }.map { it.date }.distinct().size,
        buckets = dates.map { date ->
            val items = grouped[date].orEmpty()
            FocusChartBucket(date, items.sumOf { it.focusSeconds.coerceAtLeast(0) },
                items.sumOf { it.completedPomodoros.coerceAtLeast(0) })
        }
    )
}

class GetHistoryStatsUseCase(private val repository: HistoryStatsRepository) {
    operator fun invoke(period: HistoryPeriod, today: LocalDate, zone: ZoneId): Flow<HistoryStats> {
        val range = historyDateRange(period, today)
        return repository.observeDailyFocus(range.startMillis(zone), range.endMillis(zone))
            .map { calculateHistoryStats(period, today, it) }
            .flowOn(Dispatchers.Default)
    }
}
