package com.umamit.feature.stats

import com.umamit.core.domain.stats.BestRecipeTea
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.PeriodValue
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.RecipeRating
import com.umamit.core.domain.stats.StarBucket
import com.umamit.core.domain.stats.StatsPeriod
import com.umamit.core.domain.stats.TasteStats
import com.umamit.core.domain.stats.TeaRatingRow
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
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

    val fullTaste = TasteStats(
        averages = mapOf(
            TasteAxis.UMAMI to 4.5,
            TasteAxis.SWEETNESS to 3.5,
            TasteAxis.BITTERNESS to 1.5,
            TasteAxis.ASTRINGENCY to 2.0,
            TasteAxis.BODY to 3.0,
            TasteAxis.AROMA to 4.0,
        ),
        infusionCount = 34,
    )

    val withData = RatingsStats(
        average = 4.2,
        ratedCount = 18,
        sessionCount = 25,
        distribution = listOf(StarBucket(1, 0), StarBucket(2, 1), StarBucket(3, 3), StarBucket(4, 8), StarBucket(5, 6)),
        topRated = listOf(
            TeaRatingRow("tea-1", "Gyokuro Hisui", 4.6, 5),
            TeaRatingRow("tea-2", "Sencha Asahi", 4.2, 4),
            TeaRatingRow(null, "Gelöschter Tee", 4.0, 3),
        ),
        ratingPerPeriod = weeks.mapIndexed { index, period -> PeriodValue(period, ratings[index]) },
        bestRecipes = listOf(
            BestRecipeTea(
                "tea-1",
                "Gyokuro Hisui",
                listOf(RecipeRating("p1", "Kalt", 4.6, 3), RecipeRating("p2", "Klassisch", 4.0, 2)),
                0.6,
            ),
        ),
        taste = fullTaste,
        topDescriptors = listOf(
            DescriptorCount(TasteDescriptor.MARINE_NORI.key, 12),
            DescriptorCount(TasteDescriptor.MARINE_KOMBU.key, 7),
        ),
    )

    /** Sessions in the range, none rated; taste on two axes only. */
    val withoutRatings = withData.copy(
        average = null,
        ratedCount = 0,
        distribution = emptyList(),
        topRated = emptyList(),
        ratingPerPeriod = weeks.map { PeriodValue(it, null) },
        bestRecipes = emptyList(),
        taste = TasteStats(mapOf(TasteAxis.UMAMI to 4.0, TasteAxis.SWEETNESS to 3.5), 6),
        topDescriptors = emptyList(),
    )

    val emptyRange = RatingsStats(
        average = null,
        ratedCount = 0,
        sessionCount = 0,
        distribution = emptyList(),
        topRated = emptyList(),
        ratingPerPeriod = emptyList(),
        bestRecipes = emptyList(),
        taste = TasteStats(emptyMap(), 0),
        topDescriptors = emptyList(),
    )
}
