package com.umamit.core.domain.stats

import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

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
 * @property isCurrent whether the period holds today (it is drawn in full `primary`, D-115); false in every period
 * of a custom range that ended in an earlier month.
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
 * @property to today, or the last day of a custom range that ends before the current month.
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
        private const val WEEKLY_MAX_MONTHS = 3

        /**
         * [range] on [today]. [firstSessionDay] is the day of the first finished session, from whose month
         * [StatsRange.ALL]'s periods run; without a session that range has no periods. [custom] is the months of
         * [StatsRange.CUSTOM] (without it that range reads as [StatsRange.ALL]).
         */
        fun of(
            range: StatsRange,
            today: LocalDate,
            firstSessionDay: LocalDate?,
            custom: MonthSpan? = null,
        ): StatsWindow = when (range) {
            StatsRange.DAYS_30 -> weeks(today.minusDays(DAYS_30 - 1), today)

            StatsRange.DAYS_90 -> weeks(today.minusDays(DAYS_90 - 1), today)

            StatsRange.MONTHS_12 -> {
                val from = YearMonth.from(today).minusMonths(MONTHS_12 - 1).atDay(1)
                StatsWindow(from, today, months(from, today))
            }

            StatsRange.CUSTOM -> custom?.let { customWindow(it, today) } ?: of(StatsRange.ALL, today, firstSessionDay)

            StatsRange.ALL -> {
                val periods = firstSessionDay?.takeIf { it <= today }?.let { months(it.withDayOfMonth(1), today) }
                StatsWindow(null, today, periods.orEmpty())
            }
        }

        /**
         * The custom months (D-115 Amendment 1, R7): from the first day of *Von* to the last day of *Bis*, or to
         * today when that is the current month (or later). Per week when the span is at most 3 months, else per month.
         */
        private fun customWindow(span: MonthSpan, today: LocalDate): StatsWindow {
            val from = span.from.atDay(1)
            val to = minOf(span.to.atEndOfMonth(), today)
            if (from > to) return StatsWindow(from, to, emptyList())
            return if (span.months <= WEEKLY_MAX_MONTHS) {
                weeks(from, to, today)
            } else {
                StatsWindow(from, to, months(from, to, today))
            }
        }

        private fun weeks(from: LocalDate, today: LocalDate): StatsWindow = weeks(from, today, today)

        private fun weeks(from: LocalDate, to: LocalDate, today: LocalDate): StatsWindow {
            val periods = generateSequence(from.monday()) { it.plusWeeks(1) }
                .takeWhile { it <= to }
                .map { monday ->
                    val start = maxOf(monday, from)
                    val end = minOf(monday.plusDays(6), to)
                    StatsPeriod(
                        unit = PeriodUnit.WEEK,
                        start = start,
                        end = end,
                        isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                        isCurrent = today in start..end,
                    )
                }
                .toList()
            return StatsWindow(from, to, periods)
        }

        private fun months(from: LocalDate, to: LocalDate, today: LocalDate = to): List<StatsPeriod> =
            generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(to) }
                .map { month ->
                    val start = maxOf(month.atDay(1), from)
                    val end = minOf(month.atEndOfMonth(), to)
                    StatsPeriod(PeriodUnit.MONTH, start, end, isoWeek = null, isCurrent = today in start..end)
                }
                .toList()

        private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
