package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SyntheticShop.Size
import org.junit.Test

/**
 * A tea's package sizes and prices (08 §2.4 item 6; #67): the sizes from the page's configurator
 * and the sitemap's variant URLs, the page's own size at its price, larger sizes estimated, smaller
 * ones left to the exact price fetch. Every page and URL is synthetic (D-016).
 */
class PackageSizesTest {
    private fun parse(html: String, url: String = "$SHOP/morgennebel-sencha/XY0100"): ProductPage =
        (TeeKontorParser.parseProduct(html.toByteArray(), url) as ProductParse.Parsed).page

    @Test
    fun theConfiguratorsSizesAreReadWithTheirSavingsAndThePagesOwnSize() {
        val page = parse(SyntheticShop.productPage(baseUrl = SHOP, sizes = FIVE_SIZES))

        assertThat(page.sizeOptions).containsExactly(
            SizeOption(grams = 10.0, savingsPercent = null, selected = false),
            SizeOption(grams = 50.0, savingsPercent = null, selected = false),
            SizeOption(grams = 100.0, savingsPercent = null, selected = true),
            SizeOption(grams = 250.0, savingsPercent = 3, selected = false),
            SizeOption(grams = 1000.0, savingsPercent = 8, selected = false),
        ).inOrder()
    }

    @Test
    fun optionsThatAreNoSizeAreIgnoredAndKilogramsAreConverted() {
        val page = parse(
            SyntheticShop.productPage(
                baseUrl = SHOP,
                sizes = listOf(Size("Dose"), Size("100g", checked = true), Size("1kg", "12% gespart¹"), Size("0,5 kg")),
            ),
        )

        assertThat(page.sizeOptions).containsExactly(
            SizeOption(grams = 100.0, savingsPercent = null, selected = true),
            SizeOption(grams = 1000.0, savingsPercent = 12, selected = false),
            SizeOption(grams = 500.0, savingsPercent = null, selected = false),
        ).inOrder()
    }

    @Test
    fun aPageWithoutConfiguratorHasNoOptions() {
        val page = parse(SyntheticShop.productPage(baseUrl = SHOP, sizes = emptyList()))

        assertThat(page.sizeOptions).isEmpty()
    }

    @Test
    fun thePagesOwnSizeHasItsPriceLargerSizesAreEstimatedSmallerOnesAreLeftOpen() {
        val page = parse(SyntheticShop.productPage(baseUrl = SHOP, sizes = FIVE_SIZES))

        val packages = Packages.of(page, group(VARIANTS))

        assertThat(packages).containsExactly(
            ShopPackage("XY0100-10", 10.0, null, false, null, "$SHOP/morgennebel-sencha/XY0100-10", null),
            ShopPackage("XY0100-50", 50.0, null, false, null, "$SHOP/morgennebel-sencha/XY0100-50", null),
            ShopPackage("XY0100", 100.0, 1240, false, null, "$SHOP/morgennebel-sencha/XY0100", true),
            // 12,40 € ÷ 100 g × 250 g × (1 − 3 %) = 30,07 €; × 1000 g × (1 − 8 %) = 114,08 €.
            ShopPackage("XY0100-250", 250.0, 3007, true, 3, "$SHOP/morgennebel-sencha/XY0100-250", null),
            ShopPackage("XY0100-1000", 1000.0, 11408, true, 8, "$SHOP/morgennebel-sencha/XY0100-1000", null),
        ).inOrder()
    }

    @Test
    fun anEstimateIsRoundedToTheCent() {
        // SCR-23's example: 17,90 € for 100 g → 250 g with 3 % savings ≈ 43,41 € (43,4075 €).
        val page = parse(
            SyntheticShop.productPage(
                baseUrl = SHOP,
                price = "17,90 €",
                unitReference = "(179,00 € / 1 kg)",
                sizes = listOf(Size("100g", checked = true), Size("250g", "3% gespart¹")),
            ),
        )

        val estimate = Packages.of(page, group(VARIANTS)).single { it.grams == 250.0 }

        assertThat(estimate.priceCents).isEqualTo(4341)
        assertThat(estimate.priceIsEstimate).isTrue()
    }

    @Test
    fun sizesComeFromTheConfiguratorAndTheVariantUrls() {
        // 500 g only in the sitemap (no savings known), 250 g only on the page (no variant URL).
        val page = parse(
            SyntheticShop.productPage(
                baseUrl = SHOP,
                sizes = listOf(Size("100g", checked = true), Size("250g", "3% gespart¹")),
            ),
        )

        val packages = Packages.of(page, group(mapOf("XY0100-500" to "$SHOP/morgennebel-sencha/XY0100-500")))

        assertThat(packages.map { Triple(it.articleNumber, it.priceCents, it.variantUrl) }).containsExactly(
            Triple("XY0100", 1240L, "$SHOP/morgennebel-sencha/XY0100"),
            Triple("XY0100-250", 3007L, null),
            Triple("XY0100-500", 6200L, "$SHOP/morgennebel-sencha/XY0100-500"),
        ).inOrder()
    }

    @Test
    fun withoutAPriceNoSizeIsEstimated() {
        val page = parse(
            SyntheticShop.productPage(
                baseUrl = SHOP,
                price = null,
                metaPrice = null,
                unitReference = null,
                sizes = FIVE_SIZES,
            ),
        )

        val packages = Packages.of(page, group(VARIANTS))

        assertThat(packages.map { it.grams }).containsExactly(10.0, 50.0, 100.0, 250.0, 1000.0).inOrder()
        assertThat(packages.map { it.priceCents }.distinct()).containsExactly(null)
        assertThat(packages.none { it.priceIsEstimate }).isTrue()
    }

    @Test
    fun aVariantsPageKnowsItsOwnSizeFromItsOrderNumber() {
        // A product listed only by its sizes: the smallest size's page is the one fetched (D-077).
        val url = "$SHOP/morgennebel-sencha/XY0100-50"
        val page = parse(
            SyntheticShop.productPage(
                baseUrl = SHOP,
                title = "Morgennebel Sencha – 50g",
                sku = "XY0100-50",
                canonicalPath = "/morgennebel-sencha/XY0100-50",
                price = "6,20 €",
                unitContent = "0.05 kg",
                unitReference = "(124,00 € / 1 kg)",
                sizes = listOf(Size("50g", checked = true), Size("100g")),
            ),
            url,
        )
        val sizes = mapOf("XY0100-50" to url, "XY0100-100" to "$SHOP/morgennebel-sencha/XY0100-100")

        val packages = Packages.of(page, ProductGroup("XY0100", url, sizes))

        assertThat(packages).containsExactly(
            ShopPackage("XY0100-50", 50.0, 620, false, null, url, true),
            ShopPackage("XY0100-100", 100.0, 1240, true, null, "$SHOP/morgennebel-sencha/XY0100-100", null),
        ).inOrder()
    }

    private fun group(variants: Map<String, String>) =
        ProductGroup("XY0100", "$SHOP/morgennebel-sencha/XY0100", variants)

    private companion object {
        const val SHOP = "https://shop.example"

        val VARIANTS = listOf(10, 50, 250, 1000).associate {
            "XY0100-$it" to "$SHOP/morgennebel-sencha/XY0100-$it"
        }

        val FIVE_SIZES = listOf(
            Size("10g"),
            Size("50g"),
            Size("100g", checked = true),
            Size("250g", "3% gespart¹"),
            Size("1000g", "8% gespart¹"),
        )
    }
}
