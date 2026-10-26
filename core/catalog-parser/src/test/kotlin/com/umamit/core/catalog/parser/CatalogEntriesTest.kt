package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SyntheticShop.Size
import com.umamit.core.catalog.parser.SyntheticShop.Stock
import org.junit.Test

/**
 * One catalog entry per tea (08 §2.4 items 6–7; #67): a tea the shop sells under one article
 * number per size („split numbers") is merged by its normalised name into one entry keyed by the
 * lowest number, with every size at its own page's price. Every page is synthetic (D-016).
 */
class CatalogEntriesTest {
    @Suppress("LongParameterList")
    private fun entry(
        number: String,
        slug: String,
        title: String,
        grams: Int,
        price: String,
        stock: Stock = Stock.IN_STOCK,
        sizes: List<Size> = listOf(Size("${grams}g", checked = true)),
    ): CatalogEntry {
        val url = "$SHOP/$slug/$number"
        val html = SyntheticShop.productPage(
            baseUrl = SHOP,
            title = title,
            sku = number,
            canonicalPath = "/$slug/$number",
            price = price,
            unitContent = "$grams g",
            unitReference = null,
            stock = stock,
            sizes = sizes,
        )
        val page = (TeeKontorParser.parseProduct(html.toByteArray(), url) as ProductParse.Parsed).page
        return CatalogEntry.of(ProductGroup(number, url, emptyMap()), page)
    }

    @Test
    fun aTeaSplitIntoOneNumberPerSizeIsOneEntryUnderTheLowestNumber() {
        val small = entry("XY0868", "abendrot-matcha-30g", "Abendrot Matcha 30g", 30, "9,90 €")
        val large = entry("XY0869", "abendrot-matcha-100g", "Abendrot Matcha – 100g", 100, "29,00 €")

        val entries = CatalogEntries.merge(listOf(large, small))

        val merged = entries.single()
        assertThat(merged.key).isEqualTo("XY0868")
        assertThat(merged.mergedKeys).containsExactly("XY0869")
        assertThat(merged.page.name).isEqualTo("Abendrot Matcha 30g")
        assertThat(merged.page.productUrl).isEqualTo("$SHOP/abendrot-matcha-30g/XY0868")
        assertThat(merged.packages).containsExactly(
            ShopPackage("XY0868", 30.0, 990, false, null, "$SHOP/abendrot-matcha-30g/XY0868", true),
            ShopPackage("XY0869", 100.0, 2900, false, null, "$SHOP/abendrot-matcha-100g/XY0869", true),
        ).inOrder()
    }

    @Test
    fun aSizesOwnPageWinsOverAnotherPagesEstimate() {
        // The 30 g page offers the 100 g size too; the 100 g page's own price replaces the estimate.
        val small = entry(
            "XY0868",
            "abendrot-matcha-30g",
            "Abendrot Matcha – 30g",
            30,
            "9,90 €",
            stock = Stock.OUT_OF_STOCK,
            sizes = listOf(Size("30g", checked = true), Size("100g")),
        )
        val large = entry("XY0869", "abendrot-matcha-100g", "Abendrot Matcha – 100g", 100, "29,00 €")

        val merged = CatalogEntries.merge(listOf(small, large)).single()

        assertThat(merged.packages.map { Triple(it.grams, it.priceCents, it.priceIsEstimate) }).containsExactly(
            Triple(30.0, 990L, false),
            Triple(100.0, 2900L, false),
        ).inOrder()
        // In stock when any of its pages is.
        assertThat(merged.page.available).isTrue()
    }

    @Test
    fun teasWithTheSameNameAndTheSameSizeStayApart() {
        // Same name, same size: two teas (e.g. two harvests), not one tea split by size.
        val first = entry("XY0283", "kabusecha-hikari", "Kabusecha Hikari – 100g", 100, "12,00 €")
        val second = entry("XY0656", "kabusecha-hikari", "Kabusecha Hikari – 100g", 100, "13,00 €")

        val entries = CatalogEntries.merge(listOf(second, first))

        assertThat(entries.map { it.key }).containsExactly("XY0283", "XY0656").inOrder()
        assertThat(entries.flatMap { it.mergedKeys }).isEmpty()
    }

    @Test
    fun teasWithOtherNamesAreNeverMerged() {
        val sencha = entry("XY0100", "morgennebel-sencha", "Morgennebel Sencha – 100g", 100, "12,40 €")
        val gyokuro = entry("XY0200", "abendrot-gyokuro-50g", "Abendrot Gyokuro – 50g", 50, "15,00 €")

        val entries = CatalogEntries.merge(listOf(gyokuro, sencha))

        assertThat(entries.map { it.key }).containsExactly("XY0100", "XY0200").inOrder()
    }

    @Test
    fun theNameIsNormalisedWithoutSizeCaseAndPunctuation() {
        assertThat(CatalogEntries.normalisedName("Bio Abendrot-Matcha 30g")).isEqualTo("bio abendrot matcha")
        assertThat(CatalogEntries.normalisedName("BIO Abendrot Matcha (100 g)")).isEqualTo("bio abendrot matcha")
        assertThat(CatalogEntries.normalisedName("Abendrot Matcha 0,5kg")).isEqualTo("abendrot matcha")
        assertThat(CatalogEntries.normalisedName("Sencha 2026")).isEqualTo("sencha 2026")
    }

    private companion object {
        const val SHOP = "https://shop.example"
    }
}
