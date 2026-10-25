package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SyntheticShop.Stock
import org.junit.Test

/**
 * The product page parser's basic fields (08 §2.3; #64): name, secondary name, article number,
 * canonical URL, price, unit price, stock and the breadcrumb with its classification and *Sorte*.
 * Every page is synthetic (D-016). Cross-selling boxes of other products carry their own prices,
 * numbers and buttons on every page; none of them may leak into the product.
 */
class TeeKontorParserTest {
    private fun parse(html: String, url: String = "$SHOP/morgennebel-sencha/XY0100"): ProductPage {
        val result = TeeKontorParser.parseProduct(html.toByteArray(), url)
        assertThat(result).isInstanceOf(ProductParse.Parsed::class.java)
        return (result as ProductParse.Parsed).page
    }

    @Test
    fun allBasicFieldsOfAGreenTea() {
        val page = parse(SyntheticShop.productPage(baseUrl = SHOP))

        assertThat(page).isEqualTo(
            ProductPage(
                articleNumber = "XY0100",
                key = "XY0100",
                productUrl = "$SHOP/morgennebel-sencha/XY0100",
                name = "Morgennebel Sencha",
                secondaryName = null,
                breadcrumb = listOf(
                    Crumb("Tee", "/tee/"),
                    Crumb("Grüner Tee", "/tee/gruener-tee/"),
                    Crumb("Japan", "/tee/gruener-tee/japan/"),
                    Crumb("Sencha", "/tee/gruener-tee/japan/sencha/"),
                ),
                scope = CatalogScope.GREEN,
                teaType = "Sencha",
                priceCents = 1240,
                packageGrams = 100.0,
                basePriceCentsPer100g = 1240,
                available = true,
            ),
        )
        assertThat(page.categoryPath).isEqualTo("/tee/gruener-tee/japan/sencha/")
        assertThat(page.shopCategory).isEqualTo("Tee › Grüner Tee › Japan › Sencha")
    }

    @Test
    fun theSizeSuffixIsStrippedFromTheName() {
        mapOf(
            "Morgennebel Sencha – 100g" to "Morgennebel Sencha",
            "Morgennebel Sencha - 30 g" to "Morgennebel Sencha",
            "Morgennebel Sencha — 0,5 kg" to "Morgennebel Sencha",
            "Morgennebel Sencha" to "Morgennebel Sencha",
            "  Morgennebel   Sencha  " to "Morgennebel Sencha",
            "Sencha 2026 – Erste Pflückung" to "Sencha 2026 – Erste Pflückung",
        ).forEach { (title, name) ->
            assertThat(parse(SyntheticShop.productPage(title = title)).name).isEqualTo(name)
        }
    }

    @Test
    fun japaneseOrChineseTextInTheTitleIsTheSecondaryName() {
        val page = parse(SyntheticShop.productPage(title = "Morgennebel Sencha 朝霧 煎茶 – 100g"))

        assertThat(page.name).isEqualTo("Morgennebel Sencha")
        assertThat(page.secondaryName).isEqualTo("朝霧 煎茶")
        val bracketed = parse(SyntheticShop.productPage(title = "Kabuse 「かぶせ茶」 Mild"))
        assertThat(bracketed.name).isEqualTo("Kabuse Mild")
        assertThat(bracketed.secondaryName).isEqualTo("「かぶせ茶」")
    }

    @Test
    fun aMissingTitleFallsBackToTheProductMicrodata() {
        val page = parse(SyntheticShop.productPage(title = null, microdataName = "Morgennebel Sencha – 100g"))

        assertThat(page.name).isEqualTo("Morgennebel Sencha")
    }

    @Test
    fun absentFieldsStayEmpty() {
        val page = parse(
            SyntheticShop.productPage(
                title = null,
                sku = null,
                canonicalPath = null,
                price = null,
                metaPrice = null,
                unitContent = null,
                unitReference = null,
                stock = Stock.UNKNOWN,
            ),
            url = "$SHOP/morgennebel-sencha/XY0100-50",
        )

        assertThat(page.name).isNull()
        assertThat(page.articleNumber).isNull()
        // Without its number on the page, the key comes from the page's URL; so does the product URL.
        assertThat(page.key).isEqualTo("XY0100")
        assertThat(page.productUrl).isEqualTo("$SHOP/morgennebel-sencha/XY0100-50")
        assertThat(page.priceCents).isNull()
        assertThat(page.packageGrams).isNull()
        assertThat(page.basePriceCentsPer100g).isNull()
        assertThat(page.available).isNull()
        assertThat(page.scope).isEqualTo(CatalogScope.GREEN)
    }

    @Test
    fun theKeyIsTheBaseOfTheOrderNumber() {
        assertThat(parse(SyntheticShop.productPage(sku = "XY0100-50")).key).isEqualTo("XY0100")
        assertThat(parse(SyntheticShop.productPage(sku = "XY0100M")).key).isEqualTo("XY0100")
        assertThat(parse(SyntheticShop.productPage(sku = "XY0100a")).key).isEqualTo("XY0100a")
    }

    @Test
    fun pricesInGermanFormatAndTheMicrodataFallback() {
        assertThat(parse(SyntheticShop.productPage(price = "1.234,50 €")).priceCents).isEqualTo(123_450)
        assertThat(parse(SyntheticShop.productPage(price = "ab 7,05 €")).priceCents).isEqualTo(705)
        assertThat(parse(SyntheticShop.productPage(price = null, metaPrice = "17.9")).priceCents).isEqualTo(1790)
        assertThat(parse(SyntheticShop.productPage(price = "Preis auf Anfrage", metaPrice = null)).priceCents).isNull()
    }

    @Test
    fun theUnitPriceGivesThePriceOf100g() {
        val cases = listOf(
            Triple("0.1 kg", "(179,00 € / 1 kg)", 1790L),
            Triple("50 g", "(35,80 € / 100 g)", 3580L),
            Triple("0,25 kg", "(84,00 € / 1 kg)", 840L),
            Triple("30 g", "(1.299,50 € / 1 kg)", 12_995L),
        )
        cases.forEach { (content, reference, per100g) ->
            val page = parse(SyntheticShop.productPage(unitContent = content, unitReference = reference))
            assertThat(page.basePriceCentsPer100g).isEqualTo(per100g)
        }
        assertThat(parse(SyntheticShop.productPage(unitContent = "0.25 kg")).packageGrams).isEqualTo(250.0)
    }

    @Test
    fun withoutAReferenceTheUnitPriceComesFromPriceAndContent() {
        val page = parse(SyntheticShop.productPage(price = "8,95 €", unitContent = "50 g", unitReference = null))

        assertThat(page.basePriceCentsPer100g).isEqualTo(1790)
        assertThat(
            parse(SyntheticShop.productPage(unitContent = "20 Stück", unitReference = null)).basePriceCentsPer100g,
        )
            .isNull()
    }

    @Test
    fun stockFromTheBuyBoxOnly() {
        assertThat(parse(SyntheticShop.productPage(stock = Stock.IN_STOCK)).available).isTrue()
        assertThat(parse(SyntheticShop.productPage(stock = Stock.OUT_OF_STOCK)).available).isFalse()
        // The cross-selling boxes have buy buttons and stock notes of their own; they don't count.
        assertThat(parse(SyntheticShop.productPage(stock = Stock.UNKNOWN, crossSellingBoxes = 3)).available).isNull()
    }

    @Test
    fun crossSellingBoxesNeverLeakIntoTheProduct() {
        val page = parse(
            SyntheticShop.productPage(title = null, sku = null, price = null, metaPrice = null, crossSellingBoxes = 4),
        )

        assertThat(page.name).isNull()
        assertThat(page.articleNumber).isNull()
        assertThat(page.priceCents).isNull()
    }

    @Test
    fun theBreadcrumbClassifiesTheProduct() {
        val cases = listOf(
            SyntheticShop.SENCHA_PATH to CatalogScope.GREEN,
            listOf("Tee" to "/tee/", "Grüner Tee" to "/tee/gruener-tee/", "China" to "/tee/gruener-tee/china/") to
                CatalogScope.GREEN,
            listOf("Tee" to "/tee/", "Japantee" to "/tee/japantee/", "Gyokuro" to "/tee/japantee/gyokuro/") to
                CatalogScope.GREEN,
            listOf("Tee" to "/tee/", "Japantee" to "/tee/japantee/", "Shincha" to "/tee/japantee/shincha-2026/") to
                CatalogScope.GREEN,
            listOf("Tee" to "/tee/", "Japantee" to "/tee/japantee/", "Wakoucha" to "/tee/japantee/wakoucha/") to
                CatalogScope.NOT_GREEN,
            listOf("Matcha" to "/matcha/", "Matcha Teepulver" to "/matcha/matcha-teepulver/") to CatalogScope.GREEN,
            listOf("Matcha" to "/matcha/", "Matcha Sets" to "/matcha/matcha-sets/") to CatalogScope.NOT_GREEN,
            listOf("Matcha" to "/matcha/", "Chawan" to "/matcha/matchaschalen-chawan/") to CatalogScope.NOT_GREEN,
            SyntheticShop.BLACK_TEA_PATH to CatalogScope.NOT_GREEN,
            SyntheticShop.TEAWARE_PATH to CatalogScope.NOT_GREEN,
        )
        cases.forEach { (path, scope) ->
            assertThat(parse(SyntheticShop.productPage(breadcrumb = path)).scope).isEqualTo(scope)
        }
    }

    @Test
    fun withoutABreadcrumbTheScopeIsUnknown() {
        assertThat(parse(SyntheticShop.productPage(breadcrumb = null)).scope).isEqualTo(CatalogScope.UNKNOWN)
        assertThat(parse(SyntheticShop.productPage(breadcrumb = emptyList())).scope).isEqualTo(CatalogScope.UNKNOWN)
    }

    @Test
    fun theSorteIsTheTypeLevelOfTheBreadcrumb() {
        val cases = listOf(
            SyntheticShop.SENCHA_PATH to "Sencha",
            listOf(
                "Tee" to "/tee/",
                "Japantee" to "/tee/japantee/",
                "Gyokuro" to "/tee/japantee/gyokuro/",
            ) to "Gyokuro",
            listOf("Matcha" to "/matcha/", "Matcha Teepulver" to "/matcha/matcha-teepulver/") to "Matcha Teepulver",
            listOf("Grüner Tee" to "/tee/gruener-tee/", "China" to "/tee/gruener-tee/china/") to null,
            listOf("Grüner Tee" to "/tee/gruener-tee/", "Bio" to "/tee/gruener-tee/bio-gruentee/") to null,
        )
        cases.forEach { (path, type) ->
            assertThat(parse(SyntheticShop.productPage(breadcrumb = path)).teaType).isEqualTo(type)
        }
    }

    @Test
    fun aPageWithoutAnyProductPartIsATypedFailure() {
        listOf(
            "",
            "<html><body><h1>Wartungsarbeiten</h1><p>Bitte später wiederkommen.</p></body></html>",
            "\u0089PNG\r\n\u001a\n binary",
            SyntheticShop.productPage(title = null, sku = null, breadcrumb = null, price = null, metaPrice = null),
        ).forEach { html ->
            assertThat(TeeKontorParser.parseProduct(html.toByteArray(), "$SHOP/x/XY0100"))
                .isEqualTo(ProductParse.Failed(ParseFailure.NOT_A_PRODUCT_PAGE))
        }
    }

    @Test
    fun theParserHasAVersion() {
        assertThat(TeeKontorParser.PARSER_VERSION).isAtLeast(1)
    }

    private companion object {
        const val SHOP = "https://shop.example"
    }
}
