package com.umamit.feature.stats

import android.content.res.Resources
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.StatsPeriod
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN)

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
