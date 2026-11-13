package com.umamit.core.domain.stats

import kotlin.math.floor

// The rules of *Bewertungen* (INS-02, D-115). Every function takes the range's rated (or all) sessions.

private const val TOP = 5
private const val TEN = 10.0
private const val HALF = 0.5
private const val BEST_RECIPE_MIN_RECIPES = 2
private const val BEST_RECIPE_MIN_RATINGS = 2

/**
 * The Tee-Rangliste (D-115 Amendment 1, R4, R11): every tea with at least one rating in [rated], best average first
 * (one decimal, half up), ties by more ratings, then by name.
 */
internal fun teaRanking(
    rated: List<DatedSession>,
    names: Map<String, String>,
    teas: Map<String, StatsTea> = emptyMap(),
): List<TeaRatingRow> = rated
    .groupBy { it.session.teaKey() }
    .values
    .map { group ->
        val teaId = group.first().session.teaId
        val tea = teaId?.let(teas::get)
        TeaRatingRow(
            teaId = teaId,
            teaName = group.teaName(names),
            average = oneDecimal(group.map { it.rating }.average()),
            ratingCount = group.size,
            coverPhoto = tea?.coverPhoto,
            styleKey = tea?.styleKey ?: group.maxBy { it.session.startedAtMs }.session.styleKey,
        )
    }
    .sortedWith(
        compareByDescending<TeaRatingRow> { it.average }.thenByDescending { it.ratingCount }.thenBy { it.teaName },
    )

/**
 * „Neu entdeckt" (R4): the teas whose first finished session ever ([all], not only the range's) falls in [window],
 * newest first, with their average over [rated] (the range's rated sessions). Under *Gesamt* nothing is new.
 */
internal fun newlyDiscovered(
    all: List<DatedSession>,
    rated: List<DatedSession>,
    window: StatsWindow,
    names: Map<String, String>,
    teas: Map<String, StatsTea> = emptyMap(),
): List<NewTea> {
    if (window.from == null) return emptyList()
    val ratedByTea = rated.groupBy { it.session.teaKey() }
    return all
        .groupBy { it.session.teaKey() }
        .mapNotNull { (key, group) ->
            val first = group.minBy { it.session.startedAtMs }
            if (first.day !in window) return@mapNotNull null
            val ratings = ratedByTea[key].orEmpty()
            val teaId = first.session.teaId
            val tea = teaId?.let(teas::get)
            NewTea(
                teaId = teaId,
                teaName = group.teaName(names),
                firstDay = first.day,
                average = ratings.takeIf { it.isNotEmpty() }?.let { oneDecimal(it.map { r -> r.rating }.average()) },
                ratingCount = ratings.size,
                coverPhoto = tea?.coverPhoto,
                styleKey = tea?.styleKey ?: group.maxBy { it.session.startedAtMs }.session.styleKey,
            ) to first.session.startedAtMs
        }
        .sortedWith(compareByDescending<Pair<NewTea, Long>> { it.second }.thenBy { it.first.teaName })
        .map { it.first }
}

/** [value] rounded half up to one decimal. */
private fun oneDecimal(value: Double): Double = floor(value * TEN + HALF) / TEN

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
internal fun bestRecipes(
    rated: List<DatedSession>,
    names: Map<String, String>,
    teas: Map<String, StatsTea> = emptyMap(),
): List<BestRecipeTea> = rated
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
        val teaId = teaSessions.first().session.teaId
        val tea = teaId?.let(teas::get)
        BestRecipeTea(
            teaId = teaId,
            teaName = teaSessions.teaName(names),
            recipes = recipes,
            gap = recipes.first().average - recipes.last().average,
            coverPhoto = tea?.coverPhoto,
            styleKey = tea?.styleKey ?: teaSessions.maxBy { it.session.startedAtMs }.session.styleKey,
        )
    }
    .sortedWith(compareByDescending<BestRecipeTea> { it.gap }.thenBy { it.teaName })
    .take(TOP)
