package com.umamit.core.model

/**
 * A catalog tea as the catalog list shows it (SCR-19, CAT-10): a Tee-Kontor green tea of the
 * last refresh, keyed by the shop's base article number (05 §4.1).
 *
 * @property name the shop's name; the article number when the page showed none.
 * @property teaType the *Sorte* as the shop names it (Sencha, Gyokuro, …), if any.
 * @property basePriceCentsPer100g the price of 100 g in euro cents, if the shop showed one.
 * @property available in stock at the refresh; null when the page didn't say.
 */
data class CatalogTeaSummary(
    val key: String,
    val name: String,
    val secondaryName: String?,
    val teaType: String?,
    val basePriceCentsPer100g: Long?,
    val available: Boolean?,
)

/** The catalog list's content: the teas by name and when the last successful refresh finished (CAT-07 AC6). */
data class CatalogOverview(val teas: List<CatalogTeaSummary>, val lastRefreshAtMillis: Long?) {
    /** Never loaded or emptied: the list shows SCR-19's empty state. */
    val isEmpty: Boolean get() = teas.isEmpty()
}
