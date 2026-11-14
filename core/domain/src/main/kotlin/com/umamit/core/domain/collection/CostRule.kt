package com.umamit.core.domain.collection

import com.umamit.core.model.BrewSession
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockSummary
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * What a tea costs on its detail (INV-08, D-104, D-115 Amendment 2); all amounts in cents, rounded half up.
 *
 * @property pricePer100gCents „12,40 € / 100 g".
 * @property recipeLiterCents „ca. 2,40 € pro Liter (Rezept)"; null without a default recipe with water and a
 * brewing step.
 * @property average „Ø 2,70 € pro Liter aus 5 Sitzungen"; null before the first priced session with a volume.
 */
data class TeaCost(val pricePer100gCents: Long, val recipeLiterCents: Long? = null, val average: LiterAverage? = null)

/** The real cost per litre over [sessionCount] finished sessions with a stored cost and a volume. */
data class LiterAverage(val literCents: Long, val sessionCount: Int)

/** The real cost per cup over [sessionCount] sessions; kept for the Statistik until #275 converts it to litres. */
data class CupAverage(val cupCents: Long, val sessionCount: Int)

/**
 * The cost rule of INV-08 and D-104, on top of [StockSummary], and the volume rule (D-115 Amendment 2): a session's
 * volume is the session's water for each poured non-rinse step (a whisk-only session pours it once), 0 ml without a
 * `water_ml` or when every step was skipped. Cost is shown per litre.
 */
object CostRule {
    private const val GRAMS_PER_PRICE = 100
    private const val ML_PER_LITER = 1000.0

    /** The old cup size, 150 ml: only the Statistik's cups still use it until #275. */
    const val DEFAULT_CUP_SIZE_ML = 150
    private const val HALF = 0.5

    /**
     * The tea's cost, or null when no priced INITIAL or PURCHASE event exists („no price, no cost").
     *
     * @param stock the tea's [StockSummary]: its weighted price per gram.
     * @param defaultRecipe the tea's default recipe, for the estimate.
     * @param sessions the tea's sessions; only finished ones with a `cost_cents` and a volume count for the average.
     */
    fun of(stock: StockSummary, defaultRecipe: Recipe?, sessions: List<BrewSession>): TeaCost? {
        val perGram = stock.costPerGramCents ?: return null
        return TeaCost(
            pricePer100gCents = (perGram * GRAMS_PER_PRICE).roundToLong(),
            recipeLiterCents = defaultRecipe?.let { recipe ->
                val ml = sessionVolumeMl(recipe.steps.map { it.kind }, recipe.waterMl)
                if (ml > 0) (recipe.leafGrams * perGram * ML_PER_LITER / ml).roundToLong() else null
            },
            average = average(sessions),
        )
    }

    private fun average(sessions: List<BrewSession>): LiterAverage? {
        val priced = sessions
            .filter { it.status != SessionStatus.IN_PROGRESS && it.costCents != null }
            .map { session ->
                val poured = session.steps.filter { it.actualSeconds != null && !it.skipped }.map { it.kind }
                (session.costCents ?: 0L) to sessionVolumeMl(poured, session.snapshot.waterMl)
            }
            .filter { (_, ml) -> ml > 0 }
        if (priced.isEmpty()) return null
        val cents = priced.sumOf { it.first }
        val ml = priced.sumOf { it.second.toLong() }
        return LiterAverage(literCents = (cents * ML_PER_LITER / ml).roundToLong(), sessionCount = priced.size)
    }

    /**
     * A session's volume in ml (INV-08, INS-01, D-115 Amendment 2): [waterMl] for each poured non-rinse step, once
     * for whisking only; 0 without a positive [waterMl] or when nothing was poured.
     *
     * @param poured the kinds of the session's poured steps: ended (an actual time) and not skipped; for a recipe,
     * its steps.
     */
    fun sessionVolumeMl(poured: List<StepKind>, waterMl: Int?): Int =
        if (waterMl == null || waterMl <= 0) 0 else infusions(poured) * waterMl

    /**
     * A finished session's cups, unrounded: its poured infusions × [waterMl] ÷ [cupSizeMl], never below 1. Only the
     * Statistik still uses it, until #275 converts it to volume.
     */
    fun sessionCups(poured: List<StepKind>, waterMl: Int?, cupSizeMl: Int): Double {
        val infusions = infusions(poured).coerceAtLeast(1)
        val cups = if (waterMl == null || waterMl <= 0) {
            infusions.toDouble()
        } else {
            infusions.toDouble() * waterMl / cupSizeMl.coerceAtLeast(1)
        }
        return cups.coerceAtLeast(1.0)
    }

    /** A sum of unrounded cups as shown: whole cups, rounded half up. */
    fun displayCups(cups: Double): Int = floor(cups + HALF).toInt()

    /** The infusions of these step kinds: one per non-rinse step, one for whisking only. */
    private fun infusions(kinds: List<StepKind>): Int {
        val brewing = kinds.filter { it != StepKind.RINSE }
        return if (brewing.isNotEmpty() && brewing.all { it == StepKind.WHISK }) 1 else brewing.size
    }
}
