package com.umamit.core.domain.collection

import com.umamit.core.model.BrewSession
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockSummary
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * What a tea costs on its detail (INV-08, D-104); all amounts in cents, rounded half up.
 *
 * @property pricePer100gCents „12,40 € / 100 g".
 * @property recipeCupCents „ca. 0,24 € pro Tasse (Rezept)"; null without a default recipe with a cup.
 * @property average „Ø 0,27 € pro Tasse aus 5 Sitzungen"; null before the first priced session.
 */
data class TeaCost(val pricePer100gCents: Long, val recipeCupCents: Long? = null, val average: CupAverage? = null)

/** The real cost per cup over [sessionCount] finished sessions with a stored cost. */
data class CupAverage(val cupCents: Long, val sessionCount: Int)

/**
 * The cost rule of INV-08 and D-104, on top of [StockSummary], and the one cup rule (D-115 Amendment 1): a cup is
 * poured water ÷ the cup size. A rinse pours none, a whisk-only recipe or session is one infusion, and a session
 * without a `water_ml` counts one cup per infusion.
 */
object CostRule {
    private const val GRAMS_PER_PRICE = 100

    /** `cupSizeMl`'s default, 150 ml (05 §6). */
    const val DEFAULT_CUP_SIZE_ML = 150
    private const val HALF = 0.5

    /**
     * The tea's cost, or null when no priced INITIAL or PURCHASE event exists („no price, no cost").
     *
     * @param stock the tea's [StockSummary]: its weighted price per gram.
     * @param defaultRecipe the tea's default recipe, for the estimate.
     * @param sessions the tea's sessions; only the finished ones with a `cost_cents` count for the average.
     * @param cupSizeMl the cup size setting: the water of one cup.
     */
    fun of(
        stock: StockSummary,
        defaultRecipe: Recipe?,
        sessions: List<BrewSession>,
        cupSizeMl: Int = DEFAULT_CUP_SIZE_ML,
    ): TeaCost? {
        val perGram = stock.costPerGramCents ?: return null
        return TeaCost(
            pricePer100gCents = (perGram * GRAMS_PER_PRICE).roundToLong(),
            recipeCupCents = defaultRecipe?.let { recipe ->
                val cups =
                    infusions(recipe.steps.map { it.kind }) * recipe.waterMl.toDouble() / cupSizeMl.coerceAtLeast(1)
                if (cups > 0) (recipe.leafGrams * perGram / cups).roundToLong() else null
            },
            average = average(sessions, cupSizeMl),
        )
    }

    private fun average(sessions: List<BrewSession>, cupSizeMl: Int): CupAverage? {
        val priced = sessions.filter { it.status != SessionStatus.IN_PROGRESS && it.costCents != null }
        if (priced.isEmpty()) return null
        val cost = priced.sumOf { it.costCents ?: 0L }
        val cups = priced.sumOf { session ->
            val poured = session.steps.filter { it.actualSeconds != null && !it.skipped }.map { it.kind }
            sessionCups(poured, session.snapshot.waterMl, cupSizeMl)
        }
        return CupAverage(cupCents = (cost / cups).roundToLong(), sessionCount = priced.size)
    }

    /**
     * A finished session's cups, unrounded (INV-08, INS-01): its poured infusions × [waterMl] ÷ [cupSizeMl], never
     * below 1 (a session that poured nothing, or whisked only, still used its leaf). Without [waterMl] each
     * infusion is one cup.
     *
     * @param poured the kinds of the session's poured steps: ended (an actual time) and not skipped.
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
