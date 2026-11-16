package com.umamit.core.domain.stats

import com.umamit.core.model.StatsRankingBy
import kotlin.math.floor

// The rules of *Bewertungen* (INS-02, D-115, D-117). Every function takes the range's sessions.

private const val TOP = 5
private const val TEN = 10.0
private const val HALF = 0.5
private const val BEST_RECIPE_MIN_RECIPES = 2
private const val BEST_RECIPE_MIN_RATINGS = 2

/** The rating of the kind [by]: *Tee* (`rating`) or *Zubereitung* (`brewing_rating`, D-117); null when unrated. */
private fun StatsSession.ratingOf(by: StatsRankingBy): Double? = when (by) {
    StatsRankingBy.TEA -> rating
    StatsRankingBy.BREWING -> brewingRating
}

/** A session with the rating of the kind it was picked by. */
private class Rated(val dated: DatedSession, val rating: Double)

private fun List<DatedSession>.ratedBy(by: StatsRankingBy): List<Rated> =
    mapNotNull { dated -> dated.session.ratingOf(by)?.let { Rated(dated, it) } }

/**
 * The Tee-Rangliste (D-115 Amendment 1, R4, R11; D-117 Q5): every tea with at least one rating of the kind [by] in
 * [inRange], best average first (one decimal, half up), ties by more ratings, then by name.
 */
internal fun teaRanking(
    inRange: List<DatedSession>,
    by: StatsRankingBy,
    names: Map<String, String>,
    teas: Map<String, StatsTea>,
): List<TeaRatingRow> = inRange
    .ratedBy(by)
    .groupBy { it.dated.session.teaKey() }
    .values
    .map { rated ->
        val group = rated.map { it.dated }
        val tea = group.groupTea(names, teas)
        TeaRatingRow(
            teaId = tea.teaId,
            teaName = tea.teaName,
            average = oneDecimal(rated.map { it.rating }.average()),
            ratingCount = group.size,
            coverPhoto = tea.coverPhoto,
            styleKey = tea.styleKey,
        )
    }
    .sortedWith(
        compareByDescending<TeaRatingRow> { it.average }.thenByDescending { it.ratingCount }.thenBy { it.teaName },
    )

/**
 * „Neu entdeckt" (R4, D-117 Q8): the teas whose first finished session ever ([all], not only the range's) falls in
 * [window], newest first, with their *Tee* average over [inRange] (the range's sessions). Under *Gesamt* nothing is
 * new.
 */
internal fun newlyDiscovered(
    all: List<DatedSession>,
    inRange: List<DatedSession>,
    window: StatsWindow,
    names: Map<String, String>,
    teas: Map<String, StatsTea>,
): List<NewTea> {
    if (window.from == null) return emptyList()
    val ratedByTea = inRange.ratedBy(StatsRankingBy.TEA).groupBy { it.dated.session.teaKey() }
    return all
        .groupBy { it.session.teaKey() }
        .mapNotNull { (key, group) ->
            val first = group.minBy { it.session.startedAtMs }
            if (first.day !in window) return@mapNotNull null
            val ratings = ratedByTea[key].orEmpty()
            val tea = group.groupTea(names, teas)
            NewTea(
                teaId = tea.teaId,
                teaName = tea.teaName,
                firstDay = first.day,
                average = ratings.takeIf { it.isNotEmpty() }?.let { oneDecimal(it.map { r -> r.rating }.average()) },
                ratingCount = ratings.size,
                coverPhoto = tea.coverPhoto,
                styleKey = tea.styleKey,
            ) to first.session.startedAtMs
        }
        .sortedWith(compareByDescending<Pair<NewTea, Long>> { it.second }.thenBy { it.first.teaName })
        .map { it.first }
}

/** [value] rounded half up to one decimal. */
private fun oneDecimal(value: Double): Double = floor(value * TEN + HALF) / TEN

/** The average rating of the kind [by] per period; null where a period has none (each kind has its own gaps). */
internal fun ratingPerPeriod(
    window: StatsWindow,
    inRange: List<DatedSession>,
    by: StatsRankingBy,
): List<PeriodValue<Double?>> = inRange.ratedBy(by).let { rated ->
    val sessions = rated.associate { it.dated to it.rating }
    perPeriod(window, rated.map { it.dated }, RatingSum()) { sum, session -> sum.plus(sessions.getValue(session)) }
        .map { PeriodValue(it.period, it.value.average) }
}

private data class RatingSum(val total: Double = 0.0, val count: Int = 0) {
    fun plus(rating: Double) = RatingSum(total + rating, count + 1)

    val average: Double? get() = if (count == 0) null else total / count
}

/**
 * The best recipe (INS-02, D-117 Q7): the teas with ≥ 2 recipes with a *Zubereitung* rating ≥ 2× each, their
 * qualifying recipes best first by their *Zubereitung* average; up to 5 teas by the gap between best and worst, then
 * by name. Sessions without a recipe don't count.
 */
internal fun bestRecipes(
    inRange: List<DatedSession>,
    names: Map<String, String>,
    teas: Map<String, StatsTea>,
): List<BestRecipeTea> = inRange
    .ratedBy(StatsRankingBy.BREWING)
    .filter { it.dated.session.profileId != null }
    .groupBy { it.dated.session.teaKey() }
    .values
    .mapNotNull { teaRated ->
        val teaSessions = teaRated.map { it.dated }
        val recipes = teaRated.groupBy { it.dated.session.profileId.orEmpty() }
            .filterValues { it.size >= BEST_RECIPE_MIN_RATINGS }
            .map { (profileId, group) ->
                val name = group.maxBy { it.dated.session.startedAtMs }.dated.session.profileName
                RecipeRating(profileId, name, group.map { it.rating }.average(), group.size)
            }
            .sortedWith(compareByDescending<RecipeRating> { it.average }.thenBy { it.profileName.orEmpty() })
        if (recipes.size < BEST_RECIPE_MIN_RECIPES) return@mapNotNull null
        val tea = teaSessions.groupTea(names, teas)
        BestRecipeTea(
            teaId = tea.teaId,
            teaName = tea.teaName,
            recipes = recipes,
            gap = recipes.first().average - recipes.last().average,
            coverPhoto = tea.coverPhoto,
            styleKey = tea.styleKey,
        )
    }
    .sortedWith(compareByDescending<BestRecipeTea> { it.gap }.thenBy { it.teaName })
    .take(TOP)
