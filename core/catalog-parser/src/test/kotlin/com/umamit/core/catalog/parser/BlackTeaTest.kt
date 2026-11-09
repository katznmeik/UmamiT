package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import org.junit.Test

/**
 * The shop's black teas (v0.5.0, #203; CAT-01, CAT-06, 08 §2.4 item 3, §2.6, §3; D-108): scope, kind,
 * style, *Sorte* and recipe source, on synthetic pages in the shop's structure (D-016).
 */
class BlackTeaTest {
    private fun parse(html: String) =
        (TeeKontorParser.parseProduct(html.toByteArray(), "https://shop.example/x/XB0100") as ProductParse.Parsed).page

    private fun blackPage(
        title: String = "Nebelhang Darjeeling – 100g",
        breadcrumb: List<Pair<String, String>> = SyntheticShop.DARJEELING_PATH,
        brewRows: List<Pair<String, List<String>>> = SyntheticShop.BLACK_BREW_ROWS,
        properties: List<Pair<String, List<String>>> = emptyList(),
    ) = parse(
        SyntheticShop.productPage(title = title, breadcrumb = breadcrumb, brewRows = brewRows, properties = properties),
    )

    /** The shop's one western row (~10 g/L, 100 °C, 2–3 min) in the mug: 2,5 g / 250 ml, 2:30, one infusion. */
    private val fromGrid = RecipeTemplate(
        name = "Westlich",
        method = BrewMethod.WESTERN,
        leafGrams = 2.5,
        waterMl = 250,
        baseTempC = 100,
        steps = listOf(RecipeStep(StepKind.INFUSION, 150)),
        source = RecipeSource.CATALOG_TABLE,
        isDefault = true,
    )

    @Test
    fun aDarjeelingOfItsSubSectionIsABlackTeaWithItsSubSectionAsSorteAndTheShopsRecipe() {
        val page = blackPage()
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Darjeeling")
        assertThat(result.styleKey).isEqualTo("darjeeling")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.BLACK)
        assertThat(BuiltInStyles.byKey(result.styleKey)!!.category).isEqualTo(TeaCategory.BLACK)
        // The shop's grid wins, with its one infusion: the second western infusion is green-only.
        assertThat(result.recipes).containsExactly(fromGrid)
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aClassicBlackTeaTakesItsSorteAndStyleFromItsName() {
        val assam = blackPage(title = "Flussbiegung Assam TGFOP – 100g", breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH)
        val assamRecipes = CatalogRecipes.derive(assam)
        assertThat(assam.scope).isEqualTo(CatalogScope.TEA)
        assertThat(assam.teaType).isEqualTo("Assam")
        assertThat(assamRecipes.styleKey).isEqualTo("assam")
        assertThat(assamRecipes.recipes).containsExactly(fromGrid)

        val ceylon = blackPage(title = "Hochland Ceylon OP – 100g", breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH)
        assertThat(ceylon.teaType).isEqualTo("Ceylon")
        assertThat(CatalogRecipes.derive(ceylon).styleKey).isEqualTo("ceylon")

        // A name naming no region: no Sorte, the generic black style, not flagged.
        val plain = blackPage(title = "Hafenkontor Morgentee – 100g", breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH)
        val plainRecipes = CatalogRecipes.derive(plain)
        assertThat(plain.teaType).isNull()
        assertThat(plainRecipes.styleKey).isEqualTo("black_generic")
        assertThat(CatalogKind.of(plainRecipes.styleKey)).isEqualTo(CatalogKind.BLACK)
        assertThat(plainRecipes.recipes).containsExactly(fromGrid)
        assertThat(plainRecipes.checkReason).isNull()
    }

    @Test
    fun aFlavouredBlackTeaIsFlavouredBlackWithItsSorteLeftToItsName() {
        val page = blackPage(
            title = "Abendrot Bergamotte – 100g",
            breadcrumb = SyntheticShop.BLACK_FLAVOURED_PATH,
            properties = listOf("Aroma" to listOf("natürlich aromatisiert")),
        )
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.isFlavored).isTrue()
        assertThat(page.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("flavored_black")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.BLACK)
        assertThat(result.recipes).containsExactly(fromGrid)

        // The flavoured sub-section alone makes it flavoured; a region in its name is its Sorte.
        val darjeeling =
            blackPage(title = "Darjeeling mit Vanille – 100g", breadcrumb = SyntheticShop.BLACK_FLAVOURED_PATH)
        assertThat(darjeeling.isFlavored).isTrue()
        assertThat(darjeeling.teaType).isEqualTo("Darjeeling")
        assertThat(CatalogRecipes.derive(darjeeling).styleKey).isEqualTo("flavored_black")
    }

    @Test
    fun aBlendHasNoSorteUnlessItsNameNamesOne() {
        val blend = blackPage(title = "Nordwind Mischung – 250g", breadcrumb = SyntheticShop.BLACK_BLENDS_PATH)
        val result = CatalogRecipes.derive(blend)

        assertThat(blend.scope).isEqualTo(CatalogScope.TEA)
        assertThat(blend.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("black_generic")
        assertThat(result.recipes.single().source).isEqualTo(RecipeSource.CATALOG_TABLE)

        val named = blackPage(title = "Kontor Darjeeling Blend – 250g", breadcrumb = SyntheticShop.BLACK_BLENDS_PATH)
        assertThat(named.teaType).isEqualTo("Darjeeling")
        assertThat(CatalogRecipes.derive(named).styleKey).isEqualTo("darjeeling")
    }

    @Test
    fun aRegionSubSectionIsTheSorteAndTheJapanTreesBlackTeasAreBlackToo() {
        val india = blackPage(title = "Bergwiese – 100g", breadcrumb = SyntheticShop.BLACK_TEA_PATH)
        assertThat(india.scope).isEqualTo(CatalogScope.TEA)
        assertThat(india.teaType).isEqualTo("Indien")
        assertThat(CatalogRecipes.derive(india).styleKey).isEqualTo("black_generic")

        val koucha = blackPage(
            title = "Bergdorf Wakoucha – 50g",
            breadcrumb = listOf(
                "Tee" to "/tee/",
                "Japantee" to "/tee/japantee/",
                "Koucha / Schwarztee" to "/tee/japantee/koucha-schwarztee/",
            ),
        )
        assertThat(koucha.scope).isEqualTo(CatalogScope.TEA)
        assertThat(koucha.teaType).isEqualTo("Japan")
        assertThat(CatalogRecipes.derive(koucha).styleKey).isEqualTo("black_generic")
    }

    @Test
    fun aTeaBagOrMakersPageIsClassifiedByItsTeaSectionAndSkippedWithoutOne() {
        val bagsOnly = blackPage(title = "Kontor Assam 20 Aufgussbeutel", breadcrumb = SyntheticShop.TEA_BAGS_PATH)
        assertThat(bagsOnly.scope).isEqualTo(CatalogScope.NOT_GREEN)
        assertThat(blackPage(breadcrumb = SyntheticShop.MAKER_PATH).scope).isEqualTo(CatalogScope.NOT_GREEN)

        val withSection = blackPage(
            title = "Kontor Assam 20 Aufgussbeutel",
            breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH + SyntheticShop.TEA_BAGS_PATH.last(),
            brewRows = listOf("westlich" to listOf("1Stk/300ml", "100°C", "3 Min")),
        )
        val result = CatalogRecipes.derive(withSection)
        assertThat(withSection.scope).isEqualTo(CatalogScope.TEA)
        assertThat(withSection.teaType).isEqualTo("Assam")
        assertThat(result.styleKey).isEqualTo("assam")
        assertThat(result.form).isEqualTo(TeaForm.TEA_BAG)
        assertThat(result.recipes.single().source).isEqualTo(RecipeSource.CATALOG_TABLE)
    }

    @Test
    fun blackSetsAndTeawareStayOut() {
        assertThat(blackPage(title = "Darjeeling Probierset 3x50g").scope).isEqualTo(CatalogScope.SET)
        assertThat(blackPage(title = "Assam 3er Set", breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH).scope)
            .isEqualTo(CatalogScope.SET)
        val setsSection = listOf(
            "Tee" to "/tee/",
            "Multipacks & Sets" to "/tee/multipacks-sets-zum-probieren/",
            "Schwarztee-Sets" to "/tee/multipacks-sets-zum-probieren/schwarztee-sets/",
        )
        assertThat(blackPage(title = "Darjeeling Reise", breadcrumb = setsSection).scope)
            .isEqualTo(CatalogScope.NOT_GREEN)
        assertThat(blackPage(title = "Teedose für Schwarztee", breadcrumb = SyntheticShop.BLACK_CLASSIC_PATH).scope)
            .isEqualTo(CatalogScope.NOT_GREEN)
    }

    @Test
    fun withoutTheShopsValuesTheStyleDefaultIsUsedAndTheTeaFlaggedForCheck() {
        val result = CatalogRecipes.derive(blackPage(brewRows = emptyList()))

        assertThat(result.recipes).containsExactly(
            BuiltInStyles.byKey("darjeeling")!!.defaultRecipe,
        )
        assertThat(result.recipes.single().source).isEqualTo(RecipeSource.STYLE_DEFAULT)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun theBlackSectionIsStillToComeForACatalogOfAnOlderParser() {
        assertThat(TeeKontorParser.PARSER_VERSION).isEqualTo(8)
        assertThat(TeeKontorParser.SCOPE_SINCE_VERSION).isEqualTo(TeeKontorParser.PARSER_VERSION)
        assertThat(TeeKontorParser.kindsSince(7)).containsExactly(CatalogKind.BLACK)
        assertThat(TeeKontorParser.kindsSince(6)).containsExactly(CatalogKind.HERBAL, CatalogKind.BLACK)
        assertThat(TeeKontorParser.kindsSince(8)).isEmpty()
    }
}
