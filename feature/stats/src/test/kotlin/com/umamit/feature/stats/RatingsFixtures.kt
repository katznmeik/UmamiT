package com.umamit.feature.stats

import com.umamit.core.domain.stats.BestRecipeTea
import com.umamit.core.domain.stats.NewTea
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.PeriodValue
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.RecipeRating
import com.umamit.core.domain.stats.StatsPeriod
import com.umamit.core.domain.stats.TeaRatingRow
import java.time.LocalDate

/** Fixed results of the *Bewertungen* tab (synthetic data only). */
internal object RatingsFixtures {
    private val weeks = (0 until 8).map { index ->
        val start = LocalDate.of(2026, 9, 21).plusWeeks(index.toLong())
        StatsPeriod(
            PeriodUnit.WEEK,
            start,
            start.plusDays(6),
            start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            index == 7,
        )
    }

    private val ratings = listOf(3.5, 4.0, null, 4.5, 4.0, 5.0, null, 4.5)

    val withData = RatingsStats(
        ratedCount = 18,
        sessionCount = 25,
        teaRanking = listOf(
            TeaRatingRow("tea-1", "Gyokuro Hisui", 4.6, 5, styleKey = "gyokuro"),
            TeaRatingRow("tea-2", "Sencha Asahi", 4.2, 4, styleKey = "sencha"),
            TeaRatingRow(null, "Gelöschter Tee", 4.0, 3),
        ) + (1..9).map { TeaRatingRow("extra-$it", "Testtee $it", 3.9 - it * 0.1, 2) },
        newlyDiscovered = listOf(
            NewTea("tea-1", "Gyokuro Hisui", LocalDate.of(2026, 11, 2), 4.6, 5, styleKey = "gyokuro"),
            NewTea("new-2", "Neuling ohne Wertung", LocalDate.of(2026, 10, 20), null, 0),
            NewTea(null, "Gelöschter Neuling", LocalDate.of(2026, 10, 14), 3.0, 1),
        ) + (4..7).map { NewTea("new-$it", "Neuer Tee $it", LocalDate.of(2026, 10, 1), 4.0, 1) },
        ratingPerPeriod = weeks.mapIndexed { index, period -> PeriodValue(period, ratings[index]) },
        bestRecipes = listOf(
            BestRecipeTea(
                "tea-1",
                "Gyokuro Hisui",
                listOf(RecipeRating("p1", "Kalt", 4.6, 3), RecipeRating("p2", "Klassisch", 4.0, 2)),
                0.6,
            ),
        ),
    )

    /** Sessions in the range, none rated. */
    val withoutRatings = withData.copy(
        ratedCount = 0,
        teaRanking = emptyList(),
        ratingPerPeriod = weeks.map { PeriodValue(it, null) },
        bestRecipes = emptyList(),
    )

    /** Sessions in the range, none rated, but a tea discovered. */
    val withoutRatingsNew = withoutRatings.copy(newlyDiscovered = withData.newlyDiscovered.take(2))

    val emptyRange = RatingsStats(
        ratedCount = 0,
        sessionCount = 0,
        teaRanking = emptyList(),
        newlyDiscovered = emptyList(),
        ratingPerPeriod = emptyList(),
        bestRecipes = emptyList(),
    )
}
