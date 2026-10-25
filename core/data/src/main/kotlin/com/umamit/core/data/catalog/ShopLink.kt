package com.umamit.core.data.catalog

import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import kotlin.math.abs

/**
 * The shop page *Im Shop öffnen* opens (CAT-11, D-072): for a tea in the collection, the page of
 * the package size bought last, else the product page. Opening it is the user's own navigation in
 * the browser, not a network use of the app (GEN-01).
 */
object ShopLink {
    /** Grams of a package and of a purchase are the same size within this. */
    private const val SAME_GRAMS = 0.01

    /**
     * The URL to open for a catalog tea with [productUrl] and [packages]: when [events] (the stock
     * events of the collection tea linked to it) have an INITIAL or PURCHASE event, the variant
     * URL of the package whose grams, else whose label, match the latest of them; without a
     * match, or for a tea not in the collection (null), [productUrl].
     */
    fun url(productUrl: String, packages: List<CatalogPackage>, events: List<InventoryEvent>?): String {
        val bought = events.orEmpty()
            .withIndex()
            .filter { it.value.type == InventoryEventType.INITIAL || it.value.type == InventoryEventType.PURCHASE }
            .maxWithOrNull(compareBy({ it.value.occurredAtMillis }, { it.index }))
            ?.value
            ?: return productUrl
        val sized = packages.filter { it.variantUrl != null }
        val match = sized.firstOrNull { abs(it.grams - bought.deltaGrams) < SAME_GRAMS }
            ?: bought.packageLabel?.let { label ->
                sized.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
            }
        return match?.variantUrl ?: productUrl
    }
}
