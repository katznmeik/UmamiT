package com.umamit.core.model

/** A stock change of a tea (05 §3.10). [deltaGrams] is positive when added, negative when used. */
data class InventoryEvent(
    val id: String,
    val teaId: String,
    val type: InventoryEventType,
    val deltaGrams: Double,
    val occurredAtMillis: Long,
    val sessionId: String? = null,
    /** Price paid, for INITIAL and PURCHASE events. */
    val priceCents: Long? = null,
    val currency: String? = DEFAULT_CURRENCY,
    val packageLabel: String? = null,
    val note: String? = null,
) {
    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

/**
 * Stock of one tea, derived from its events (05 §3.10).
 *
 * @property remainingGrams Σ `delta_grams` of all events.
 * @property costPerGramCents Σ price / Σ grams of the INITIAL and PURCHASE events that carry a
 *   price; null when no such event exists.
 */
data class StockSummary(val remainingGrams: Double, val costPerGramCents: Double?) {
    companion object {
        private val PRICED_TYPES = setOf(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)

        /** Computes the stock of the given events of one tea. */
        fun of(events: List<InventoryEvent>): StockSummary {
            val priced = events.filter { it.type in PRICED_TYPES && it.priceCents != null }
            val pricedGrams = priced.sumOf { it.deltaGrams }
            val cost = if (pricedGrams > 0.0) priced.sumOf { it.priceCents!! }.toDouble() / pricedGrams else null
            return StockSummary(remainingGrams = events.sumOf { it.deltaGrams }, costPerGramCents = cost)
        }
    }
}

/** INV-05's stock status of a tea: *Leer*, *Knapp* or *Vorrätig*. */
enum class StockLevel {
    EMPTY,
    LOW,
    AVAILABLE,
    ;

    companion object {
        /** Below this a stock reads „Leer": it shows as „0,0 g" (INV-05: Leer ≤ 0 g). */
        const val EMPTY_BELOW_GRAMS = 0.05

        /** Sums of 0,1 g steps in doubles differ from the threshold only by rounding noise. */
        private const val SAME_GRAMS = 1e-6

        /** The status of [remainingGrams] with the „Knapp ab" [thresholdGrams] (the tea's own, else SET-16's). */
        fun of(remainingGrams: Double, thresholdGrams: Double): StockLevel = when {
            remainingGrams < EMPTY_BELOW_GRAMS -> EMPTY
            remainingGrams <= thresholdGrams + SAME_GRAMS -> LOW
            else -> AVAILABLE
        }
    }
}

/**
 * Start's low-stock card (INV-10, SCR-02, D-097): „*[teaName]* wird knapp · 14 g übrig", or „… ist leer".
 *
 * @property styleKey the tea's style, for the leaf's colour.
 * @property reorderUrl INV-09's link for *Nachbestellen*, or null when INV-09 offers none.
 * @property hideKey what *Ausblenden* stores in `lowStockHidden`: `<tea id>/<latest INITIAL or PURCHASE event id>`.
 */
data class LowStockCard(
    val teaId: String,
    val teaName: String,
    val styleKey: String?,
    val remainingGrams: Double,
    val reorderUrl: String?,
    val hideKey: String,
) {
    /** *Leer* (INV-05): „*Tanabata* ist leer". */
    val isEmpty: Boolean get() = remainingGrams < StockLevel.EMPTY_BELOW_GRAMS
}

/**
 * One line of the daily low-stock reminder (INV-11, D-098): a tea that is *Knapp* or *Leer* (INV-05),
 * „Tanabata · 14 g" or „Hisui · leer".
 */
data class LowStockTea(val teaId: String, val teaName: String, val remainingGrams: Double) {
    /** *Leer* (INV-05): „Hisui · leer". */
    val isEmpty: Boolean get() = remainingGrams < StockLevel.EMPTY_BELOW_GRAMS
}
