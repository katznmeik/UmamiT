package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsRankingBy
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** *Bewertungen*: INS-02 (Tee-Rangliste, Neu entdeckt, over time, best recipe), D-115 Amendment 1 R4, R11. */
class RatingsStatsTest {
    @Test
    fun `rated and total sessions are counted`() {
        val sessions = listOf(0.5, 1.0, 4.5).map { session(rating = it) } + session(rating = null)

        val ratings = calculator(sessions).ratings(StatsRange.DAYS_30)

        assertThat(ratings.ratedCount).isEqualTo(3)
        assertThat(ratings.sessionCount).isEqualTo(4)
        assertThat(calculator(listOf(session())).ratings(StatsRange.DAYS_30).teaRanking).isEmpty()
    }

    @Test
    fun `the ranking lists every tea with one rating, best one-decimal average first, all rows`() {
        val sessions = listOf(4.0, 5.0, 4.5).map { session(teaId = "a", teaName = "Alpha", rating = it) } +
            session(teaId = "b", teaName = "Beta", rating = 5.0) +
            listOf(3.0, 3.0).map { session(teaId = "c", teaName = "Gamma", rating = it) } +
            session(teaId = "d", teaName = "Delta", rating = null) +
            session(teaId = "b", teaName = "Beta", rating = 1.0, day = TODAY.minusDays(40))

        val ranking = calculator(sessions).ratings(StatsRange.DAYS_30).teaRanking

        assertThat(ranking.map { it.teaName }).containsExactly("Beta", "Alpha", "Gamma").inOrder()
        assertThat(ranking.map { it.average }).containsExactly(5.0, 4.5, 3.0).inOrder()
        assertThat(ranking.map { it.ratingCount }).containsExactly(1, 3, 2).inOrder()
        assertThat(calculator(sessions).ratings(StatsRange.DAYS_90).teaRanking.first { it.teaName == "Beta" }.average)
            .isEqualTo(3.0)
    }

    @Test
    fun `the average is one decimal, half up, and ties go to more ratings, then to the name`() {
        // 4,25 -> 4,3; 4,3 from 3 ratings; the same 4,3 from 3 ratings under another name; 4,3 from 2 ratings.
        val sessions = listOf(4.0, 4.5).map { session(teaId = "a", teaName = "Alpha", rating = it) } +
            listOf(4.5, 4.0, 4.5).map { session(teaId = "b", teaName = "Beta", rating = it) } +
            listOf(4.5, 4.0, 4.5).map { session(teaId = "c", teaName = "Cha", rating = it) } +
            listOf(4.0, 4.5).map { session(teaId = "d", teaName = "Delta", rating = it) }

        val ranking = calculator(sessions).ratings(StatsRange.DAYS_30).teaRanking

        assertThat(ranking.map { it.average }.distinct()).containsExactly(4.3)
        assertThat(ranking.map { it.teaName }).containsExactly("Beta", "Cha", "Alpha", "Delta").inOrder()
    }

    @Test
    fun `a ranking of more than 10 teas keeps every row`() {
        val sessions = (1..12).map { session(teaId = "t$it", teaName = "Tee $it", rating = 3.0) }

        assertThat(calculator(sessions).ratings(StatsRange.DAYS_30).teaRanking).hasSize(12)
    }

    @Test
    fun `a deleted tea is ranked by its snapshot name without an id`() {
        val sessions = listOf(
            session(teaId = null, teaName = "Weg", rating = 4.0, styleKey = "gyokuro"),
            session(teaId = null, teaName = "Weg", rating = 5.0),
        )

        val row = calculator(sessions).ratings(StatsRange.DAYS_30).teaRanking.single()

        assertThat(row.teaId).isNull()
        assertThat(row.teaName).isEqualTo("Weg")
        assertThat(row.average).isEqualTo(4.5)
        assertThat(row.ratingCount).isEqualTo(2)
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

    @Test
    fun `new teas are those whose first session ever falls in the range, newest first`() {
        val sessions = listOf(
            // Alpha: first brewed long ago, again in range: not new.
            session(teaId = "a", teaName = "Alpha", day = TODAY.minusDays(200)),
            session(teaId = "a", teaName = "Alpha", day = TODAY.minusDays(5), rating = 5.0),
            // Beta: first brewed 20 days ago, rated 4 and 5 in range.
            session(teaId = "b", teaName = "Beta", day = TODAY.minusDays(20), rating = 4.0),
            session(teaId = "b", teaName = "Beta", day = TODAY.minusDays(2), rating = 5.0),
            // Gamma: first brewed 3 days ago, unrated.
            session(teaId = "c", teaName = "Gamma", day = TODAY.minusDays(3)),
            // Delta: first brewed 100 days ago: before the 30-day range.
            session(teaId = "d", teaName = "Delta", day = TODAY.minusDays(100)),
            session(teaId = "d", teaName = "Delta", day = TODAY.minusDays(1), rating = 1.0),
        )

        val new = calculator(sessions).ratings(StatsRange.DAYS_30).newlyDiscovered

        assertThat(new.map { it.teaName }).containsExactly("Gamma", "Beta").inOrder()
        assertThat(new.map { it.average }).containsExactly(null, 4.5).inOrder()
        assertThat(new.map { it.ratingCount }).containsExactly(0, 2).inOrder()
        assertThat(new.map { it.firstDay }).containsExactly(TODAY.minusDays(3), TODAY.minusDays(20)).inOrder()
        assertThat(calculator(sessions).ratings(StatsRange.DAYS_90).newlyDiscovered.map { it.teaName })
            .containsExactly("Gamma", "Beta").inOrder()
    }

    @Test
    fun `new teas are empty under Gesamt`() {
        val sessions = listOf(session(teaId = "a", day = TODAY.minusDays(3), rating = 4.0))

        assertThat(calculator(sessions).ratings(StatsRange.ALL).newlyDiscovered).isEmpty()
    }

    @Test
    fun `a deleted tea can be new and carries the snapshot style, a living one its cover and style`() {
        val photo = java.io.File("cover.jpg")
        val sessions = listOf(
            session(teaId = "a", teaName = "Alpha", day = TODAY.minusDays(2)),
            session(teaId = null, teaName = "Weg", day = TODAY.minusDays(1), styleKey = "gyokuro"),
        )

        val new = calculator(sessions, listOf(tea("a", "Alpha", coverPhoto = photo, styleKey = "sencha")))
            .ratings(StatsRange.DAYS_30).newlyDiscovered

        assertThat(new.map { it.teaId }).containsExactly(null, "a").inOrder()
        assertThat(new[0].styleKey).isEqualTo("gyokuro")
        assertThat(new[1].coverPhoto).isEqualTo(photo)
        assertThat(new[1].styleKey).isEqualTo("sencha")
    }

    @Test
    fun `new teas of a custom range count the first session in those months only`() {
        val sessions = listOf(
            session(teaId = "a", teaName = "Alpha", day = LocalDate.of(2026, 3, 10)),
            session(teaId = "b", teaName = "Beta", day = LocalDate.of(2026, 2, 28)),
            session(teaId = "b", teaName = "Beta", day = LocalDate.of(2026, 3, 1)),
        )
        val march = MonthSpan(YearMonth.of(2026, 3), YearMonth.of(2026, 3))

        val new = calculator(sessions).ratings(StatsRange.CUSTOM, march).newlyDiscovered

        assertThat(new.map { it.teaName }).containsExactly("Alpha")
    }

    private fun rated(tea: String, profile: String?, vararg values: Double) = values.map {
        session(
            teaId = tea,
            teaName = "Tee $tea",
            profileId = profile,
            profileName = profile?.let {
                "Rezept $it"
            },
            brewingRating = it,
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
    fun `ranking and best recipe rows carry the cover photo and the style key, a deleted tea the snapshot style`() {
        val photo = java.io.File("cover.jpg")
        val sessions = listOf(4.0, 5.0, 4.5).map { session(teaId = "a", rating = it) } +
            listOf(3.0, 3.0, 3.0).map { session(teaId = null, teaName = "Weg", rating = it, styleKey = "gyokuro") } +
            rated("a", "kalt", 4.0, 4.0) + rated("a", "heiss", 3.0, 3.0)
        val teas = listOf(tea("a", coverPhoto = photo, styleKey = "sencha"))

        val ratings = calculator(sessions, teas).ratings(StatsRange.DAYS_30)

        val top = ratings.teaRanking.associateBy { it.teaId }
        assertThat(top.getValue("a").coverPhoto).isEqualTo(photo)
        assertThat(top.getValue("a").styleKey).isEqualTo("sencha")
        assertThat(top.getValue(null).coverPhoto).isNull()
        assertThat(top.getValue(null).styleKey).isEqualTo("gyokuro")
        assertThat(ratings.bestRecipes.single().coverPhoto).isEqualTo(photo)
        assertThat(ratings.bestRecipes.single().styleKey).isEqualTo("sencha")
    }

    @Test
    fun `the Zubereitung ranking uses only Zubereitung ratings, ties as for Tee`() {
        val sessions = listOf(
            session(teaId = "a", teaName = "Zeta", rating = 5.0, brewingRating = 4.0),
            session(teaId = "b", teaName = "Alpha", rating = 1.0, brewingRating = 4.0),
            session(teaId = "c", teaName = "Mu", brewingRating = 4.0),
            session(teaId = "c", teaName = "Mu", brewingRating = 4.0),
            session(teaId = "d", teaName = "Eins", rating = 5.0),
        )

        val ratings = calculator(sessions).ratings(StatsRange.ALL)

        assertThat(ratings.ranking(StatsRankingBy.BREWING).map { it.teaName })
            .containsExactly("Mu", "Alpha", "Zeta").inOrder()
        assertThat(ratings.ranking(StatsRankingBy.BREWING).first().ratingCount).isEqualTo(2)
        assertThat(ratings.ranking(StatsRankingBy.TEA).map { it.teaName })
            .containsExactly("Eins", "Zeta", "Alpha").inOrder()
    }

    @Test
    fun `nothing is rated only without a rating of either kind`() {
        val onlyBrewing = calculator(listOf(session(brewingRating = 3.5))).ratings(StatsRange.DAYS_30)
        val none = calculator(listOf(session())).ratings(StatsRange.DAYS_30)

        assertThat(onlyBrewing.ratedCount).isEqualTo(1)
        assertThat(onlyBrewing.teaRanking).isEmpty()
        assertThat(onlyBrewing.brewingRanking).hasSize(1)
        assertThat(none.ratedCount).isEqualTo(0)
    }

    @Test
    fun `the two Verlauf series have their own gaps`() {
        // 30 T has weeks KW 42-46: Tee in KW 42 and 46, Zubereitung in KW 42, 44 and 46.
        val sessions = listOf(
            session(TODAY.minusDays(28), rating = 3.0, brewingRating = 2.0),
            session(TODAY.minusDays(28), rating = 4.0, brewingRating = 3.0),
            session(TODAY.minusDays(14), brewingRating = 4.5),
            session(TODAY, rating = 5.0, brewingRating = 3.5),
        )

        val ratings = calculator(sessions).ratings(StatsRange.DAYS_30)

        assertThat(ratings.ratingPerPeriod.map { it.value }).containsExactly(3.5, null, null, null, 5.0).inOrder()
        assertThat(ratings.brewingRatingPerPeriod.map { it.value })
            .containsExactly(2.5, null, 4.5, null, 3.5).inOrder()
        assertThat(ratings.brewingRatingPerPeriod.map { it.period })
            .containsExactlyElementsIn(ratings.ratingPerPeriod.map { it.period }).inOrder()
    }

    @Test
    fun `best recipe compares Zubereitung, not Tee`() {
        val sessions =
            // Tee ratings say Kalt is worse, Zubereitung says better: the Zubereitung order counts.
            (1..2).map {
                session(teaId = "a", profileId = "kalt", profileName = "Kalt", rating = 2.0, brewingRating = 5.0)
            } +
                (1..2).map {
                    session(teaId = "a", profileId = "heiss", profileName = "Heiss", rating = 5.0, brewingRating = 3.0)
                } +
                // Tea b: recipes rated on Tee only: nothing qualifies.
                (1..2).map { session(teaId = "b", profileId = "x", rating = 4.0) } +
                (1..2).map { session(teaId = "b", profileId = "y", rating = 3.0) } +
                // Tea c: second recipe has just one Zubereitung rating.
                (1..2).map { session(teaId = "c", profileId = "x", brewingRating = 4.0) } +
                session(teaId = "c", profileId = "y", brewingRating = 3.0)

        val best = calculator(sessions).ratings(StatsRange.DAYS_30).bestRecipes

        assertThat(best.map { it.teaId }).containsExactly("a")
        assertThat(best.single().recipes.map { it.profileId }).containsExactly("kalt", "heiss").inOrder()
        assertThat(best.single().gap).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `Neu entdeckt averages the Tee rating, not Zubereitung`() {
        val sessions = listOf(
            session(TODAY.minusDays(5), teaId = "n", teaName = "Neu", rating = 4.0, brewingRating = 2.0),
            session(TODAY.minusDays(4), teaId = "n", teaName = "Neu", rating = 5.0),
            session(TODAY.minusDays(3), teaId = "m", teaName = "Nur Zubereitung", brewingRating = 5.0),
        )

        val found = calculator(sessions).ratings(StatsRange.DAYS_30).newlyDiscovered.associateBy { it.teaName }

        assertThat(found.getValue("Neu").average).isEqualTo(4.5)
        assertThat(found.getValue("Neu").ratingCount).isEqualTo(2)
        assertThat(found.getValue("Nur Zubereitung").average).isNull()
    }
}
