package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
        val page = parse(SyntheticShop.productPage(baseUrl = SHOP, brewRows = emptyList()))

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
                sizeOptions = listOf(SizeOption(grams = 100.0, savingsPercent = null, selected = true)),
                photoUrl = "$SHOP${SyntheticShop.photoPath()}",
                descriptionHtml = "<p>Ein erfundener Beschreibungstext für den Test.</p>",
                descriptionText = "Ein erfundener Beschreibungstext für den Test.",
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

    /**
     * Live sample 2 (structure only, D-089): a product whose only breadcrumb is a tea family's page
     * under the Japan tree has no type level; its name (or its steaming or shading badge) decides.
     */
    @Test
    fun aJapanTeaWithoutTypeLevelIsGreenWhenItsNameOrBadgesSayGreen() {
        val family = listOf(
            "Tee" to "/tee/",
            "Japantee" to "/tee/japantee/",
            "Teefamilien" to "/tee/japantee/teefamilien/",
            "Familie Beispiel" to "/tee/japantee/teefamilien/familie-beispiel/",
        )
        val garden = listOf("Tee" to "/tee/", "Teegarten" to "/tee/japantee/teegarten-erfunden/")
        data class Case(
            val path: List<Pair<String, String>>,
            val title: String,
            val scope: CatalogScope,
            val type: String? = null,
            val badges: List<String> = emptyList(),
        )
        listOf(
            Case(family, "Bergdorf Bancha – 100g", CatalogScope.GREEN, "Bancha"),
            Case(family, "Bergdorf Houjicha – 100g", CatalogScope.GREEN, "Houjicha"),
            Case(family, "Bergdorf Gyokuro – 50g", CatalogScope.GREEN, "Gyokuro"),
            Case(family, "Bergdorf Kabuse – 50g", CatalogScope.GREEN, "Kabusecha"),
            Case(family, "Bergdorf Fukamushi – 100g", CatalogScope.GREEN, "Sencha"),
            Case(family, "Bergdorf Gyokuro Karigane – 100g", CatalogScope.GREEN, "Kukicha"),
            Case(garden, "Talblick Sencha – 100g", CatalogScope.GREEN, "Sencha"),
            // No type in the name, but a steaming badge: a green tea of unknown type.
            Case(family, "Bergdorf Frühling – 100g", CatalogScope.GREEN, badges = listOf("Dämpfung: Chumushi")),
            // Nothing says green, or the name says another tea or teaware: skipped.
            Case(family, "Bergdorf Frühling – 100g", CatalogScope.NOT_GREEN),
            Case(family, "Bergdorf Wakoucha – 50g", CatalogScope.NOT_GREEN),
            Case(family, "Bergdorf Benifuuki Koucha – 50g", CatalogScope.NOT_GREEN),
            Case(family, "Bergdorf Oolong – 50g", CatalogScope.NOT_GREEN),
            Case(family, "Bergdorf Kyusu für Sencha", CatalogScope.NOT_GREEN),
        ).forEach { case ->
            val page = parse(
                SyntheticShop.productPage(breadcrumb = case.path, title = case.title, invertedBadges = case.badges),
            )
            val check = assertWithMessage(case.title)
            check.that(page.scope).isEqualTo(case.scope)
            check.that(page.teaType).isEqualTo(case.type)
        }
    }

    /**
     * Full live run 1 (article numbers only, D-091): a set without a set number („…SET") got into
     * the catalog. A product of the green sections is a set by its name or its breadcrumb.
     */
    @Test
    fun aSetIsASetByItsNameOrBreadcrumbNeverAGreenTea() {
        val sets = listOf(
            "Tee" to "/tee/",
            "Grüner Tee" to "/tee/gruener-tee/",
            "Grüntee-Sets" to "/tee/gruener-tee/gruentee-sets/",
        )
        val matcha = listOf("Matcha" to "/matcha/", "Matcha Teepulver" to "/matcha/matcha-teepulver/")
        listOf(
            SyntheticShop.SENCHA_PATH to "Bio Grüntee Wanderung 3er Set – 3x50g",
            SyntheticShop.SENCHA_PATH to "Bio Grüntee Wanderung 3er-Set – 3x50g",
            SyntheticShop.SENCHA_PATH to "Morgennebel Sencha Probierset",
            SyntheticShop.SENCHA_PATH to "Tautropfen Geschenkset",
            SyntheticShop.SENCHA_PATH to "Morgennebel Jubiläumsset – 2x30g",
            SyntheticShop.SENCHA_PATH to "Morgennebel SET",
            matcha to "Grüne Wolke Komplett-Set mit Besen",
            sets to "Morgennebel Trio – 3x50g",
        ).forEach { (path, title) ->
            val page = parse(SyntheticShop.productPage(breadcrumb = path, title = title))
            assertWithMessage(title).that(page.scope).isEqualTo(CatalogScope.SET)
        }
        // Words that only contain „set" are no sets.
        listOf("Setouchi Sencha – 100g", "Sunset Sencha – 100g", "Morgennebel Sencha Settai – 50g").forEach { title ->
            val page = parse(SyntheticShop.productPage(title = title))
            assertWithMessage(title).that(page.scope).isEqualTo(CatalogScope.GREEN)
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
