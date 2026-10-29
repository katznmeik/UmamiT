package com.umamit.core.testing

import com.umamit.core.data.catalog.AddFromCatalog
import com.umamit.core.data.catalog.AddFromCatalogResult
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.ExactPriceSession
import com.umamit.core.data.catalog.ExactPrices
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [AddFromCatalog] on a [FakeCollection]: the owner is the collection tea with the catalog key that is
 * not archived; an add creates a tea named after the key with an INITIAL event, a repurchase (an
 * archived tea's brings it back) adds a PURCHASE event. Records every [purchases]; [failNext] makes
 * the next add fail.
 */
class FakeAddFromCatalog(private val collection: FakeCollection) : AddFromCatalog {
    val purchases = mutableListOf<CatalogPurchase>()

    var failNext = false

    /** While set, each [add] waits for it: a save that takes its time. */
    var gate: CompletableDeferred<Unit>? = null

    override fun observeOwner(catalogKey: String): Flow<String?> = collection.teas.observeTeas()
        .map { teas -> teas.firstOrNull { it.tea.catalogKey == catalogKey && !it.tea.isArchived }?.tea?.id }
        .distinctUntilChanged()

    override suspend fun add(purchase: CatalogPurchase): AddFromCatalogResult {
        gate?.await()
        if (failNext) {
            failNext = false
            return AddFromCatalogResult.Failed
        }
        purchases += purchase
        val linked = collection.teaRows.value.values.firstOrNull { it.catalogKey == purchase.catalogKey }
        // As on Room: a removed (archived) tea comes back (TEA-04, D-085).
        if (linked?.isArchived == true) collection.teas.saveTea(linked.copy(isArchived = false))
        val owner = linked?.id
        val teaId = owner ?: "tea-${purchase.catalogKey}"
        if (owner == null) {
            collection.teas.saveTea(
                Tea(
                    id = teaId,
                    name = purchase.catalogKey,
                    category = TeaCategory.GREEN,
                    form = TeaForm.LOOSE_LEAF,
                    catalogKey = purchase.catalogKey,
                ),
            )
        }
        collection.stock.addEvent(
            InventoryEvent(
                id = "event-${purchases.size}",
                teaId = teaId,
                type = if (owner == null) InventoryEventType.INITIAL else InventoryEventType.PURCHASE,
                deltaGrams = purchase.grams,
                occurredAtMillis = purchases.size.toLong(),
                priceCents = purchase.priceCents,
            ),
        )
        return if (owner == null) AddFromCatalogResult.Added(teaId) else AddFromCatalogResult.Repurchased(teaId)
    }
}

/**
 * [ExactPrices] answering from [prices] by variant URL (a missing URL: offline, null). Records
 * the [requested] URLs and the [sessions] opened; while [gate] is set, every fetch waits for it.
 */
class FakeExactPrices(val prices: MutableMap<String, Long> = mutableMapOf()) : ExactPrices {
    val requested = mutableListOf<String>()

    var sessions = 0
        private set

    var gate: CompletableDeferred<Unit>? = null

    override fun session(): ExactPriceSession {
        sessions++
        return ExactPriceSession { url, _ ->
            requested += url
            gate?.await()
            prices[url]
        }
    }
}
