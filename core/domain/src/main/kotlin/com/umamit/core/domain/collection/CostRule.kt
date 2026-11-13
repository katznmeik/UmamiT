package com.umamit.core.domain.collection

import com.umamit.core.model.BrewSession
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockSummary
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
 * The cost rule of INV-08 and D-104, on top of [StockSummary]. One poured infusion is one cup: a rinse is none, a
 * whisk-only recipe or session is one.
 */
object CostRule {
    private const val GRAMS_PER_PRICE = 100

    /**
     * The tea's cost, or null when no priced INITIAL or PURCHASE event exists („no price, no cost").
     *
     * @param stock the tea's [StockSummary]: its weighted price per gram.
     * @param defaultRecipe the tea's default recipe, for the estimate.
     * @param sessions the tea's sessions; only the finished ones with a `cost_cents` count for the average.
     */
    fun of(stock: StockSummary, defaultRecipe: Recipe?, sessions: List<BrewSession>): TeaCost? {
        val perGram = stock.costPerGramCents ?: return null
        return TeaCost(
            pricePer100gCents = (perGram * GRAMS_PER_PRICE).roundToLong(),
            recipeCupCents = defaultRecipe?.let { recipe ->
                val cups = cups(recipe.steps.map { it.kind })
                if (cups > 0) (recipe.leafGrams * perGram / cups).roundToLong() else null
            },
            average = average(sessions),
        )
    }

    private fun average(sessions: List<BrewSession>): CupAverage? {
        val priced = sessions.filter { it.status != SessionStatus.IN_PROGRESS && it.costCents != null }
        if (priced.isEmpty()) return null
        val cost = priced.sumOf { it.costCents ?: 0L }
        val cups = priced.sumOf { session ->
            sessionCups(session.steps.filter { it.actualSeconds != null && !it.skipped }.map { it.kind })
        }
        return CupAverage(cupCents = (cost.toDouble() / cups).roundToLong(), sessionCount = priced.size)
    }

    /**
     * A finished session's cups (INV-08, INS-01): one per poured non-rinse step, whisking only is one; a session
     * that poured no cup (every step skipped, only a rinse, no step rows) still used its leaf and is one cup.
     *
     * @param poured the kinds of the session's poured steps: ended (an actual time) and not skipped.
     */
    fun sessionCups(poured: List<StepKind>): Int = cups(poured).coerceAtLeast(1)

    /** The cups of these brewing step kinds: one per non-rinse step, one for whisking only. */
    private fun cups(kinds: List<StepKind>): Int {
        val brewing = kinds.filter { it != StepKind.RINSE }
        return if (brewing.isNotEmpty() && brewing.all { it == StepKind.WHISK }) 1 else brewing.size
    }
}
