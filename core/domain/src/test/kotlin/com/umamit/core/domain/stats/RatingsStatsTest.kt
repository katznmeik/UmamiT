package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.infusion
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import org.junit.Test

/** *Bewertungen*: INS-02 (average, distribution, top rated, over time, best recipe) and INS-04 (taste). */
class RatingsStatsTest {
    @Test
    fun `average over rated sessions and the 1-5 star buckets, half stars rounded down`() {
        val sessions = listOf(0.5, 1.0, 4.5, 5.0).map { session(rating = it) } + session(rating = null)

        val ratings = calculator(sessions).ratings(StatsRange.DAYS_30)

        assertThat(ratings.average).isWithin(1e-9).of(2.75)
        assertThat(ratings.ratedCount).isEqualTo(4)
        assertThat(ratings.sessionCount).isEqualTo(5)
        assertThat(ratings.distribution.map { it.stars }).containsExactly(1, 2, 3, 4, 5).inOrder()
        assertThat(ratings.distribution.map { it.count }).containsExactly(2, 0, 0, 1, 1).inOrder()
    }

    @Test
    fun `no rating gives no average`() {
        assertThat(calculator(listOf(session())).ratings(StatsRange.DAYS_30).average).isNull()
    }

    @Test
    fun `top rated needs 3 ratings in the range`() {
        val sessions = listOf(4.0, 5.0, 4.5).map { session(teaId = "a", teaName = "Alpha", rating = it) } +
            listOf(5.0, 5.0).map { session(teaId = "b", teaName = "Beta", rating = it) } +
            listOf(3.0, 3.0, 3.0, 3.0).map { session(teaId = "c", teaName = "Gamma", rating = it) } +
            session(teaId = "b", teaName = "Beta", rating = 5.0, day = TODAY.minusDays(40))

        val top = calculator(sessions).ratings(StatsRange.DAYS_30).topRated

        assertThat(top.map { it.teaName }).containsExactly("Alpha", "Gamma").inOrder()
        assertThat(top.first().average).isWithin(1e-9).of(4.5)
        assertThat(top.first().ratingCount).isEqualTo(3)
        assertThat(calculator(sessions).ratings(StatsRange.DAYS_90).topRated.first().teaName).isEqualTo("Beta")
    }

    @Test
    fun `the average per period has gaps where no rating is`() {
        // 30 T has weeks KW 42–46; ratings in KW 42 (16 Oct) and KW 46 (today) only.
        val sessions = listOf(
            session(TODAY.minusDays(28), rating = 3.0),
            session(TODAY.minusDays(28), rating = 4.0),
            session(TODAY, rating = 5.0),
            session(TODAY.minusDays(7)),
        )

        val line = calculator(sessions).ratings(StatsRange.DAYS_30).ratingPerPeriod

        assertThat(line.map { it.value }).containsExactly(3.5, null, null, null, 5.0).inOrder()
    }

    private fun rated(tea: String, profile: String?, vararg values: Double) = values.map {
        session(
            teaId = tea,
            teaName = "Tee $tea",
            profileId = profile,
            profileName = profile?.let {
                "Rezept $it"
            },
            rating = it,
        )
    }

    @Test
    fun `best recipe - two recipes rated twice each, ordered by the gap, without recipe-less sessions`() {
        val sessions =
            // Tea a: gap 1,5 (Kalt 4,75 vs Klassisch 3,25); the recipe-less 1,0 ratings don't count.
            rated("a", "kalt", 4.5, 5.0) + rated("a", "klassisch", 3.0, 3.5) + rated("a", null, 1.0, 1.0) +
                // Tea b: gap 0,5 with three qualifying recipes, the one-rating recipe left out.
                rated(
                    "b",
                    "x",
                    4.0,
                    4.0,
                ) + rated("b", "y", 3.5, 3.5) + rated("b", "z", 4.0, 4.5) + rated("b", "w", 1.0) +
                // Tea c: only one recipe has two ratings.
                rated("c", "x", 4.0, 4.0) + rated("c", "y", 1.0) +
                // Tea d: two recipes, but only through the recipe-less sessions: not qualifying.
                rated("d", "x", 5.0, 5.0) + rated("d", null, 1.0, 1.0)

        val best = calculator(sessions).ratings(StatsRange.DAYS_30).bestRecipes

        assertThat(best.map { it.teaId }).containsExactly("a", "b").inOrder()
        assertThat(best[0].recipes.map { it.profileName }).containsExactly("Rezept kalt", "Rezept klassisch").inOrder()
        assertThat(best[0].gap).isWithin(1e-9).of(1.5)
        assertThat(best[1].recipes.map { it.profileId }).containsExactly("z", "x", "y").inOrder()
        assertThat(best[1].gap).isWithin(1e-9).of(0.75)
    }

    @Test
    fun `best recipe is empty when no tea qualifies, and lists at most 5 teas`() {
        assertThat(calculator(rated("a", "x", 4.0, 4.0)).ratings(StatsRange.DAYS_30).bestRecipes).isEmpty()

        val six = (1..6).flatMap { rated("t$it", "x", 5.0, 5.0) + rated("t$it", "y", 5.0 - it * 0.5, 5.0 - it * 0.5) }
        val best = calculator(six).ratings(StatsRange.DAYS_30).bestRecipes
        assertThat(best.map { it.teaId }).containsExactly("t6", "t5", "t4", "t3", "t2").inOrder()
    }

    @Test
    fun `taste per axis over the infusions that set it, v0_6_0 session taste included`() {
        val sessions = listOf(
            session(
                steps = listOf(
                    StatsStep(StepKind.RINSE, ended = true),
                    infusion(TasteProfile(umami = 4, sweetness = 2)),
                    infusion(TasteProfile(umami = 2)),
                    infusion(),
                ),
            ),
            // A v0.6.0 session without step rows keeps its own taste.
            session(steps = emptyList(), sessionTaste = TasteProfile(umami = 3, body = 5)),
            session(TODAY.minusDays(40), steps = listOf(infusion(TasteProfile(umami = 0)))),
        )

        val taste = calculator(sessions).ratings(StatsRange.DAYS_30).taste

        assertThat(taste.infusionCount).isEqualTo(3)
        assertThat(taste.averages[TasteAxis.UMAMI]).isWithin(1e-9).of(3.0)
        assertThat(taste.averages[TasteAxis.SWEETNESS]).isWithin(1e-9).of(2.0)
        assertThat(taste.averages[TasteAxis.BODY]).isWithin(1e-9).of(5.0)
        assertThat(taste.averages).doesNotContainKey(TasteAxis.AROMA)
    }

    @Test
    fun `top 5 descriptors with counts, unknown keys dropped`() {
        val sessions = listOf(
            session(descriptorKeys = listOf("marine.nori", "sweet.honig", "alt.weg")),
            session(descriptorKeys = listOf("marine.nori", "green.gras")),
            session(descriptorKeys = listOf("marine.nori", "a.b", "c.d", "e.f", "alt.weg")),
        )
        val known = setOf("marine.nori", "sweet.honig", "green.gras", "a.b", "c.d", "e.f")

        val top = calculator(sessions, known = known).ratings(StatsRange.DAYS_30).topDescriptors

        assertThat(top).containsExactly(
            DescriptorCount("marine.nori", 3),
            DescriptorCount("a.b", 1),
            DescriptorCount("c.d", 1),
            DescriptorCount("e.f", 1),
            DescriptorCount("green.gras", 1),
        ).inOrder()
    }
}
