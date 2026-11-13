package com.umamit.core.domain.stats

import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteAxis
import java.io.File
import java.time.DayOfWeek

/**
 * The tab *Überblick* (INS-01, INS-03, INS-06) for one range.
 *
 * @property sessionCount the finished sessions in the range.
 * @property cupCount their cups (INV-08).
 * @property teaCount the non-archived teas now („24 Tees").
 * @property teasAddedInRange the non-archived teas created in the range („+3 im Zeitraum", hidden at 0).
 * @property sessionsPerPeriod the bars, one per period of the range.
 * @property streak over all time, never the range.
 * @property mostBrewed the top 5 teas by sessions in the range.
 * @property sorten the donut: the five largest Sorten, then „Andere" ([SorteShare.sorte] null) when it has any.
 * @property dayParts the four day parts, in [DayPart] order, with their shares.
 * @property weekdays Mo–So with their shares.
 */
data class OverviewStats(
    val sessionCount: Int,
    val cupCount: Int,
    val teaCount: Int,
    val teasAddedInRange: Int,
    val sessionsPerPeriod: List<PeriodValue<Int>>,
    val streak: Streak,
    val mostBrewed: List<TeaSessions>,
    val sorten: List<SorteShare>,
    val dayParts: List<Share<DayPart>>,
    val weekdays: List<Share<DayOfWeek>>,
)

/**
 * The streak („Serie", INS-01) in days; [current] 0 reads „Keine Serie gerade". The current streak includes today
 * when today has a session, else it ends yesterday.
 */
data class Streak(val current: Int, val longest: Int)

/**
 * A tea and its sessions in the range („12×").
 *
 * @property teaId the tea to open, or null for a deleted tea (no tap).
 * @property teaName the tea's current name, else the latest session's snapshot.
 * @property coverPhoto the tea's cover photo file, or null (a leaf stands in).
 * @property styleKey the style key (08 §2) of the leaf placeholder's colour; a deleted tea's from its latest session.
 */
data class TeaSessions(
    val teaId: String?,
    val teaName: String,
    val sessions: Int,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)

/**
 * A donut slice (INS-03): [sorte] null is „Andere"; [percent] whole numbers that sum to 100 over the slices.
 */
data class SorteShare(val sorte: String?, val sessions: Int, val percent: Int)

/** A share of the range's sessions (INS-06): [percent] whole numbers that sum to 100 over the row (0 when empty). */
data class Share<K>(val key: K, val sessions: Int, val percent: Int)

/** The day parts of INS-06, by the session's local start time. */
enum class DayPart {
    /** 05:00–10:59. */
    MORGEN,

    /** 11:00–13:59. */
    MITTAG,

    /** 14:00–17:59. */
    NACHMITTAG,

    /** 18:00–04:59. */
    ABEND,
}

/**
 * The tab *Bewertungen* (INS-02, INS-04) for one range.
 *
 * @property average the average rating of the rated sessions; null without one.
 * @property ratedCount „18 von [sessionCount] Sitzungen bewertet".
 * @property distribution five buckets 1–5 ★ (half stars rounded down, 0,5 counts as 1 ★), in star order.
 * @property topRated the top 5 teas by average among those with ≥ 3 rated sessions.
 * @property ratingPerPeriod the line: the average per period, null for a period without a rating (the line breaks).
 * @property bestRecipes up to 5 teas with ≥ 2 recipes rated ≥ 2× each, the largest gap first; empty hides it.
 * @property taste the radar.
 * @property topDescriptors the five most frequent known descriptor keys, most frequent first, then by key.
 */
data class RatingsStats(
    val average: Double?,
    val ratedCount: Int,
    val sessionCount: Int,
    val distribution: List<StarBucket>,
    val topRated: List<TeaRatingRow>,
    val ratingPerPeriod: List<PeriodValue<Double?>>,
    val bestRecipes: List<BestRecipeTea>,
    val taste: TasteStats,
    val topDescriptors: List<DescriptorCount>,
)

/** The rated sessions whose rating rounds down to [stars] (1–5). */
data class StarBucket(val stars: Int, val count: Int)

/** „Ø 4,6 ★ · 5 Bewertungen"; [teaId] null for a deleted tea (no tap). */
data class TeaRatingRow(val teaId: String?, val teaName: String, val average: Double, val ratingCount: Int)

/**
 * A tea's recipes by average rating, best first („Gyokuro Hisui · Kalt 4,6 ★ · Klassisch 4,0 ★").
 *
 * @property gap the best recipe's average minus the worst's.
 */
data class BestRecipeTea(val teaId: String?, val teaName: String, val recipes: List<RecipeRating>, val gap: Double)

/** A recipe's average over its rated sessions; [profileName] is the latest session's `profile_name` snapshot. */
data class RecipeRating(val profileId: String, val profileName: String?, val average: Double, val ratingCount: Int)

/**
 * The taste radar (INS-04): [averages] per axis over the infusions that set it (an axis none set is absent);
 * [infusionCount] the infusions with any taste („aus 34 Aufgüssen").
 */
data class TasteStats(val averages: Map<TasteAxis, Double>, val infusionCount: Int)

/**
 * The tab *Verbrauch* (INS-05) for one range.
 *
 * @property grams Σ leaf of the range's sessions („38 g").
 * @property cups Σ cups of the range's sessions.
 * @property moneyCents Σ `price_cents` of the priced INITIAL and PURCHASE events dated in the range; 0 reads
 * „Keine Käufe mit Preis" when [hasPurchases] is false.
 * @property costPerCup Σ `cost_cents` ÷ Σ cups over the range's sessions with a cost; null hides it.
 * @property runningOut the non-archived teas *Leer* or *Knapp* with the warning on, *Leer* first, then by grams;
 * not limited by the range.
 */
data class ConsumptionStats(
    val grams: Double,
    val gramsPerPeriod: List<PeriodValue<Double>>,
    val cups: Int,
    val cupsPerPeriod: List<PeriodValue<Int>>,
    val moneyCents: Long,
    val hasPurchases: Boolean,
    val moneyPerPeriod: List<PeriodValue<Long>>,
    val costPerCup: CupAverage?,
    val runningOut: List<RunningOutTea>,
)

/** „12 g · knapp" / „leer"; [level] is [StockLevel.EMPTY] or [StockLevel.LOW]. */
data class RunningOutTea(val teaId: String, val teaName: String, val remainingGrams: Double, val level: StockLevel)
