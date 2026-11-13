package com.umamit.core.domain.stats

import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.TasteProfile
import kotlin.math.floor

// The rules of *Bewertungen* (INS-02, INS-04, D-115). Every function takes the range's rated (or all) sessions.

private const val TOP = 5
private const val MAX_STARS = 5
private const val TOP_RATED_MIN_RATINGS = 3
private const val BEST_RECIPE_MIN_RECIPES = 2
private const val BEST_RECIPE_MIN_RATINGS = 2

/** Five buckets 1–5 ★: half stars rounded down, 0,5 counts as 1 ★. */
internal fun starBuckets(rated: List<DatedSession>): List<StarBucket> {
    val counts = rated.groupingBy { floor(it.rating).toInt().coerceIn(1, MAX_STARS) }.eachCount()
    return (1..MAX_STARS).map { StarBucket(it, counts[it] ?: 0) }
}

/** The top 5 teas by average among those with ≥ 3 rated sessions; ties by more ratings, then by name. */
internal fun topRated(rated: List<DatedSession>, names: Map<String, String>): List<TeaRatingRow> = rated
    .groupBy { it.session.teaKey() }
    .values
    .filter { it.size >= TOP_RATED_MIN_RATINGS }
    .map { group ->
        TeaRatingRow(group.first().session.teaId, group.teaName(names), group.map { it.rating }.average(), group.size)
    }
    .sortedWith(
        compareByDescending<TeaRatingRow> { it.average }.thenByDescending { it.ratingCount }.thenBy { it.teaName },
    )
    .take(TOP)

/** The average rating per period; null where a period has none. */
internal fun ratingPerPeriod(window: StatsWindow, rated: List<DatedSession>): List<PeriodValue<Double?>> =
    perPeriod(window, rated, RatingSum()) { sum, session -> sum.plus(session.rating) }
        .map { PeriodValue(it.period, it.value.average) }

private data class RatingSum(val total: Double = 0.0, val count: Int = 0) {
    fun plus(rating: Double) = RatingSum(total + rating, count + 1)

    val average: Double? get() = if (count == 0) null else total / count
}

/**
 * The best recipe (INS-02): the teas with ≥ 2 recipes rated ≥ 2× each, their qualifying recipes best first; up to
 * 5 teas by the gap between best and worst, then by name. Sessions without a recipe don't count.
 */
internal fun bestRecipes(rated: List<DatedSession>, names: Map<String, String>): List<BestRecipeTea> = rated
    .filter { it.session.profileId != null }
    .groupBy { it.session.teaKey() }
    .values
    .mapNotNull { teaSessions ->
        val recipes = teaSessions.groupBy { it.session.profileId.orEmpty() }
            .filterValues { it.size >= BEST_RECIPE_MIN_RATINGS }
            .map { (profileId, group) ->
                val name = group.maxBy { it.session.startedAtMs }.session.profileName
                RecipeRating(profileId, name, group.map { it.rating }.average(), group.size)
            }
            .sortedWith(compareByDescending<RecipeRating> { it.average }.thenBy { it.profileName.orEmpty() })
        if (recipes.size < BEST_RECIPE_MIN_RECIPES) return@mapNotNull null
        BestRecipeTea(
            teaId = teaSessions.first().session.teaId,
            teaName = teaSessions.teaName(names),
            recipes = recipes,
            gap = recipes.first().average - recipes.last().average,
        )
    }
    .sortedWith(compareByDescending<BestRecipeTea> { it.gap }.thenBy { it.teaName })
    .take(TOP)

/**
 * The radar (INS-04): per axis over every infusion that set it: the taste on the step rows and, for a v0.6.0
 * session without step rows, the session's own (05 §9 v7), as the tea detail averages.
 */
internal fun taste(sessions: List<DatedSession>): TasteStats {
    val profiles = sessions.flatMap { dated ->
        dated.session.steps.map { it.taste } + dated.session.sessionTaste
    }.filter { it.setCount > 0 }
    return TasteStats(TasteProfile.averages(profiles), profiles.size)
}

/** The five most frequent known descriptor keys, most frequent first, then by key. */
internal fun topDescriptors(sessions: List<DatedSession>, known: Set<String>): List<DescriptorCount> = sessions
    .flatMap { it.session.descriptorKeys.distinct() }
    .filter { it in known }
    .groupingBy { it }
    .eachCount()
    .map { (key, count) -> DescriptorCount(key, count) }
    .sortedWith(compareByDescending<DescriptorCount> { it.count }.thenBy { it.key })
    .take(TOP)
