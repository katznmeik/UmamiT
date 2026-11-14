package com.umamit.core.model

import java.time.YearMonth
import java.time.temporal.ChronoUnit

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

    /** „Monate" (D-115 Amendment 1, R7): the months from `statsRangeFrom` to `statsRangeTo`, see [MonthSpan]. */
    CUSTOM,
}

/**
 * The custom range's months, both inclusive (05 §6 `statsRangeFrom`, `statsRangeTo`, stored as `yyyy-MM`). [from] is
 * never after [to].
 */
data class MonthSpan(val from: YearMonth, val to: YearMonth) {
    /** The months the span covers, [from] and [to] included. */
    val months: Int get() = ChronoUnit.MONTHS.between(from, to).toInt() + 1

    companion object {
        /** The span of two stored months, the earlier one first; null when either is missing or unreadable. */
        fun of(from: String?, to: String?): MonthSpan? {
            val a = parse(from)
            val b = parse(to)
            return if (a == null || b == null) null else MonthSpan(minOf(a, b), maxOf(a, b))
        }

        /** A stored `yyyy-MM` month; null when [text] is null or not one. */
        fun parse(text: String?): YearMonth? = text?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    }
}

/** The tab of the Statistik screen (INS-07, SCR-18), remembered across app starts (05 §6 `statsTab`). */
enum class StatsTab {
    /** *Überblick*, the default. */
    OVERVIEW,

    /** *Bewertungen*. */
    RATINGS,

    /** *Verbrauch*. */
    CONSUMPTION,
}
