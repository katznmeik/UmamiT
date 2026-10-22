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
