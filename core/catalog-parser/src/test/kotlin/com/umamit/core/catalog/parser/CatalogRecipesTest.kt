package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
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

    /** Full live run 1 (counts only, D-091): Mizucha bags without any brewing data got hot Kyusu defaults. */
    @Test
    fun mizuchaBagsWithoutBrewingDataBrewColdByDefault() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Bio Talwind Mizucha – 16 Aufgussbeutel",
                unitContent = "0.08 kg",
                brewRows = emptyList(),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.form).isEqualTo(TeaForm.TEA_BAG)
        val default = result.recipes.first()
        assertThat(default.isDefault).isTrue()
        assertThat(default.method).isEqualTo(BrewMethod.COLD_BREW)
        assertThat(default.baseTempC).isEqualTo(5)
        assertThat(result.recipes.map { it.method }).contains(BrewMethod.KYUSU)
    }

    /** Full live run 1 (counts only, D-091): a hōjicha powder's Kyusu row gave it a leaf recipe. */
    @Test
    fun aHojichaPowderIsWhiskedNeverSteepedNorColdBrewed() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Bio Röstblatt Houjicha Pulver – 50g",
                breadcrumb = listOf(
                    "Grüner Tee" to "/tee/gruener-tee/",
                    "Japan" to "/tee/gruener-tee/japan/",
                    "Houjicha" to "/tee/gruener-tee/japan/houjicha/",
                ),
                badges = listOf("Coldbrew geeignet"),
                brewRows = listOf("japan" to listOf("~1g/100ml", "~80°C", "30 Sek", "30 Sek", "60 Sek")),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("hojicha_powder")
        assertThat(result.form).isEqualTo(TeaForm.POWDER)
        val usucha = result.recipes.single()
        assertThat(usucha.method).isEqualTo(BrewMethod.MATCHA_USUCHA)
        assertThat(usucha.leafGrams).isEqualTo(1.0)
        assertThat(usucha.waterMl).isEqualTo(100)
        assertThat(usucha.baseTempC).isEqualTo(80)
        assertThat(usucha.steps).containsExactly(RecipeStep(StepKind.WHISK, 20))
        assertThat(result.checkReason).isNull()

        // Without a grid, filed under another type: the powder style's own whisked recipe, flagged
        // as a style default.
        val bare = CatalogRecipes.derive(
            parse(SyntheticShop.productPage(title = "Röstblatt Hojicha Pulver", brewRows = emptyList())),
        )
        assertThat(bare.styleKey).isEqualTo("hojicha_powder")
        assertThat(bare.recipes.map { it.method }).containsExactly(BrewMethod.MATCHA_USUCHA)
        assertThat(bare.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
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
