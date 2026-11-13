package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** D-115 Amendment 1: the custom range (INS-07), the tea donut, GETRUNKEN, the Tee-Rangliste and Neu entdeckt. */
class StatsAmendmentTest {
    private fun span(from: String, to: String) = MonthSpan.of(from, to)!!

    @Test
    fun `month span reads yyyy-MM, swaps a reversed pair and is null when unreadable`() {
        assertThat(
            MonthSpan.of("2026-09", "2026-11"),
        ).isEqualTo(MonthSpan(YearMonth.of(2026, 9), YearMonth.of(2026, 11)))
        assertThat(MonthSpan.of("2026-11", "2026-09")!!.from).isEqualTo(YearMonth.of(2026, 9))
        assertThat(MonthSpan.of("2026-09", null)).isNull()
        assertThat(MonthSpan.of("bad", "2026-09")).isNull()
        assertThat(span("2026-09", "2026-11").months).isEqualTo(3)
    }

    @Test
    fun `a custom span up to 3 months is per week and ending this month counts up to today`() {
        val window = StatsWindow.of(StatsRange.CUSTOM, TODAY, null, span("2026-09", "2026-11"))

        assertThat(window.from).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(window.to).isEqualTo(TODAY)
        assertThat(window.periods.all { it.unit == PeriodUnit.WEEK }).isTrue()
        assertThat(window.periods.first().start).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(window.periods.last().end).isEqualTo(TODAY)
        assertThat(window.periods.count { it.isCurrent }).isEqualTo(1)
    }

    @Test
    fun `a longer custom span is per month and ends with its last day when it is over`() {
        val window = StatsWindow.of(StatsRange.CUSTOM, TODAY, null, span("2026-01", "2026-05"))

        assertThat(window.to).isEqualTo(LocalDate.of(2026, 5, 31))
        assertThat(window.periods).hasSize(5)
        assertThat(window.periods.all { it.unit == PeriodUnit.MONTH }).isTrue()
        assertThat(window.periods.none { it.isCurrent }).isTrue()
        assertThat(LocalDate.of(2026, 6, 1) in window).isFalse()
    }

    @Test
    fun `a single past month is per week`() {
        val window = StatsWindow.of(StatsRange.CUSTOM, TODAY, null, span("2026-10", "2026-10"))

        assertThat(window.periods.all { it.unit == PeriodUnit.WEEK }).isTrue()
        assertThat(window.to).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun `the calculator counts only the custom months`() {
        val calc = calculator(
            listOf(session(LocalDate.of(2026, 9, 10)), session(LocalDate.of(2026, 10, 5)), session(TODAY)),
        )

        assertThat(calc.overview(StatsRange.CUSTOM, span("2026-09", "2026-10")).sessionCount).isEqualTo(2)
    }

    @Test
    fun `tea donut - top 5 with Andere, deleted tea by snapshot`() {
        val sessions = (1..7).flatMap { n -> List(8 - n) { session(teaId = "t$n", teaName = "Tee $n") } } +
            List(2) { session(teaId = null, teaName = "Gelöscht") }
        val donut = calculator(sessions, (1..7).map { tea("t$it", "Tee $it") }).overview(StatsRange.ALL).teaDonut

        assertThat(donut.map { it.teaName }).containsExactly("Tee 1", "Tee 2", "Tee 3", "Tee 4", "Tee 5", "").inOrder()
        assertThat(donut.last().isOther).isTrue()
        assertThat(donut.last().sessions).isEqualTo(5)
        assertThat(donut.sumOf { it.percent }).isEqualTo(100)

        val small = calculator(listOf(session(teaId = null, teaName = "Gelöscht"))).overview(StatsRange.ALL).teaDonut
        assertThat(small.single().teaName).isEqualTo("Gelöscht")
        assertThat(small.single().teaId).isNull()
    }

    @Test
    fun `teas added is null under Gesamt`() {
        val calc = calculator(listOf(session()), listOf(tea("t1", created = TODAY.minusDays(2))))

        assertThat(calc.overview(StatsRange.ALL).teasAddedInRange).isNull()
        assertThat(calc.overview(StatsRange.DAYS_30).teasAddedInRange).isEqualTo(1)
    }

    @Test
    fun `GETRUNKEN - sum of session costs, sessions with a price and cost per cup`() {
        val calc = calculator(
            listOf(
                session(costCents = 300),
                session(costCents = 200),
                session(),
                session(TODAY.minusDays(100), costCents = 999),
            ),
        )
        val stats = calc.consumption(StatsRange.DAYS_30)

        assertThat(stats.drunkCents).isEqualTo(500)
        assertThat(stats.drunkSessionCount).isEqualTo(2)
        assertThat(stats.drunkPerPeriod.sumOf { it.value }).isEqualTo(500)
        assertThat(stats.costPerCup).isNotNull()
        val none = calculator(listOf(session())).consumption(StatsRange.DAYS_30)
        assertThat(none.drunkSessionCount).isEqualTo(0)
        assertThat(none.costPerCup).isNull()
    }

    @Test
    fun `Tee-Rangliste - every rated tea, one decimal, ties by more ratings then name`() {
        val sessions = listOf(
            session(teaId = "a", teaName = "Zeta", rating = 4.0),
            session(teaId = "b", teaName = "Alpha", rating = 4.0),
            session(teaId = "c", teaName = "Mu", rating = 4.0),
            session(teaId = "c", teaName = "Mu", rating = 4.0),
            session(teaId = "d", teaName = "Eins", rating = 4.5),
            session(teaId = "d", teaName = "Eins", rating = 4.0),
            session(teaId = "d", teaName = "Eins", rating = 4.0),
            session(teaId = "e", teaName = "Ungewertet"),
        )
        val ranking = calculator(sessions).ratings(StatsRange.ALL).teaRanking

        assertThat(ranking.map { it.teaName }).containsExactly("Eins", "Mu", "Alpha", "Zeta").inOrder()
        assertThat(ranking.first().average).isEqualTo(4.2)
    }

    @Test
    fun `Neu entdeckt - first session ever in range, newest first, empty under Gesamt`() {
        val sessions = listOf(
            session(TODAY.minusDays(40), teaId = "old", teaName = "Alt"),
            session(TODAY.minusDays(5), teaId = "old", teaName = "Alt"),
            session(TODAY.minusDays(10), teaId = "n1", teaName = "Neu eins", rating = 4.5),
            session(TODAY.minusDays(3), teaId = "n2", teaName = "Neu zwei"),
        )
        val calc = calculator(sessions)
        val found = calc.ratings(StatsRange.DAYS_30).newlyDiscovered

        assertThat(found.map { it.teaName }).containsExactly("Neu zwei", "Neu eins").inOrder()
        assertThat(found[0].average).isNull()
        assertThat(found[1].average).isEqualTo(4.5)
        assertThat(found[1].firstDay).isEqualTo(TODAY.minusDays(10))
        assertThat(calc.ratings(StatsRange.ALL).newlyDiscovered).isEmpty()
    }
}
