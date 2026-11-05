package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import org.junit.Test

/** *Im Shop öffnen* (CAT-11, D-072): the size bought last for a tea in the collection, else the product page. */
class ShopLinkTest {
    private fun pack(grams: Double, label: String, url: String?) = CatalogPackage(
        articleNumber = "XY0100-${grams.toInt()}",
        label = label,
        grams = grams,
        priceCents = null,
        priceIsEstimate = false,
        savingsPercent = null,
        variantUrl = url,
        available = true,
    )

    private fun event(type: InventoryEventType, grams: Double, at: Long, label: String? = null) = InventoryEvent(
        id = "e$at",
        teaId = "tea",
        type = type,
        deltaGrams = grams,
        occurredAtMillis = at,
        packageLabel = label,
    )

    private val packages = listOf(
        pack(50.0, "50 g", "$PRODUCT-50"),
        pack(100.0, "100 g", PRODUCT),
        pack(250.0, "250 g · 3 % gespart", "$PRODUCT-250"),
    )

    @Test
    fun aTeaNotInTheCollectionOpensTheProductPage() {
        assertThat(ShopLink.url(PRODUCT, packages, events = null)).isEqualTo(PRODUCT)
    }

    @Test
    fun anOwnedTeaOpensTheSizeOfItsLatestPurchase() {
        val events = listOf(
            event(InventoryEventType.INITIAL, 50.0, at = 1),
            event(InventoryEventType.PURCHASE, 250.0, at = 3),
            event(InventoryEventType.BREW, -4.0, at = 4),
            event(InventoryEventType.ADJUSTMENT, 10.0, at = 5),
        )

        assertThat(ShopLink.url(PRODUCT, packages, events)).isEqualTo("$PRODUCT-250")
        assertThat(ShopLink.url(PRODUCT, packages, events.take(1))).isEqualTo("$PRODUCT-50")
    }

    @Test
    fun theLabelMatchesWhenTheGramsDont() {
        val events = listOf(event(InventoryEventType.PURCHASE, 49.0, at = 1, label = "50 g"))

        assertThat(ShopLink.url(PRODUCT, packages, events)).isEqualTo("$PRODUCT-50")
    }

    @Test
    fun withoutAMatchingSizeOrPurchaseTheProductPageOpens() {
        // A size no longer sold, a size without its own URL, no purchase at all, no package sizes yet.
        assertThat(ShopLink.url(PRODUCT, packages, listOf(event(InventoryEventType.PURCHASE, 1000.0, at = 1))))
            .isEqualTo(PRODUCT)
        assertThat(
            ShopLink.url(PRODUCT, listOf(pack(50.0, "50 g", null)), listOf(event(InventoryEventType.INITIAL, 50.0, 1))),
        ).isEqualTo(PRODUCT)
        assertThat(ShopLink.url(PRODUCT, packages, listOf(event(InventoryEventType.BREW, -4.0, at = 1))))
            .isEqualTo(PRODUCT)
        assertThat(ShopLink.url(PRODUCT, emptyList(), listOf(event(InventoryEventType.INITIAL, 50.0, at = 1))))
            .isEqualTo(PRODUCT)
    }

    @Test
    fun theReorderLinkFollowsTheCatalogTea() {
        val bought = listOf(event(InventoryEventType.INITIAL, 50.0, at = 1))
        val tea = CatalogTeaDetail(key = "XY0100", name = "Morgennebel", productUrl = PRODUCT, packages = packages)

        // INV-09: the size bought last; before the catalog was ever loaded, the tea's own product page.
        assertThat(ShopLink.reorderUrl("XY0100", tea, OWN, bought)).isEqualTo("$PRODUCT-50")
        assertThat(ShopLink.reorderUrl("XY0100", null, OWN, bought)).isEqualTo(OWN)
        // AC2: no link without a catalog key or for a tea no longer in the range.
        assertThat(ShopLink.reorderUrl(null, null, OWN, bought)).isNull()
        assertThat(ShopLink.reorderUrl("XY0100", tea.copy(discontinued = true), OWN, bought)).isNull()
    }

    private companion object {
        const val OWN = "https://shop.example/own/XY0100"

        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"
    }
}
