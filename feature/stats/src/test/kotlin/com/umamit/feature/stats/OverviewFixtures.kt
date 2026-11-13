package com.umamit.feature.stats

import com.umamit.core.domain.stats.DayPart
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.PeriodValue
import com.umamit.core.domain.stats.Share
import com.umamit.core.domain.stats.SorteShare
import com.umamit.core.domain.stats.StatsPeriod
import com.umamit.core.domain.stats.Streak
import com.umamit.core.domain.stats.TeaSessions
import com.umamit.core.domain.stats.TeaShare
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Invented *Überblick* results for the tab's tests and goldens (no shop texts, D-016). */
internal object OverviewFixtures {
    private val TODAY: LocalDate = LocalDate.of(2026, 11, 12)

    private fun week(back: Int): StatsPeriod {
        val start = TODAY.minusWeeks(back.toLong()).with(DayOfWeek.MONDAY)
        return StatsPeriod(
            unit = PeriodUnit.WEEK,
            start = start,
            end = start.plusDays(6),
            isoWeek = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            isCurrent = back == 0,
        )
    }

    private fun month(back: Int): StatsPeriod {
        val start = TODAY.minusMonths(back.toLong()).withDayOfMonth(1)
        return StatsPeriod(PeriodUnit.MONTH, start, start.plusMonths(1).minusDays(1), null, back == 0)
    }

    fun weekly(count: Int = 13): List<PeriodValue<Int>> =
        (count - 1 downTo 0).map { PeriodValue(week(it), 2 + (it * 5) % 9) }

    fun monthly(count: Int = 30): List<PeriodValue<Int>> =
        (count - 1 downTo 0).map { PeriodValue(month(it), 3 + (it * 7) % 13) }

    private val DAY_PARTS = listOf(
        Share(DayPart.MORGEN, 18, 43),
        Share(DayPart.MITTAG, 4, 10),
        Share(DayPart.NACHMITTAG, 12, 29),
        Share(DayPart.ABEND, 8, 18),
    )

    private val WEEKDAYS = DayOfWeek.entries.zip(listOf(14, 10, 12, 8, 17, 24, 15)).map { (day, percent) ->
        Share(day, percent, percent)
    }

    fun data(
        periods: List<PeriodValue<Int>> = weekly(),
        teasAdded: Int? = 3,
        streak: Streak = Streak(current = 7, longest = 21),
    ) = OverviewStats(
        sessionCount = 42,
        cupCount = 96,
        teaCount = 24,
        teasAddedInRange = teasAdded,
        sessionsPerPeriod = periods,
        streak = streak,
        mostBrewed = listOf(
            TeaSessions("t1", "Hisui Gyokuro", 12, styleKey = "gyokuro"),
            TeaSessions("t2", "Morgentau Sencha", 9, styleKey = "sencha"),
            TeaSessions(null, "Gelöschter Matcha", 7, styleKey = "matcha"),
            TeaSessions("t4", "Röstduft Hojicha", 5, styleKey = "hojicha"),
            TeaSessions("t5", "Reisaroma Genmaicha", 4, styleKey = "genmaicha"),
        ),
        teaDonut = listOf(
            TeaShare("t1", "Hisui Gyokuro", 12, 29),
            TeaShare("t2", "Morgentau Sencha", 9, 21),
            TeaShare(null, "Gelöschter Matcha", 7, 17),
            TeaShare("t4", "Röstduft Hojicha", 5, 12),
            TeaShare("t5", "Reisaroma Genmaicha", 4, 10),
            TeaShare(null, "", 5, 11, isOther = true),
        ),
        sorten = listOf(
            SorteShare("Sencha", 17, 40),
            SorteShare("Gyokuro", 12, 29),
            SorteShare("Matcha", 7, 17),
            SorteShare("Hojicha", 4, 9),
            SorteShare(null, 2, 5),
        ),
        dayParts = DAY_PARTS,
        weekdays = WEEKDAYS,
    )

    fun emptyRange() = OverviewStats(
        sessionCount = 0,
        cupCount = 0,
        teaCount = 24,
        teasAddedInRange = 0,
        sessionsPerPeriod = weekly().map { PeriodValue(it.period, 0) },
        streak = Streak(0, 21),
        mostBrewed = emptyList(),
        teaDonut = emptyList(),
        sorten = emptyList(),
        dayParts = DayPart.entries.map { Share(it, 0, 0) },
        weekdays = DayOfWeek.entries.map { Share(it, 0, 0) },
    )
}
