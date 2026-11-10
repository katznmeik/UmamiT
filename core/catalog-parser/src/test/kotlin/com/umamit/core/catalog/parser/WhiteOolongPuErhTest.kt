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
import org.junit.Test

/**
 * The shop's white teas, oolongs and Pu-Erhs (v0.5.0, #205; CAT-01, CAT-06, 08 §2.4 item 3, §2.6, §3;
 * D-108): scope, kind, style, *Sorte* and recipe source, on synthetic pages in the shop's structure (D-016).
 */
class WhiteOolongPuErhTest {
    private fun page(
        title: String,
        breadcrumb: List<Pair<String, String>>,
        brewRows: List<Pair<String, List<String>>>,
        properties: List<Pair<String, List<String>>> = emptyList(),
    ) = (
        TeeKontorParser.parseProduct(
            SyntheticShop.productPage(
                title = title,
                breadcrumb = breadcrumb,
                brewRows = brewRows,
                properties = properties,
            )
                .toByteArray(),
            "https://shop.example/x/XW0100",
        ) as ProductParse.Parsed
        ).page

    private fun western(leaf: Double, tempC: Int, seconds: Int, isDefault: Boolean = true) = RecipeTemplate(
        name = "Westlich",
        method = BrewMethod.WESTERN,
        leafGrams = leaf,
        waterMl = 250,
        baseTempC = tempC,
        steps = listOf(RecipeStep(StepKind.INFUSION, seconds)),
        source = RecipeSource.CATALOG_TABLE,
        isDefault = isDefault,
    )

    private fun gongfu(leaf: Double, water: Int, tempC: Int, vararg seconds: Int, isDefault: Boolean = true) =
        RecipeTemplate(
            name = "Gongfu",
            method = BrewMethod.GONGFU,
            leafGrams = leaf,
            waterMl = water,
            baseTempC = tempC,
            steps = seconds.map { RecipeStep(StepKind.INFUSION, it) },
            source = RecipeSource.CATALOG_TABLE,
            isDefault = isDefault,
        )

    private fun categoryOf(styleKey: String) = BuiltInStyles.byKey(styleKey)!!.category

    @Test
    fun aFlavouredWhiteTeaIsFlavouredWhiteWithTheShopsWesternRow() {
        val page = page(
            title = "Mandelwolke – 100g",
            breadcrumb = SyntheticShop.WHITE_FLAVOURED_PATH,
            brewRows = listOf("westlich" to listOf("~10g/L", "80°C", "~2 Min")),
        )
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.isFlavored).isTrue()
        assertThat(page.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("flavored_white")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.WHITE)
        assertThat(categoryOf(result.styleKey)).isEqualTo(TeaCategory.WHITE)
        // One infusion: the second western infusion is green-only.
        assertThat(result.recipes).containsExactly(western(2.5, 80, 120))
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aClassicWhiteTeaTakesItsSorteFromItsNameAndKeepsBothOfTheShopsRows() {
        val page =
            page("Nebelgarten Bai Mu Dan – 100g", SyntheticShop.WHITE_CLASSIC_PATH, SyntheticShop.WHITE_BREW_ROWS)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Bai Mu Dan")
        assertThat(result.styleKey).isEqualTo("white_generic")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.WHITE)
        // The style's western default first, the shop's Gongfu row next.
        assertThat(result.recipes).containsExactly(
            western(2.5, 80, 120),
            gongfu(2.0, 150, 80, 90, 60, 45, isDefault = false),
        ).inOrder()
        assertThat(result.checkReason).isNull()

        val plain = page("Nebelgarten – 50g", SyntheticShop.WHITE_CLASSIC_PATH, SyntheticShop.WHITE_BREW_ROWS)
        assertThat(plain.teaType).isNull()
        assertThat(CatalogRecipes.derive(plain).styleKey).isEqualTo("white_generic")
    }

    @Test
    fun aTaiwanOolongWithAGongfuGridIsAnOolongOfItsSubSection() {
        val page = page(
            title = "Bergnebel Hochland Oolong – 50g",
            breadcrumb = SyntheticShop.OOLONG_TAIWAN_PATH,
            brewRows = listOf("china" to listOf("2-3g/150ml", "~95°C", "~45 Sek", "~20 Sek", "~20 Sek")),
        )
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Taiwan")
        assertThat(result.styleKey).isEqualTo("oolong_generic")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.OOLONG)
        assertThat(categoryOf(result.styleKey)).isEqualTo(TeaCategory.OOLONG)
        // The shop's only row is the default.
        assertThat(result.recipes).containsExactly(gongfu(2.5, 150, 95, 45, 20, 20))
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aClassicOrMastersOolongTakesItsSorteFromItsName() {
        val formosa = page("Formosa Morgentau – 50g", SyntheticShop.OOLONG_CLASSIC_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(formosa.scope).isEqualTo(CatalogScope.TEA)
        assertThat(formosa.teaType).isEqualTo("Taiwan")
        assertThat(CatalogRecipes.derive(formosa).styleKey).isEqualTo("oolong_generic")

        val master = page("Meisters China Felsentee – 25g", SyntheticShop.OOLONG_MASTER_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(master.scope).isEqualTo(CatalogScope.TEA)
        assertThat(master.teaType).isEqualTo("China")

        val unnamed = page("Meisters Abendlicht – 25g", SyntheticShop.OOLONG_MASTER_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(unnamed.teaType).isNull()
        assertThat(CatalogKind.of(CatalogRecipes.derive(unnamed).styleKey)).isEqualTo(CatalogKind.OOLONG)
    }

    @Test
    fun aFlavouredOolongIsFlavouredOolong() {
        val page = page(
            title = "Abendbrise Oolong – 100g",
            breadcrumb = SyntheticShop.OOLONG_FLAVOURED_PATH,
            brewRows = SyntheticShop.FLAVOURED_WESTERN_ROWS,
            properties = listOf("Aroma" to listOf("natürlich aromatisiert")),
        )
        val result = CatalogRecipes.derive(page)

        assertThat(page.isFlavored).isTrue()
        assertThat(result.styleKey).isEqualTo("flavored_oolong")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.OOLONG)
        assertThat(result.recipes).containsExactly(western(2.0, 95, 180))
    }

    @Test
    fun aShengCakeIsAShengPuErhWithTheShopsGongfuRow() {
        val page = page("2019 Nebelberg Bing Cha – 357g", SyntheticShop.SHENG_PATH, SyntheticShop.GONGFU_ROWS)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Sheng")
        assertThat(result.styleKey).isEqualTo("sheng")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.DARK)
        assertThat(categoryOf(result.styleKey)).isEqualTo(TeaCategory.DARK)
        // „~5-15 Sek" at its midpoint.
        assertThat(result.recipes).containsExactly(gongfu(5.0, 120, 100, 10, 10, 10))
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aShouOrAnAgedPuErhOrOneOfALooseOrMakersPageTakesItsSorte() {
        val shou = page("Waldboden – 100g", SyntheticShop.SHOU_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(shou.teaType).isEqualTo("Shou")
        assertThat(CatalogRecipes.derive(shou).styleKey).isEqualTo("shou")

        val aged = page("1999 Hafenlager – 50g", SyntheticShop.PU_ERH_AGED_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(aged.teaType).isEqualTo("Aged")
        assertThat(CatalogRecipes.derive(aged).styleKey).isEqualTo("dark_generic")

        val loose = page("Talgrund Shou Pu-Erh – 100g", SyntheticShop.PU_ERH_LOOSE_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(loose.scope).isEqualTo(CatalogScope.TEA)
        assertThat(loose.teaType).isEqualTo("Shou")
        assertThat(CatalogRecipes.derive(loose).styleKey).isEqualTo("shou")

        val maker = page("2016 Bergdorf Gushu Sheng – 200g", SyntheticShop.PU_ERH_MAKER_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(maker.teaType).isEqualTo("Sheng")
        assertThat(CatalogRecipes.derive(maker).styleKey).isEqualTo("sheng")

        // „Yesheng" (wild) is no Sheng.
        val wild = page("Yesheng Wildbaum – 100g", SyntheticShop.PU_ERH_LOOSE_PATH, SyntheticShop.GONGFU_ROWS)
        assertThat(wild.teaType).isNull()
        assertThat(CatalogRecipes.derive(wild).styleKey).isEqualTo("dark_generic")
    }

    @Test
    fun theJapanTreesOolongsAndWhiteTeasJoinTheirKinds() {
        val japan = { segment: String ->
            listOf("Tee" to "/tee/", "Japantee" to "/tee/japantee/", "Japan" to "/tee/japantee/$segment/")
        }
        val oolong = page("Bergdorf Oolong – 50g", japan("oolong"), SyntheticShop.GONGFU_ROWS)
        assertThat(oolong.scope).isEqualTo(CatalogScope.TEA)
        assertThat(oolong.teaType).isEqualTo("Japan")
        assertThat(CatalogKind.of(CatalogRecipes.derive(oolong).styleKey)).isEqualTo(CatalogKind.OOLONG)

        val white = page("Bergdorf Weißtee – 30g", japan("weisstee"), SyntheticShop.WHITE_BREW_ROWS)
        assertThat(white.scope).isEqualTo(CatalogScope.TEA)
        assertThat(white.teaType).isEqualTo("Japan")
        assertThat(CatalogKind.of(CatalogRecipes.derive(white).styleKey)).isEqualTo(CatalogKind.WHITE)
    }

    @Test
    fun setsAndTeawareOfTheNewSectionsStayOut() {
        assertThat(page("Nebelberg Sheng 3er Set", SyntheticShop.SHENG_PATH, SyntheticShop.GONGFU_ROWS).scope)
            .isEqualTo(CatalogScope.SET)
        assertThat(page("Oolong Probierset", SyntheticShop.OOLONG_CLASSIC_PATH, SyntheticShop.GONGFU_ROWS).scope)
            .isEqualTo(CatalogScope.SET)
        assertThat(page("Teedose für Weißtee", SyntheticShop.WHITE_CLASSIC_PATH, emptyList()).scope)
            .isEqualTo(CatalogScope.NOT_GREEN)
    }

    @Test
    fun withoutTheShopsValuesAShengBrewsItsStyleDefaultFlaggedForCheck() {
        val result = CatalogRecipes.derive(page("Bergdorf – 100g", SyntheticShop.SHENG_PATH, emptyList()))

        assertThat(result.recipes.first()).isEqualTo(BuiltInStyles.byKey("sheng")!!.defaultRecipe)
        assertThat(result.recipes.first().method).isEqualTo(BrewMethod.GONGFU)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun theNewSectionsAreAVersionStepOfTheirOwn() {
        // v9's kinds come after v8; #206's v10 adds its own (RooibosFruitChaiTest).
        val v10 = setOf(CatalogKind.ROOIBOS, CatalogKind.FRUIT, CatalogKind.CHAI)
        assertThat(
            TeeKontorParser.kindsSince(8) - v10,
        ).containsExactly(CatalogKind.WHITE, CatalogKind.OOLONG, CatalogKind.DARK)
        assertThat(TeeKontorParser.kindsSince(7) - v10)
            .containsExactly(CatalogKind.BLACK, CatalogKind.WHITE, CatalogKind.OOLONG, CatalogKind.DARK)
        assertThat(
            TeeKontorParser.kindsSince(9),
        ).containsNoneOf(CatalogKind.WHITE, CatalogKind.OOLONG, CatalogKind.DARK)
    }
}
