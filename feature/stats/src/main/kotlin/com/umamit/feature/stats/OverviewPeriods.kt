package com.umamit.feature.stats

import android.content.res.Resources
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.StatsPeriod
import com.umamit.core.model.MonthSpan
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN)
private val YEAR_SHORT = DateTimeFormatter.ofPattern("yy", Locale.GERMAN)

/** „KW 44" for a week (its ISO number), „Okt." for a month. */
internal fun StatsPeriod.label(resources: Resources): String = when (unit) {
    PeriodUnit.WEEK -> resources.getString(R.string.stats_period_week, isoWeek)
    PeriodUnit.MONTH -> MONTH_SHORT.format(start)
}

/** The label of the value tooltip and TalkBack: a month with its year („Okt. 2026"). */
internal fun StatsPeriod.fullLabel(label: String): String = when (unit) {
    PeriodUnit.WEEK -> label
    PeriodUnit.MONTH -> MONTH_YEAR.format(start)
}

/** „Okt. 2026": a month in the pickers of *Monate* and for TalkBack. */
internal fun YearMonth.fullLabel(): String = MONTH_YEAR.format(this)

/**
 * The selector's *Monate* segment once months are chosen (INS-07, R7): „Dez.–Aug.", with the years when they differ
 * („Dez. 25–Aug. 26").
 */
internal fun MonthSpan.label(resources: Resources): String = if (from.year == to.year) {
    resources.getString(R.string.stats_range_span, MONTH_SHORT.format(from), MONTH_SHORT.format(to))
} else {
    resources.getString(
        R.string.stats_range_span_years,
        MONTH_SHORT.format(from),
        YEAR_SHORT.format(from),
        MONTH_SHORT.format(to),
        YEAR_SHORT.format(to),
    )
}
