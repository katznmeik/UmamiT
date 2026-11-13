package com.umamit.core.domain.stats

import com.umamit.core.domain.collection.CostRule
import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.model.StockLevel
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The tab *Überblick* (INS-01, INS-03, INS-06) for one range.
 *
 * @property sessionCount the finished sessions in the range.
 * @property cupCount their cups (INV-08).
 * @property teaCount the non-archived teas now („24 Tees").
 * @property teasAddedInRange the non-archived teas created in the range („+3 im Zeitraum", hidden at 0); null under
 * *Gesamt*, where it would be every tea (D-115 Amendment 1, R12).
 * @property sessionsPerPeriod the bars, one per period of the range.
 * @property streak over all time, never the range.
 * @property mostBrewed the top 5 teas by sessions in the range.
 * @property teaDonut the second donut „Tees" (INS-03, R6): the five teas with most sessions, then „Andere".
 * @property sorten the donut: the five largest Sorten, then „Andere" ([SorteShare.sorte] null) when it has any.
 * @property dayParts the four day parts, in [DayPart] order, with their shares.
 * @property weekdays Mo–So with their shares.
 */
data class OverviewStats(
    val sessionCount: Int,
    val cupCount: Int,
    val teaCount: Int,
    val teasAddedInRange: Int?,
    val sessionsPerPeriod: List<PeriodValue<Int>>,
    val streak: Streak,
    val mostBrewed: List<TeaSessions>,
    val teaDonut: List<TeaShare>,
    val sorten: List<SorteShare>,
    val dayParts: List<Share<DayPart>>,
    val weekdays: List<Share<DayOfWeek>>,
)

/**
 * The streak („Serie", INS-01) in days; [current] 0 reads „Keine Serie gerade", with „ · längste N Tage" when
 * [longest] is above 0 (R13). The current streak includes today when today has a session, else it ends yesterday.
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

/**
 * A slice of the tea donut (INS-03, R6): the share of the range's sessions of one tea. [isOther] marks „Andere" (the
 * teas beyond the five largest; [teaName] is then empty). [teaId] is null for a deleted tea, named by its snapshot.
 */
data class TeaShare(
    val teaId: String?,
    val teaName: String,
    val sessions: Int,
    val percent: Int,
    val isOther: Boolean = false,
)

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
 * The tab *Bewertungen* (INS-02, D-115 Amendment 1) for one range.
 *
 * @property ratedCount „18 von [sessionCount] Sitzungen bewertet"; 0 reads „In diesem Zeitraum nichts bewertet".
 * @property teaRanking the Tee-Rangliste (R4, R11): every tea with at least one rating in the range, best average
 * first, ties by more ratings, then by name. All rows; the screen shows the first 10.
 * @property newlyDiscovered „Neu entdeckt" (R4): the teas whose first finished session ever falls in the range,
 * newest first; always empty under *Gesamt*.
 * @property ratingPerPeriod the line: the average per period, null for a period without a rating (the line breaks).
 * @property bestRecipes up to 5 teas with ≥ 2 recipes rated ≥ 2× each, the largest gap first; empty hides it.
 */
data class RatingsStats(
    val ratedCount: Int,
    val sessionCount: Int,
    val teaRanking: List<TeaRatingRow>,
    val newlyDiscovered: List<NewTea>,
    val ratingPerPeriod: List<PeriodValue<Double?>>,
    val bestRecipes: List<BestRecipeTea>,
)

/**
 * „4,6 ★ · 5×" in the Tee-Rangliste: [average] over the tea's rated sessions in the range, rounded to one decimal
 * (half up) so the order matches what is shown; [teaId] null for a deleted tea (no tap). [coverPhoto] and
 * [styleKey] as in [TeaSessions].
 */
data class TeaRatingRow(
    val teaId: String?,
    val teaName: String,
    val average: Double,
    val ratingCount: Int,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)

/**
 * A tea's recipes by average rating, best first („Gyokuro Hisui · Kalt 4,6 ★ · Klassisch 4,0 ★").
 *
 * @property gap the best recipe's average minus the worst's.
 */
data class BestRecipeTea(
    val teaId: String?,
    val teaName: String,
    val recipes: List<RecipeRating>,
    val gap: Double,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)

/** A recipe's average over its rated sessions; [profileName] is the latest session's `profile_name` snapshot. */
data class RecipeRating(val profileId: String, val profileName: String?, val average: Double, val ratingCount: Int)

/**
 * A tea of „Neu entdeckt" (R4): [firstDay] is the day of its first finished session ever. [average] is its average
 * rating over its rated sessions in the range, one decimal, null when none was rated („ohne Bewertung");
 * [ratingCount] their number. [teaId], [coverPhoto] and [styleKey] as in [TeaRatingRow].
 */
data class NewTea(
    val teaId: String?,
    val teaName: String,
    val firstDay: LocalDate,
    val average: Double?,
    val ratingCount: Int,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)

/**
 * The tab *Verbrauch* (INS-05) for one range.
 *
 * @property grams Σ leaf of the range's sessions („38 g").
 * @property cups Σ cups of the range's sessions.
 * @property moneyCents Σ `price_cents` of the priced INITIAL and PURCHASE events dated in the range; 0 reads
 * „Keine Käufe mit Preis" when [hasPurchases] is false.
 * @property costPerCup „Ø € pro Tasse" (GETRUNKEN, R9): Σ `cost_cents` ÷ Σ cups ([CostRule.sessionCups] with the cup
 * size) over the range's sessions with a cost; null hides it.
 * @property drunkCents GETRUNKEN (R9): Σ `cost_cents` of the range's sessions; sessions without one add nothing.
 * @property drunkSessionCount the sessions with a `cost_cents` („aus 20 Sitzungen mit Preis"); 0 reads „Keine
 * Sitzungen mit Preis".
 * @property drunkPerPeriod the bars of GETRUNKEN: Σ `cost_cents` per period.
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
    val drunkCents: Long,
    val drunkSessionCount: Int,
    val drunkPerPeriod: List<PeriodValue<Long>>,
    val runningOut: List<RunningOutTea>,
)

/** „12 g · knapp" / „leer"; [level] is [StockLevel.EMPTY] or [StockLevel.LOW]. */
data class RunningOutTea(
    val teaId: String,
    val teaName: String,
    val remainingGrams: Double,
    val level: StockLevel,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)
