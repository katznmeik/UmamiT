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
 * The shop's Rotbusch, Früchtetee and Chai (v0.5.0, #206; CAT-01, CAT-06, 08 §2.4 item 3, §2.6, §3; D-108):
 * scope, kind, style, category, *Sorte* and recipe source, on synthetic pages in the shop's structure (D-016).
 * The shop files a chai under its base tea's section (live check #206), so a chai is known by its name there.
 */
class RooibosFruitChaiTest {
    private fun page(
        title: String,
        breadcrumb: List<Pair<String, String>>,
        brewRows: List<Pair<String, List<String>>> = SyntheticShop.INFUSION_BREW_ROWS,
        ingredients: String? = null,
    ) = (
        TeeKontorParser.parseProduct(
            SyntheticShop.productPage(
                title = title,
                breadcrumb = breadcrumb,
                brewRows = brewRows,
                description = "<p>Ein erfundener Text.</p>" + ingredients?.let { "<p>Zutaten: $it</p>" }.orEmpty(),
            )
                .toByteArray(),
            "https://shop.example/x/XR0100",
        ) as ProductParse.Parsed
        ).page

    /** The shop's western row of ~10 g/L at 100 °C for ~5 min in the 250 ml mug. */
    private val shopWestern = RecipeTemplate(
        name = "Westlich",
        method = BrewMethod.WESTERN,
        leafGrams = 2.5,
        waterMl = 250,
        baseTempC = 100,
        steps = listOf(RecipeStep(StepKind.INFUSION, 300)),
        source = RecipeSource.CATALOG_TABLE,
        isDefault = true,
    )

    @Test
    fun aPlainRotbuschIsARooibosWithTheShopsWesternRow() {
        val page = page("Sonnenfeld Rotbusch pur – 100g", SyntheticShop.ROOIBOS_CLASSIC_PATH)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.isFlavored).isFalse()
        assertThat(page.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("rooibos")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.ROOIBOS)
        assertThat(result.category).isEqualTo(TeaCategory.ROOIBOS)
        // One infusion: the second western infusion is green-only.
        assertThat(result.recipes).containsExactly(shopWestern)
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aFlavouredRotbuschIsARooibosToo() {
        val page = page("Zitronenhain – 100g", SyntheticShop.ROOIBOS_FLAVOURED_PATH)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.isFlavored).isTrue()
        assertThat(result.styleKey).isEqualTo("rooibos")
        assertThat(result.category).isEqualTo(TeaCategory.ROOIBOS)
        assertThat(result.recipes).containsExactly(shopWestern)
    }

    @Test
    fun aGreenRotbuschOrAHoneybushIsItsSorte() {
        assertThat(page("Pfirsichtal – 100g", SyntheticShop.ROOIBOS_GREEN_PATH).teaType).isEqualTo("Grüner Rotbusch")
        // The shop files a plain green one under „klassisch", so its name says it.
        assertThat(page("Bio Grüner Rotbusch pur – 100g", SyntheticShop.ROOIBOS_CLASSIC_PATH).teaType)
            .isEqualTo("Grüner Rotbusch")
        val honeybush = page("Honeybush Kapwind – 100g", SyntheticShop.ROOIBOS_CLASSIC_PATH)
        assertThat(honeybush.teaType).isEqualTo("Honeybush")
        assertThat(CatalogRecipes.derive(honeybush).styleKey).isEqualTo("rooibos")
    }

    @Test
    fun aFruechteteeIsAFruitTea() {
        val page = page("Beerenwolke – 100g", SyntheticShop.FRUIT_CLASSIC_PATH)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("fruit")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.FRUIT)
        assertThat(result.category).isEqualTo(TeaCategory.FRUIT)
        assertThat(result.recipes).containsExactly(shopWestern)
        assertThat(result.checkReason).isNull()
        assertThat(CatalogRecipes.derive(page("Apfelgarten – 100g", SyntheticShop.FRUIT_TEA_PATH)).styleKey)
            .isEqualTo("fruit")
    }

    @Test
    fun aBlackBasedChaiIsAChaiOfCategoryBlack() {
        val page = page(
            "Schwarztee Gewürzmarkt Chai – 100g",
            SyntheticShop.BLACK_FLAVOURED_PATH,
            brewRows = listOf("westlich" to listOf("~10g/L", "100°C", "2-3 Min")),
        )
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Schwarztee")
        assertThat(result.styleKey).isEqualTo("chai")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.CHAI)
        assertThat(result.category).isEqualTo(TeaCategory.BLACK)
        assertThat(result.recipes).containsExactly(shopWestern.copy(steps = listOf(RecipeStep(StepKind.INFUSION, 150))))
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aRooibosChaiIsAChaiOfCategoryRooibos() {
        val page = page("Rotbusch Chai Savanne – 100g", SyntheticShop.ROOIBOS_FLAVOURED_PATH)
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isEqualTo("Rotbusch")
        assertThat(result.styleKey).isEqualTo("chai")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.CHAI)
        assertThat(result.category).isEqualTo(TeaCategory.ROOIBOS)
        assertThat(result.recipes).containsExactly(shopWestern)
    }

    @Test
    fun aChaiWithNoBaseNamedIsOfCategoryOther() {
        val page = page("Gewürzkarawane Chai – 100g", SyntheticShop.CHAI_PATH, ingredients = "Zimt, Ingwer, Kardamom")
        val result = CatalogRecipes.derive(page)

        assertThat(page.scope).isEqualTo(CatalogScope.TEA)
        assertThat(page.teaType).isNull()
        assertThat(result.styleKey).isEqualTo("chai")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.CHAI)
        assertThat(result.category).isEqualTo(TeaCategory.OTHER)
        assertThat(result.recipes).containsExactly(shopWestern)

        // Filed in the chai section, its ingredients name the base.
        val black = page("Karawane – 100g", SyntheticShop.CHAI_PATH, ingredients = "Schwarzer Tee, Zimt, Nelken")
        assertThat(CatalogRecipes.derive(black).styleKey).isEqualTo("chai")
        assertThat(CatalogRecipes.derive(black).category).isEqualTo(TeaCategory.BLACK)
    }

    @Test
    fun aGreenOrHerbalChaiKeepsItsSectionAsCategory() {
        val green = page(
            "Grüntee Gewürz Chai – 100g",
            listOf(
                "Tee" to "/tee/",
                "Grüner Tee" to "/tee/gruener-tee/",
                "Klassischer Grüntee" to "/tee/gruener-tee/klassischer-gruentee/",
            ),
        )
        assertThat(green.scope).isEqualTo(CatalogScope.GREEN)
        assertThat(green.teaType).isEqualTo("Grüntee")
        assertThat(CatalogRecipes.derive(green).styleKey).isEqualTo("chai")
        assertThat(CatalogRecipes.derive(green).category).isEqualTo(TeaCategory.GREEN)

        val herbal = page("Kräuter Chai Waldweg – 100g", SyntheticShop.HERBAL_PATH)
        assertThat(herbal.scope).isEqualTo(CatalogScope.HERBAL)
        assertThat(herbal.teaType).isEqualTo("Kräutertee")
        assertThat(CatalogRecipes.derive(herbal).styleKey).isEqualTo("chai")
        assertThat(CatalogRecipes.derive(herbal).category).isEqualTo(TeaCategory.HERBAL)

        // „Chai" as a word only.
        assertThat(CatalogRecipes.derive(page("Chaiyaphum – 100g", SyntheticShop.BLACK_CLASSIC_PATH)).styleKey)
            .isEqualTo("black_generic")
    }

    @Test
    fun withoutTheShopsValuesAChaiBrewsItsStyleDefaultFlaggedForCheck() {
        val result = CatalogRecipes.derive(
            page("Rotbusch Chai – 100g", SyntheticShop.ROOIBOS_FLAVOURED_PATH, emptyList()),
        )

        assertThat(result.recipes).containsExactly(BuiltInStyles.byKey("chai")!!.defaultRecipe)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun setsAndTeawareOfTheNewSectionsStayOut() {
        assertThat(page("Rotbusch Probierset", SyntheticShop.ROOIBOS_CLASSIC_PATH).scope).isEqualTo(CatalogScope.SET)
        assertThat(page("Chai 3er Set", SyntheticShop.CHAI_PATH).scope).isEqualTo(CatalogScope.SET)
        assertThat(page("Teesieb für Früchtetee", SyntheticShop.FRUIT_CLASSIC_PATH).scope)
            .isEqualTo(CatalogScope.NOT_GREEN)
    }

    /**
     * A Wakoucha on a Japan tea family's page (no type level, D-089): a black tea from v0.5.0, since the
     * catalog holds every tea under *Tee* (D-108 point 4); its *Sorte* is „Japan" like the Japan tree's
     * black teas'. An oolong there is an oolong; teaware stays out.
     */
    @Test
    fun aWakouchaOnAJapanTeaFamilysPageIsABlackTeaOfJapan() {
        val family = listOf(
            "Tee" to "/tee/",
            "Japantee" to "/tee/japantee/",
            "Teefamilien" to "/tee/japantee/teefamilien/",
            "Familie Beispiel" to "/tee/japantee/teefamilien/familie-beispiel/",
        )
        val wakoucha = page("Bergdorf Wakoucha – 50g", family, SyntheticShop.BLACK_BREW_ROWS)
        val result = CatalogRecipes.derive(wakoucha)

        assertThat(wakoucha.scope).isEqualTo(CatalogScope.TEA)
        assertThat(wakoucha.teaType).isEqualTo("Japan")
        assertThat(result.styleKey).isEqualTo("black_generic")
        assertThat(CatalogKind.of(result.styleKey)).isEqualTo(CatalogKind.BLACK)
        assertThat(result.category).isEqualTo(TeaCategory.BLACK)
        assertThat(result.checkReason).isNull()

        val oolong = page("Bergdorf Oolong – 50g", family, SyntheticShop.GONGFU_ROWS)
        assertThat(oolong.scope).isEqualTo(CatalogScope.TEA)
        assertThat(oolong.teaType).isEqualTo("Japan")
        assertThat(CatalogRecipes.derive(oolong).styleKey).isEqualTo("oolong_generic")

        assertThat(page("Bergdorf Kyusu für Wakoucha", family, emptyList()).scope).isEqualTo(CatalogScope.NOT_GREEN)
        // A green tea of the family stays green.
        assertThat(page("Bergdorf Sencha – 100g", family, SyntheticShop.BREW_ROWS).scope).isEqualTo(CatalogScope.GREEN)
    }

    @Test
    fun theNewSectionsAreParserV10() {
        assertThat(TeeKontorParser.PARSER_VERSION).isEqualTo(10)
        assertThat(TeeKontorParser.SCOPE_SINCE_VERSION).isEqualTo(10)
        assertThat(
            TeeKontorParser.kindsSince(9),
        ).containsExactly(CatalogKind.ROOIBOS, CatalogKind.FRUIT, CatalogKind.CHAI)
        assertThat(TeeKontorParser.kindsSince(10)).isEmpty()
    }
}
