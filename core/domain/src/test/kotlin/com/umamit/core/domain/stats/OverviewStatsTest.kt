package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.infusion
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StepKind
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** *Überblick*: INS-01 (numerals, streak, most brewed), INS-03 (Sorten), INS-06 (habits). */
class OverviewStatsTest {
    private fun streak(vararg daysAgo: Long) = calculator(daysAgo.map { session(TODAY.minusDays(it)) }).streak()

    @Test
    fun `streak - empty, today only, a run ending yesterday, a gap`() {
        assertThat(streak()).isEqualTo(Streak(0, 0))
        assertThat(streak(0)).isEqualTo(Streak(1, 1))
        // Today has no session yet: the run up to yesterday still holds.
        assertThat(streak(1, 2, 3)).isEqualTo(Streak(3, 3))
        // Nothing yesterday or today: no current streak.
        assertThat(streak(2, 3)).isEqualTo(Streak(0, 2))
        assertThat(streak(0, 1, 3, 4)).isEqualTo(Streak(2, 2))
        // Two sessions on one day count once.
        assertThat(streak(0, 0, 1)).isEqualTo(Streak(2, 2))
    }

    @Test
    fun `the longest streak can be older than the current one, and the range does not change it`() {
        val sessions = (0L..1L).map { session(TODAY.minusDays(it)) } +
            (200L..220L).map { session(TODAY.minusDays(it)) }
        val calc = calculator(sessions)

        assertThat(calc.streak()).isEqualTo(Streak(current = 2, longest = 21))
        StatsRange.entries.forEach { assertThat(calc.overview(it).streak).isEqualTo(Streak(2, 21)) }
    }

    @Test
    fun `sessions, volume and teas with those created in the range`() {
        val sessions = listOf(
            session(waterMl = 100, steps = listOf(StatsStep(StepKind.RINSE, ended = true), infusion(), infusion())),
            session(waterMl = 100, steps = listOf(StatsStep(StepKind.WHISK, ended = true))),
            session(TODAY.minusDays(40)),
        )
        val teas = listOf(
            tea("a", created = TODAY.minusDays(3)),
            tea("b", created = TODAY.minusDays(60)),
            tea("c", created = TODAY.minusDays(2), archived = true),
        )

        val overview = calculator(sessions, teas).overview(StatsRange.DAYS_30)

        assertThat(overview.sessionCount).isEqualTo(2)
        assertThat(overview.volumeMl).isEqualTo(300L)
        assertThat(overview.teaCount).isEqualTo(2)
        assertThat(overview.teasAddedInRange).isEqualTo(1)
    }

    @Test
    fun `most brewed is the top 5 by sessions, a deleted tea under its snapshot without an id`() {
        val sessions = List(4) { session(teaId = "a", teaName = "Alt") } +
            List(3) { session(teaId = null, teaName = "Gelöschter Tee") } +
            List(2) { session(teaId = "b", teaName = "Bergwind") } +
            List(2) { session(teaId = "c", teaName = "Abendrot") } +
            session(teaId = "d") + session(teaId = "e") + session(teaId = "f", teaName = "Zuletzt")
        val teas = listOf(tea("a", name = "Neuer Name"))

        val top = calculator(sessions, teas).overview(StatsRange.DAYS_30).mostBrewed

        assertThat(top.map { it.teaName to it.sessions }).containsExactly(
            "Neuer Name" to 4,
            "Gelöschter Tee" to 3,
            "Abendrot" to 2,
            "Bergwind" to 2,
            "Morgennebel" to 1,
        ).inOrder()
        assertThat(top[1].teaId).isNull()
        assertThat(top[0].teaId).isEqualTo("a")
    }

    @Test
    fun `most brewed carries the cover photo and the style key, a deleted tea the latest snapshot style`() {
        val photo = java.io.File("cover.jpg")
        val sessions = List(2) { session(teaId = "a") } +
            session(day = TODAY.minusDays(3), teaId = null, teaName = "Weg", styleKey = "matcha") +
            session(teaId = null, teaName = "Weg", styleKey = "gyokuro")
        val teas = listOf(tea("a", coverPhoto = photo, styleKey = "sencha"))

        val top = calculator(sessions, teas).overview(StatsRange.DAYS_30).mostBrewed

        assertThat(top[0].coverPhoto).isEqualTo(photo)
        assertThat(top[0].styleKey).isEqualTo("sencha")
        assertThat(top[1].coverPhoto).isNull()
        assertThat(top[1].styleKey).isEqualTo("gyokuro")
    }

    @Test
    fun `Sorten - five named and Andere, whole percentages summing to 100 with ties`() {
        val sorts = listOf("Sencha", "Gyokuro", "Matcha", "Bancha", "Kabusecha", "Genmaicha")
        val sessions = sorts.map { session(teaType = it) } + session(teaType = null) + session(teaType = " ")

        val donut = calculator(sessions).overview(StatsRange.DAYS_30).sorten

        // 8 sessions, 1 each for six Sorten (12,5 %), Andere = Sencha's sixth place + no-Sorte ×2 = 3 (37,5 %).
        assertThat(
            donut.map {
                it.sorte
            },
        ).containsExactly("Bancha", "Genmaicha", "Gyokuro", "Kabusecha", "Matcha", null)
            .inOrder()
        assertThat(donut.last().sessions).isEqualTo(3)
        assertThat(donut.sumOf { it.percent }).isEqualTo(100)
        assertThat(donut.map { it.percent }).containsExactly(13, 13, 12, 12, 12, 38).inOrder()
    }

    @Test
    fun `Sorten - thirds round to 100 and no Andere slice when every session has a top Sorte`() {
        val sessions = listOf(session(teaType = "Sencha"), session(teaType = "Gyokuro"), session(teaType = "Matcha"))

        val donut = calculator(sessions).overview(StatsRange.DAYS_30).sorten

        assertThat(donut.map { it.percent }).containsExactly(34, 33, 33).inOrder()
        assertThat(donut.none { it.sorte == null }).isTrue()
        assertThat(calculator(listOf(session(teaType = null))).overview(StatsRange.DAYS_30).sorten)
            .containsExactly(SorteShare(null, 1, 100))
    }

    @Test
    fun `day parts at their edges`() {
        val edges = mapOf(
            LocalTime.of(4, 59) to DayPart.ABEND,
            LocalTime.of(5, 0) to DayPart.MORGEN,
            LocalTime.of(10, 59) to DayPart.MORGEN,
            LocalTime.of(11, 0) to DayPart.MITTAG,
            LocalTime.of(13, 59) to DayPart.MITTAG,
            LocalTime.of(14, 0) to DayPart.NACHMITTAG,
            LocalTime.of(17, 59) to DayPart.NACHMITTAG,
            LocalTime.of(18, 0) to DayPart.ABEND,
        )
        edges.forEach { (time, part) -> assertThat(dayPartOf(time)).isEqualTo(part) }

        val parts = calculator(edges.keys.map { session(time = it) }).overview(StatsRange.DAYS_30).dayParts
        assertThat(parts.map { it.key }).containsExactlyElementsIn(DayPart.entries).inOrder()
        assertThat(parts.map { it.sessions }).containsExactly(2, 2, 2, 2).inOrder()
        assertThat(parts.map { it.percent }).containsExactly(25, 25, 25, 25).inOrder()
    }

    @Test
    fun `weekdays run Monday to Sunday with shares`() {
        // 9 Nov 2026 is a Monday.
        val sessions = listOf(
            session(LocalDate.of(2026, 11, 9)),
            session(LocalDate.of(2026, 11, 9)),
            session(LocalDate.of(2026, 11, 13)),
        )

        val weekdays = calculator(sessions).overview(StatsRange.DAYS_30).weekdays

        assertThat(weekdays.map { it.key }).containsExactlyElementsIn(DayOfWeek.entries).inOrder()
        assertThat(weekdays.first().percent).isEqualTo(67)
        assertThat(weekdays.single { it.key == DayOfWeek.FRIDAY }.percent).isEqualTo(33)
        assertThat(weekdays.sumOf { it.percent }).isEqualTo(100)
    }

    @Test
    fun `an empty range gives zeros and no slices`() {
        val overview = calculator(listOf(session(TODAY.minusDays(100)))).overview(StatsRange.DAYS_30)

        assertThat(overview.sessionCount).isEqualTo(0)
        assertThat(overview.sorten).isEmpty()
        assertThat(overview.mostBrewed).isEmpty()
        assertThat(overview.dayParts.map { it.percent }).containsExactly(0, 0, 0, 0)
        assertThat(overview.sessionsPerPeriod.map { it.value }).containsExactly(0, 0, 0, 0, 0)
    }
}
