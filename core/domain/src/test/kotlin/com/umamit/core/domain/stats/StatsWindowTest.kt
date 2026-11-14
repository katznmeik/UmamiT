package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.clock
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.model.StatsRange
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** INS-07: the rolling ranges and their periods; a session's day in its own zone (02 §12 counting rules). */
class StatsWindowTest {
    @Test
    fun `30 T is today and the 29 days before, in ISO weeks whose edge weeks are clipped`() {
        // Today is Friday 13 Nov 2026 (KW 46); the range starts on Wednesday 15 Oct (KW 42).
        val window = StatsWindow.of(StatsRange.DAYS_30, TODAY, firstSessionDay = null)

        assertThat(window.from).isEqualTo(LocalDate.of(2026, 10, 15))
        assertThat(window.periods.map { it.isoWeek }).containsExactly(42, 43, 44, 45, 46).inOrder()
        assertThat(window.periods.first().start).isEqualTo(LocalDate.of(2026, 10, 15))
        assertThat(window.periods.first().end).isEqualTo(LocalDate.of(2026, 10, 18))
        assertThat(window.periods.last().start).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(window.periods.last().end).isEqualTo(TODAY)
        assertThat(window.periods.map { it.isCurrent }).containsExactly(false, false, false, false, true).inOrder()
        assertThat(window.periods.all { it.unit == PeriodUnit.WEEK }).isTrue()
    }

    @Test
    fun `90 T has about 13 weekly bars`() {
        val window = StatsWindow.of(StatsRange.DAYS_90, TODAY, firstSessionDay = null)

        assertThat(window.from).isEqualTo(TODAY.minusDays(89))
        assertThat(window.periods).hasSize(14) // 16 Aug (Sun, KW 33) … 13 Nov (KW 46)
        assertThat(window.periods.first().isoWeek).isEqualTo(33)
    }

    @Test
    fun `weeks across a year boundary read KW 53 then KW 1`() {
        // 2026 has 53 ISO weeks: Mon 28 Dec 2026 – Sun 3 Jan 2027 is KW 53, Mon 4 Jan 2027 is KW 1.
        val today = LocalDate.of(2027, 1, 6)
        val window = StatsWindow.of(StatsRange.DAYS_30, today, firstSessionDay = null)

        assertThat(window.periods.map { it.isoWeek }.takeLast(2)).containsExactly(53, 1).inOrder()
        assertThat(window.periodIndex(LocalDate.of(2027, 1, 3))).isEqualTo(window.periods.size - 2)
        assertThat(window.periodIndex(LocalDate.of(2027, 1, 4))).isEqualTo(window.periods.size - 1)
        assertThat(window.periodIndex(window.from!!.minusDays(1))).isEqualTo(-1)
    }

    @Test
    fun `12 M is the current month and the 11 before`() {
        val window = StatsWindow.of(StatsRange.MONTHS_12, TODAY, firstSessionDay = null)

        assertThat(window.from).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(window.periods).hasSize(12)
        assertThat(window.periods.last().month).isEqualTo(YearMonth.of(2026, 11))
        assertThat(window.periods.last().end).isEqualTo(TODAY)
        assertThat(window.periods.all { it.unit == PeriodUnit.MONTH && it.isoWeek == null }).isTrue()
    }

    @Test
    fun `Gesamt runs per month from the first session's month, without a lower bound`() {
        val window = StatsWindow.of(StatsRange.ALL, TODAY, firstSessionDay = LocalDate.of(2026, 3, 17))

        assertThat(window.from).isNull()
        assertThat(window.periods.first().start).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(window.periods.map { it.month }.last()).isEqualTo(YearMonth.of(2026, 11))
        assertThat(window.periods).hasSize(9)
        assertThat(StatsWindow.of(StatsRange.ALL, TODAY, firstSessionDay = null).periods).isEmpty()
    }

    @Test
    fun `Gesamt starts at the first session's month through the calculator`() {
        val calc = calculator(listOf(session(LocalDate.of(2026, 9, 30)), session(TODAY)))

        assertThat(calc.window(StatsRange.ALL).periods.map { it.month })
            .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)).inOrder()
        assertThat(calc.overview(StatsRange.ALL).sessionsPerPeriod.map { it.value }).containsExactly(1, 0, 1).inOrder()
    }

    @Test
    fun `a session counts on its own zone's day, not the clock's`() {
        // 23:30 on 13 Nov in New York is 14 Nov in Berlin, but the session belongs to 13 Nov (today).
        val newYork = ZoneId.of("America/New_York")
        val late = session(TODAY, LocalTime.of(23, 30), zone = newYork)
        // 00:30 on 14 Oct in Tokyo is still 13 Oct in Berlin, but belongs to 14 Oct: outside 30 T (from 15 Oct)
        val tokyo = session(LocalDate.of(2026, 10, 14), LocalTime.of(0, 30), zone = ZoneId.of("Asia/Tokyo"))
        val inside = session(LocalDate.of(2026, 10, 15), LocalTime.of(0, 30), zone = ZoneId.of("Asia/Tokyo"))

        val overview = calculator(listOf(late, tokyo, inside)).overview(StatsRange.DAYS_30)

        assertThat(overview.sessionCount).isEqualTo(2)
        assertThat(overview.sessionsPerPeriod.first().value).isEqualTo(1)
        assertThat(overview.sessionsPerPeriod.last().value).isEqualTo(1)
        assertThat(overview.streak.current).isEqualTo(1)
    }

    @Test
    fun `DST change days keep the session on its local day and part`() {
        // 25 Oct 2026: Berlin falls back at 03:00; 29 Mar 2026: it springs forward at 02:00.
        val autumn = session(LocalDate.of(2026, 10, 25), LocalTime.of(0, 30))
        val autumnEvening = session(LocalDate.of(2026, 10, 25), LocalTime.of(23, 59))
        val spring = session(LocalDate.of(2026, 3, 29), LocalTime.of(5, 0))
        val calc = calculator(listOf(autumn, autumnEvening, spring))

        val days = calc.overview(StatsRange.DAYS_30)
        assertThat(days.sessionCount).isEqualTo(2)
        assertThat(days.weekdays.single { it.key == java.time.DayOfWeek.SUNDAY }.sessions).isEqualTo(2)
        assertThat(days.dayParts.single { it.key == DayPart.ABEND }.sessions).isEqualTo(2)
        val all = calc.overview(StatsRange.ALL)
        assertThat(all.dayParts.single { it.key == DayPart.MORGEN }.sessions).isEqualTo(1)
        assertThat(all.sessionsPerPeriod.first().period.month).isEqualTo(YearMonth.of(2026, 3))
    }

    @Test
    fun `a session dated after today does not count`() {
        val overview = calculator(listOf(session(TODAY.plusDays(1)))).overview(StatsRange.ALL)

        assertThat(overview.sessionCount).isEqualTo(0)
        assertThat(calculator(listOf(session(TODAY.plusDays(1)))).hasSessions).isFalse()
    }

    @Test
    fun `the clock's zone decides today`() {
        val calc = calculator(listOf(session(TODAY)), clock = clock(TODAY.plusDays(1)))

        assertThat(calc.streak()).isEqualTo(Streak(current = 1, longest = 1))
    }
}
