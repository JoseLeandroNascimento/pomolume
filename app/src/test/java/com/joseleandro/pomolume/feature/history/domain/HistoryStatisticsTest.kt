package com.joseleandro.pomolume.feature.history.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HistoryStatisticsTest {
    @Test fun weekUsesMondayThroughSundayAcrossYearBoundary() {
        val range = historyDateRange(HistoryPeriod.WEEK, LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), range.firstDate)
        assertEquals(LocalDate.of(2027, 1, 4), range.endDateExclusive)
    }

    @Test fun sundayBelongsToPreviousMondayAndMondayStartsNewWeek() {
        assertEquals(LocalDate.of(2026, 9, 21),
            historyDateRange(HistoryPeriod.WEEK, LocalDate.of(2026, 9, 27)).firstDate)
        assertEquals(LocalDate.of(2026, 9, 28),
            historyDateRange(HistoryPeriod.WEEK, LocalDate.of(2026, 9, 28)).firstDate)
    }

    @Test fun monthUsesCalendarMonthIncludingLeapDay() {
        val range = historyDateRange(HistoryPeriod.MONTH, LocalDate.of(2024, 2, 20))
        assertEquals(LocalDate.of(2024, 2, 1), range.firstDate)
        assertEquals(LocalDate.of(2024, 3, 1), range.endDateExclusive)
        val stats = calculateHistoryStats(HistoryPeriod.MONTH, LocalDate.of(2024, 2, 20), emptyList())
        assertEquals(29, stats.buckets.size)
        assertEquals(LocalDate.of(2024, 2, 29), stats.buckets.last().date)
        assertEquals(3, stats.leadingEmptyDays)
    }

    @Test fun yearStartsJanuaryFirstAndEndsAtNextYear() {
        val range = historyDateRange(HistoryPeriod.YEAR, LocalDate.of(2026, 9, 24))
        assertEquals(LocalDate.of(2026, 1, 1), range.firstDate)
        assertEquals(LocalDate.of(2027, 1, 1), range.endDateExclusive)
    }

    @Test fun springClockChangeDayContains23Hours() {
        val zone = ZoneId.of("America/New_York")
        val range = historyDateRange(HistoryPeriod.TODAY, LocalDate.of(2026, 3, 8))
        assertEquals(Duration.ofHours(23).toMillis(), range.endMillis(zone)!! - range.startMillis(zone)!!)
    }

    @Test fun autumnClockChangeDayContains25Hours() {
        val zone = ZoneId.of("America/New_York")
        val range = historyDateRange(HistoryPeriod.TODAY, LocalDate.of(2026, 11, 1))
        assertEquals(Duration.ofHours(25).toMillis(), range.endMillis(zone)!! - range.startMillis(zone)!!)
    }

    @Test fun midnightDstGapUsesFirstValidLocalTime() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val date = LocalDate.of(2018, 11, 4)
        val range = historyDateRange(HistoryPeriod.TODAY, date)
        assertEquals(date.atTime(1, 0).atZone(zone).toInstant().toEpochMilli(), range.startMillis(zone))
        assertEquals(Duration.ofHours(23).toMillis(), range.endMillis(zone)!! - range.startMillis(zone)!!)
    }

    @Test fun statsUseSelectedPeriodAndFillMissingWeekDaysWithZero() {
        val today = LocalDate.of(2026, 9, 24)
        val stats = calculateHistoryStats(HistoryPeriod.WEEK, today, listOf(
            DailyFocusStat(LocalDate.of(2026, 9, 20), 9_000, 6),
            DailyFocusStat(LocalDate.of(2026, 9, 21), 1_500, 1),
            DailyFocusStat(LocalDate.of(2026, 9, 24), 3_000, 2),
            DailyFocusStat(LocalDate.of(2026, 9, 28), 9_000, 6)
        ))
        assertEquals(4_500L, stats.focusSeconds)
        assertEquals(3, stats.completedPomodoros)
        assertEquals(2, stats.activeDays)
        assertEquals(7, stats.buckets.size)
        assertEquals(0L, stats.buckets[1].focusSeconds)
        assertEquals(3_000L, stats.maximumBucketSeconds)
    }

    @Test fun yearlyChartGroupsDaysIntoExactly12OrderedMonths() {
        val stats = calculateHistoryStats(HistoryPeriod.YEAR, LocalDate.of(2026, 9, 24), listOf(
            DailyFocusStat(LocalDate.of(2026, 12, 25), 600, 1),
            DailyFocusStat(LocalDate.of(2026, 1, 2), 1_500, 1),
            DailyFocusStat(LocalDate.of(2026, 1, 3), 3_000, 2),
            DailyFocusStat(LocalDate.of(2025, 12, 31), 9_000, 6)
        ))
        assertEquals(12, stats.buckets.size)
        assertEquals(5_100L, stats.focusSeconds)
        assertEquals(4, stats.completedPomodoros)
        assertEquals(3, stats.activeDays)
        assertEquals(4_500L, stats.buckets.first().focusSeconds)
        assertEquals(600L, stats.buckets.last().focusSeconds)
        assertEquals(0L, stats.buckets[1].focusSeconds)
    }

    @Test fun activeDaysAreDistinctAndZeroActivityDoesNotCreateAnActiveDay() {
        val date = LocalDate.of(2026, 9, 24)
        val stats = calculateHistoryStats(HistoryPeriod.MONTH, date, listOf(
            DailyFocusStat(date, 1_500, 1),
            DailyFocusStat(date, 1_500, 1),
            DailyFocusStat(date.minusDays(1), 0, 0)
        ))
        assertEquals(3_000L, stats.focusSeconds)
        assertEquals(2, stats.completedPomodoros)
        assertEquals(1, stats.activeDays)
    }

    @Test fun allPeriodPreservesEntireHistoryWithoutSyntheticChart() {
        val range = historyDateRange(HistoryPeriod.ALL, LocalDate.of(2026, 9, 24))
        assertNull(range.firstDate)
        assertNull(range.endDateExclusive)
        val stats = calculateHistoryStats(HistoryPeriod.ALL, LocalDate.of(2026, 9, 24),
            listOf(DailyFocusStat(LocalDate.of(2020, 1, 1), 1_500, 1)))
        assertEquals(1_500L, stats.focusSeconds)
        assertTrue(stats.buckets.isEmpty())
    }

    @Test fun useCasePassesExactLocalCalendarBoundsToAggregationRepository() = runTest {
        val today = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        var requestedStart: Long? = null
        var requestedEnd: Long? = null
        val repository = object : HistoryStatsRepository {
            override fun observeDailyFocus(startDate: Long?, endDate: Long?) =
                flowOf(listOf(DailyFocusStat(today, 1_500, 1))).also {
                    requestedStart = startDate
                    requestedEnd = endDate
                }
        }
        val result = GetHistoryStatsUseCase(repository)(HistoryPeriod.TODAY, today, zone).first()
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), requestedStart)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), requestedEnd)
        assertEquals(1_500L, result.focusSeconds)
        assertEquals(1, result.activeDays)
    }
}
