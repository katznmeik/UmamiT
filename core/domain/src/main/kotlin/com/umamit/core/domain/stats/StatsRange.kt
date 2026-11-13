package com.umamit.core.domain.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** The time range selector of INS-07: rolling up to today, one for every tab. */
enum class StatsRange {
    /** „30 T": today and the 29 days before, per ISO week. */
    DAYS_30,

    /** „90 T": today and the 89 days before, per ISO week. */
    DAYS_90,

    /** „12 M": the current month and the 11 before, per month (12 bars, the current month last). */
    MONTHS_12,

    /** „Gesamt": everything up to today, per month from the first finished session's month. */
    ALL,
}

/** The unit of a chart's periods (INS-07). */
enum class PeriodUnit {
    /** An ISO week, Monday first („KW 44"). */
    WEEK,

    /** A calendar month („Okt."). */
    MONTH,
}

/**
 * One bar or line point of the charts (INS-07).
 *
 * @property start the first day the period counts: an edge week starts at the range's first day.
 * @property end the last day it counts (inclusive): the last week or month ends today.
 * @property isoWeek the ISO week number („KW 44") of a [PeriodUnit.WEEK]; null for a month.
 * @property isCurrent whether the period holds today (it is drawn in full `primary`, D-115).
 */
data class StatsPeriod(
    val unit: PeriodUnit,
    val start: LocalDate,
    val end: LocalDate,
    val isoWeek: Int?,
    val isCurrent: Boolean,
) {
    /** The calendar month of a [PeriodUnit.MONTH] (for a week, the month of its first counted day). */
    val month: YearMonth get() = YearMonth.from(start)
}

/** A chart value of one period. */
data class PeriodValue<T>(val period: StatsPeriod, val value: T)

/**
 * A range resolved against today: which days count and the periods of its charts.
 *
 * @property from the first day that counts; null for [StatsRange.ALL], where every day up to today counts.
 * @property to today.
 */
data class StatsWindow(val from: LocalDate?, val to: LocalDate, val periods: List<StatsPeriod>) {
    operator fun contains(day: LocalDate): Boolean = (from == null || day >= from) && day <= to

    /** The index of [day]'s period in [periods], or -1 when no period holds it. */
    fun periodIndex(day: LocalDate): Int {
        val first = periods.firstOrNull()
        val index = when {
            first == null || day > to || day < first.start -> -1
            first.unit == PeriodUnit.WEEK -> ChronoUnit.WEEKS.between(first.start.monday(), day.monday()).toInt()
            else -> ChronoUnit.MONTHS.between(YearMonth.from(first.start), YearMonth.from(day)).toInt()
        }
        return if (index in periods.indices) index else -1
    }

    companion object {
        private const val DAYS_30 = 30L
        private const val DAYS_90 = 90L
        private const val MONTHS_12 = 12L

        /**
         * [range] on [today]. [firstSessionDay] is the day of the first finished session, from whose month
         * [StatsRange.ALL]'s periods run; without a session that range has no periods.
         */
        fun of(range: StatsRange, today: LocalDate, firstSessionDay: LocalDate?): StatsWindow = when (range) {
            StatsRange.DAYS_30 -> weeks(today.minusDays(DAYS_30 - 1), today)

            StatsRange.DAYS_90 -> weeks(today.minusDays(DAYS_90 - 1), today)

            StatsRange.MONTHS_12 -> {
                val from = YearMonth.from(today).minusMonths(MONTHS_12 - 1).atDay(1)
                StatsWindow(from, today, months(from, today))
            }

            StatsRange.ALL -> {
                val periods = firstSessionDay?.takeIf { it <= today }?.let { months(it.withDayOfMonth(1), today) }
                StatsWindow(null, today, periods.orEmpty())
            }
        }

        private fun weeks(from: LocalDate, today: LocalDate): StatsWindow {
            val periods = generateSequence(from.monday()) { it.plusWeeks(1) }
                .takeWhile { it <= today }
                .map { monday ->
                    val start = maxOf(monday, from)
                    val end = minOf(monday.plusDays(6), today)
                    StatsPeriod(
                        unit = PeriodUnit.WEEK,
                        start = start,
                        end = end,
                        isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                        isCurrent = end == today,
                    )
                }
                .toList()
            return StatsWindow(from, today, periods)
        }

        private fun months(from: LocalDate, today: LocalDate): List<StatsPeriod> =
            generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(today) }
                .map { month ->
                    val end = minOf(month.atEndOfMonth(), today)
                    StatsPeriod(PeriodUnit.MONTH, month.atDay(1), end, isoWeek = null, isCurrent = end == today)
                }
                .toList()

        private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
