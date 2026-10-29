package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** The shop's Kräutertees (CAT-01, 08 §2.4 item 3, §2.6, §3; D-093): classification, style and recipes. */
class HerbalTeaTest {
    private fun parse(html: String) =
        (TeeKontorParser.parseProduct(html.toByteArray(), "https://shop.example/x/XY0100") as ProductParse.Parsed).page

    private fun herbalPage(
        title: String = "Wiesenzauber – 100g",
        breadcrumb: List<Pair<String, String>> = SyntheticShop.HERBAL_PATH,
        brewRows: List<Pair<String, List<String>>> = listOf("westlich" to listOf("~12g/L", "100°C", "~8 Min")),
        badges: List<String> = emptyList(),
        description: String? = "<p>Ein erfundener Beschreibungstext für den Test.</p>",
    ) = parse(
        SyntheticShop.productPage(
            title = title,
            breadcrumb = breadcrumb,
            brewRows = brewRows,
            badges = badges,
            description = description,
        ),
    )

    @Test
    fun aProductOfTheKraeuterteeSectionIsHerbalWithItsSubSectionAsSorte() {
        val page = herbalPage()

        assertThat(page.scope).isEqualTo(CatalogScope.HERBAL)
        assertThat(page.teaType).isEqualTo("Wiesenmischungen")
    }

    @Test
    fun aKraeuterteeFiledInTheSectionItselfHasNoSorte() {
        val page = herbalPage(breadcrumb = SyntheticShop.HERBAL_PATH.take(2))

        assertThat(page.scope).isEqualTo(CatalogScope.HERBAL)
        assertThat(page.teaType).isNull()
    }

    @Test
    fun fruitTeaAndOtherSectionsStayOut() {
        assertThat(herbalPage(breadcrumb = SyntheticShop.FRUIT_TEA_PATH).scope).isEqualTo(CatalogScope.NOT_GREEN)
        assertThat(herbalPage(breadcrumb = SyntheticShop.BLACK_TEA_PATH).scope).isEqualTo(CatalogScope.NOT_GREEN)
    }

    @Test
    fun aSetOfKraeuterteesIsASet() {
        assertThat(herbalPage(title = "Kräuter Probierset").scope).isEqualTo(CatalogScope.SET)
    }

    @Test
    fun aGreenTeaKeepsItsScopeAndAJapanTeaNamingAHerbIsStillNoHerbalTea() {
        assertThat(herbalPage(breadcrumb = SyntheticShop.SENCHA_PATH).scope).isEqualTo(CatalogScope.GREEN)
        val japanHerb = herbalPage(
            title = "Kräuter aus dem Tal – 50g",
            breadcrumb = listOf(
                "Tee" to "/tee/",
                "Japantee" to "/tee/japantee/",
                "Teefamilien" to "/tee/japantee/teefamilien/",
            ),
        )
        assertThat(japanHerb.scope).isEqualTo(CatalogScope.NOT_GREEN)
    }

    @Test
    fun aKraeuterteeMapsToTheHerbalStyleUnflaggedWithoutUmami() {
        val result = CatalogRecipes.derive(herbalPage())

        assertThat(result.styleKey).isEqualTo("herbal")
        assertThat(result.form).isEqualTo(TeaForm.LOOSE_LEAF)
        assertThat(result.umamiLevel).isEqualTo(UmamiLevel.NONE)
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun withoutTheShopsValuesTheHerbalDefaultIsUsedAndFlagged() {
        val result = CatalogRecipes.derive(herbalPage(brewRows = emptyList()))

        assertThat(result.recipes).containsExactly(
            RecipeTemplate(
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 2.0,
                waterMl = 250,
                baseTempC = 100,
                steps = listOf(RecipeStep(StepKind.INFUSION, 360)),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun theShopsGridWinsWithItsOneInfusionAndNoFlag() {
        // ~12 g/L in the mug: 3,0 g / 250 ml; no second western infusion for a Kräutertee.
        val result = CatalogRecipes.derive(herbalPage())

        assertThat(result.recipes).containsExactly(
            RecipeTemplate(
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 3.0,
                waterMl = 250,
                baseTempC = 100,
                steps = listOf(RecipeStep(StepKind.INFUSION, 480)),
                source = RecipeSource.CATALOG_TABLE,
                isDefault = true,
            ),
        )
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun theShopsTextWinsOverTheDefaultAndFlagsTheTea() {
        val result = CatalogRecipes.derive(
            herbalPage(
                brewRows = emptyList(),
                description = "<p>Zubereitung: 4 g auf 300 ml, mit 95 °C aufgießen und 5 Minuten ziehen lassen.</p>",
            ),
        )

        val default = result.recipes.single()
        assertThat(default.leafGrams).isEqualTo(4.0)
        assertThat(default.waterMl).isEqualTo(300)
        assertThat(default.baseTempC).isEqualTo(95)
        assertThat(default.steps).containsExactly(RecipeStep(StepKind.INFUSION, 300))
        assertThat(default.source).isEqualTo(RecipeSource.CATALOG_TEXT)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_TEXT)
    }

    @Test
    fun aGridAndTextThatDisagreeKeepTheGridAndFlagTheConflict() {
        val result = CatalogRecipes.derive(
            herbalPage(description = "<p>Zubereitung: mit 70 °C aufgießen und 3 Minuten ziehen lassen.</p>"),
        )

        assertThat(result.recipes.single().baseTempC).isEqualTo(100)
        assertThat(result.recipes.single().steps).containsExactly(RecipeStep(StepKind.INFUSION, 480))
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.TABLE_TEXT_CONFLICT)
    }

    @Test
    fun theColdBrewBadgeAloneAddsNoColdBrewButTheShopsColdRowDoes() {
        val badgeOnly = CatalogRecipes.derive(herbalPage(badges = listOf("Coldbrew geeignet")))
        assertThat(badgeOnly.recipes.map { it.method }).containsExactly(BrewMethod.WESTERN)

        val coldRow = CatalogRecipes.derive(
            herbalPage(
                brewRows = listOf(
                    "westlich" to listOf("~12g/L", "100°C", "~8 Min"),
                    "westlich" to listOf("~15g/L", "kalt", "~6 Std"),
                ),
            ),
        )
        assertThat(coldRow.recipes.map { it.method })
            .containsExactly(BrewMethod.WESTERN, BrewMethod.COLD_BREW).inOrder()
        assertThat(coldRow.recipes.last().steps).containsExactly(RecipeStep(StepKind.INFUSION, 6 * 3600))
        assertThat(coldRow.checkReason).isNull()
    }
}
