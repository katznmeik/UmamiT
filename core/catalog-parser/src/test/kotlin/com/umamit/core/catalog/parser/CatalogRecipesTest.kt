package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** A parsed product page to its style and recipes (CAT-04, CAT-06), as the refresh stores them. */
class CatalogRecipesTest {
    private fun parse(html: String) =
        (TeeKontorParser.parseProduct(html.toByteArray(), "https://shop.example/x/XY0100") as ProductParse.Parsed).page

    @Test
    fun aPageGivesItsStyleUmamiLevelAndRecipes() {
        val page = parse(
            SyntheticShop.productPage(
                badges = listOf("Bio", "Coldbrew geeignet"),
                scales = listOf("Umami" to 3),
                brewRows = listOf(
                    "westlich" to listOf("~10g/L", "~70°C", "~1 Min", "-", "-"),
                    "japan" to listOf("~4g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek"),
                ),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("sencha")
        assertThat(result.form).isEqualTo(TeaForm.LOOSE_LEAF)
        assertThat(result.umamiLevel).isEqualTo(UmamiLevel.HIGH)
        assertThat(result.recipes.map { it.method })
            .containsExactly(BrewMethod.KYUSU, BrewMethod.WESTERN, BrewMethod.COLD_BREW).inOrder()
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aTeaFromJapanInAGenericSectionDefaultsToItsKyusuRow() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Wolkenspitze – 50g",
                breadcrumb = listOf("Tee" to "/tee/", "Grüner Tee" to "/tee/gruener-tee/"),
                properties = listOf("Anbaugebiete" to listOf("Japan")),
                brewRows = listOf(
                    "westlich" to listOf("~10g/L", "~80°C", "~2 Min", "-", "-"),
                    "japan" to listOf("~4g/200ml", "~70°C", "60 Sek", "10 Sek", "10 Sek"),
                ),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("green_generic")
        assertThat(result.recipes.first().method).isEqualTo(BrewMethod.KYUSU)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.UNMAPPED_STYLE)
    }

    @Test
    fun aTeaOnATeaFamilysPageTakesTheStyleItsNameNames() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Bergdorf Bancha – 100g",
                breadcrumb = listOf(
                    "Tee" to "/tee/",
                    "Japantee" to "/tee/japantee/",
                    "Teefamilien" to "/tee/japantee/teefamilien/",
                    "Familie Beispiel" to "/tee/japantee/teefamilien/familie-beispiel/",
                ),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.GREEN)
        assertThat(result.styleKey).isEqualTo("bancha")
        assertThat(result.recipes.first().method).isEqualTo(BrewMethod.KYUSU)
        assertThat(result.checkReason).isNull()
    }

    /**
     * Live sample 2 (structure only, D-089): cold-brew bags under Sencha, one grid row at „kalt",
     * a video pointer and the real instruction in the prose; 100 g of 20 bags weigh 5 g each.
     */
    @Test
    fun coldBrewBagsUnderSenchaBrewColdByTheirGridRow() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Kühle Brise Mizudashi – 20 Aufgussbeutel",
                unitContent = "0.1 kg",
                badges = listOf("Coldbrew geeignet"),
                description = "<p>Ein erfundener Tee für kalte Tage.</p>" +
                    "<p>In diesem Video zeigen wir, wie der Kaltaufguss in nur 1 Minute gelingt.</p>" +
                    "<p>Einen Beutel auf 0,3–0,5 Liter kaltes Wasser geben, kurz schütteln und zweimal aufgießen.</p>",
                brewRows = listOf("japan" to listOf("1Stk./500ml", "kalt", "~3 Min", "~2 Min", "-")),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("sencha")
        assertThat(result.form).isEqualTo(TeaForm.TEA_BAG)
        val default = result.recipes.first()
        assertThat(default.isDefault).isTrue()
        assertThat(default.method).isEqualTo(BrewMethod.COLD_BREW)
        assertThat(default.leafGrams).isEqualTo(5.0)
        assertThat(default.waterMl).isEqualTo(500)
        assertThat(default.steps.map { it.durationSeconds }).containsExactly(180, 120).inOrder()
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aKoreanTeaNoKeywordNamesIsAKoreanGreen() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Hügelgarten Frühling – 50g",
                breadcrumb = listOf(
                    "Grüner Tee" to "/tee/gruener-tee/",
                    "Andere" to "/tee/gruener-tee/andere-regionen/",
                ),
                properties = listOf("Anbaugebiete" to listOf("Südkorea")),
            ),
        )

        assertThat(CatalogRecipes.derive(page).styleKey).isEqualTo("korean_green")
    }
}
