package com.umamit.feature.catalog

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewTableCell
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteScale
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeExactPrices
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.umamit.core.designsystem.R as DesignR

/**
 * A catalog tea's detail (SCR-21, CAT-11; #66) on fake data: the shop's fields as found, and
 * *Im Shop öffnen* handing the product page (or, for a tea in the collection, the page of the
 * size bought last, D-072) to the browser. Invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogTeaScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val catalog = FakeCatalogRepository(details = listOf(SENCHA))
    private var backs = 0

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(key: String = SENCHA.key) {
        val viewModel = CatalogTeaViewModel(
            SavedStateHandle(mapOf(CatalogTeaViewModel.KEY to key)),
            catalog,
            collection.stock,
            FakeAddFromCatalog(collection),
            FakeExactPrices(),
            FakeClock(),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogTeaRoute(onBack = { backs++ }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onNodeWithText(text, substring = substring).performScrollTo().assertIsDisplayed()

    private fun openShop(): Intent {
        compose.onNodeWithTag(CATALOG_TEA_OPEN_SHOP_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        return requireNotNull(shadowOf(compose.activity).nextStartedActivity) { "no activity started" }
    }

    @Test
    fun theDetailShowsTheShopsFieldsAsFound() {
        show()

        listOf(
            "Morgennebel Sencha", "朝霧", "Sencha", str(R.string.catalog_tea_organic),
            str(R.string.catalog_tea_cold_brew), str(R.string.catalog_price_per_100g, "12,40"),
            "Japan", "Uji", "Yabukita", "Frühjahr 2026", "20 Tage", "Chumushi",
            "naturbelassen", "grasig, süßlich", "Grüner Tee",
            str(R.string.catalog_tea_brew_kyusu), str(R.string.catalog_tea_brew_western), "~4g/100ml", "~65°C",
            "Zubereitung: 4 g auf 100 ml bei 60 °C.",
        ).forEach { shown(it) }
        // The description's HTML as formatted text.
        shown("Ein erfundener kräftiger Tee.", substring = true)
        compose.onNodeWithContentDescription(str(R.string.catalog_tea_scale_description, "Umami", 4))
            .performScrollTo()
            .assertIsDisplayed()
        // Not flavoured, in stock: no such tags.
        compose.onNodeWithText(str(R.string.catalog_tea_flavored)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.catalog_tea_out_of_stock)).assertDoesNotExist()
    }

    @Test
    fun thePackagesShowTheirPricesEstimatesAndThePriceOnAdding() {
        show()

        compose.onNodeWithContentDescription(str(R.string.catalog_tea_section_packages))
            .performScrollTo()
            .assertIsDisplayed()
        // Smallest first: a sample without a price, an out-of-stock size, the page's own size, an estimate.
        // A number and its unit are joined by non-breaking spaces ( ).
        listOf(
            "10 g" to "Preis beim Hinzufügen",
            "50 g" to "7,90 € · nicht vorrätig",
            "100 g" to "12,40 €",
            "250 g" to "≈ 30,07 € · 3 % gespart",
        ).forEach { (size, price) ->
            compose.onNode(hasText(size) and hasText(price)).performScrollTo().assertIsDisplayed()
        }
        shown(str(R.string.catalog_package_estimate_note))
    }

    @Test
    fun withoutEstimatesThereIsNoEstimateNote() {
        catalog.details.value = mapOf(
            SENCHA.key to SENCHA.copy(packages = SENCHA.packages.filterNot { it.priceIsEstimate }),
        )
        show()

        compose.onNodeWithText("12,40 €").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.catalog_package_estimate_note)).assertDoesNotExist()
    }

    @Test
    fun sectionsTheShopDoesntFillAreLeftOut() {
        catalog.details.value =
            mapOf("XY0300" to CatalogTeaDetail(key = "XY0300", name = "Schlichter Tee", productUrl = PRODUCT))
        show("XY0300")

        shown("Schlichter Tee")
        listOf(
            R.string.catalog_tea_section_profile,
            R.string.catalog_tea_section_taste,
            R.string.catalog_tea_section_description,
            R.string.catalog_tea_section_brewing,
            R.string.catalog_tea_section_packages,
        ).forEach { compose.onNodeWithContentDescription(str(it)).assertDoesNotExist() }
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        listOf(1, 2).forEach {
            compose.onNodeWithContentDescription(resources.getQuantityString(R.plurals.catalog_tea_section_recipes, it))
                .assertDoesNotExist()
        }
    }

    @Test
    fun theDerivedRecipesFollowTheShopsTableWithTheReasonToCheckThem() {
        catalog.details.value = mapOf(
            SENCHA.key to SENCHA.copy(recipes = RECIPES, recipeCheckReason = RecipeCheckReason.FROM_TEXT),
        )
        show()

        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        compose.onNodeWithContentDescription(resources.getQuantityString(R.plurals.catalog_tea_section_recipes, 3))
            .performScrollTo()
            .assertIsDisplayed()
        // „Rezept prüfen" with the reason (CAT-05).
        shown(str(DesignR.string.ds_check_recipe))
        shown(str(com.umamit.core.ui.R.string.ui_recipe_reason_from_text))
        // The default recipe, its values and steps.
        shown(str(R.string.catalog_tea_recipe_default))
        shown(str(R.string.catalog_tea_recipe_values, "4,0", 200, 65))
        shown("1:00 · 0:10 · 0:10")
        // The western recipe's second infusion is hotter.
        shown("1:00 · " + str(R.string.catalog_tea_recipe_step_temperature, "0:15", 70))
        // The cold brew, „kalt", with the source of its values as a note.
        shown("Kaltaufguss")
        shown(str(R.string.catalog_tea_recipe_values_cold, "8,0", 200))
        shown("25:00")
        shown(str(R.string.catalog_tea_recipe_source_text))
    }

    @Test
    fun aRecipeStraightFromTheTableNamesNoReason() {
        catalog.details.value = mapOf(SENCHA.key to SENCHA.copy(recipes = RECIPES.take(1)))
        show()

        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        compose.onNodeWithContentDescription(resources.getQuantityString(R.plurals.catalog_tea_section_recipes, 1))
            .performScrollTo()
            .assertIsDisplayed()
        shown(str(R.string.catalog_tea_recipe_values, "4,0", 200, 65))
        compose.onNodeWithText(str(DesignR.string.ds_check_recipe)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.catalog_tea_recipe_source_text)).assertDoesNotExist()
    }

    @Test
    fun aMatchaRecipeIsWhisked() {
        val usucha = RecipeTemplate(
            "Usucha",
            BrewMethod.MATCHA_USUCHA,
            1.0,
            100,
            70,
            listOf(RecipeStep(StepKind.WHISK, 20)),
            RecipeSource.CATALOG_TABLE,
            isDefault = true,
        )
        catalog.details.value = mapOf(SENCHA.key to SENCHA.copy(recipes = listOf(usucha)))
        show()

        shown(str(R.string.catalog_tea_recipe_values, "1,0", 100, 70))
        shown(str(R.string.catalog_tea_recipe_whisk, "0:20"))
    }

    @Test
    fun imShopOeffnenOpensTheProductPageInTheBrowser() {
        show()

        val intent = openShop()

        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data.toString()).isEqualTo(PRODUCT)
        assertThat(intent.categories).contains(Intent.CATEGORY_BROWSABLE)
    }

    @Test
    fun forATeaInTheCollectionItOpensTheSizeBoughtLast() {
        runBlocking {
            collection.teas.saveTea(
                Tea(
                    id = "own",
                    name = "Mein Sencha",
                    category = TeaCategory.GREEN,
                    form = TeaForm.LOOSE_LEAF,
                    catalogKey = SENCHA.key,
                ),
            )
            collection.stock.addEvent(event("a", InventoryEventType.INITIAL, 50.0, at = 1))
            collection.stock.addEvent(event("b", InventoryEventType.PURCHASE, 250.0, at = 2))
            collection.stock.addEvent(event("c", InventoryEventType.BREW, -4.0, at = 3))
        }
        show()

        assertThat(openShop().data.toString()).isEqualTo("$PRODUCT-250")
    }

    @Test
    fun aTeaGoneFromTheCatalogSaysSoAndBackLeaves() {
        show("XY9999")

        compose.onNodeWithText(str(R.string.catalog_tea_gone_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        assertThat(backs).isEqualTo(1)
    }

    private fun event(id: String, type: InventoryEventType, grams: Double, at: Long) =
        InventoryEvent(id = id, teaId = "own", type = type, deltaGrams = grams, occurredAtMillis = at)

    private companion object {
        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"

        /** Kyusu (default), Westlich and a cold brew from the prose, as 08 §2.5's examples derive them. */
        val RECIPES = listOf(
            RecipeTemplate(
                "Kyusu",
                BrewMethod.KYUSU,
                4.0,
                200,
                65,
                listOf(60, 10, 10).map { RecipeStep(StepKind.INFUSION, it) },
                RecipeSource.CATALOG_TEXT,
                isDefault = true,
            ),
            RecipeTemplate(
                "Westlich",
                BrewMethod.WESTERN,
                2.5,
                250,
                65,
                listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 15, tempC = 70)),
                RecipeSource.CATALOG_TABLE,
            ),
            RecipeTemplate(
                "Kaltaufguss",
                BrewMethod.COLD_BREW,
                8.0,
                200,
                5,
                listOf(RecipeStep(StepKind.INFUSION, 1500)),
                RecipeSource.CATALOG_TEXT,
            ),
        )

        val SENCHA = CatalogTeaDetail(
            key = "XY0100",
            name = "Morgennebel Sencha",
            secondaryName = "朝霧",
            productUrl = PRODUCT,
            teaType = "Sencha",
            originCountry = "Japan",
            originRegion = "Uji",
            cultivar = "Yabukita",
            harvest = "Frühjahr 2026",
            shading = "20 Tage",
            steaming = "Chumushi",
            isOrganic = true,
            coldBrewSuitable = true,
            aroma = "naturbelassen",
            tasteNotes = "grasig, süßlich",
            tasteScales = listOf(TasteScale("Umami", 4), TasteScale("Bitterstoffe", 1)),
            ingredients = "Grüner Tee",
            descriptionHtml = "<p>Ein erfundener <b>kräftiger</b> Tee.</p>",
            brewTable = listOf(
                BrewTableRow(
                    "japan",
                    listOf(BrewTableCell("Menge", "~4g/100ml"), BrewTableCell("Temperatur", "~60°C")),
                ),
                BrewTableRow(
                    "westlich",
                    listOf(BrewTableCell("Menge", "~10g/L"), BrewTableCell("Temperatur", "~65°C")),
                ),
            ),
            brewText = "Zubereitung: 4 g auf 100 ml bei 60 °C.",
            basePriceCentsPer100g = 1240,
            available = true,
            packages = listOf(
                CatalogPackage("XY0100-10", "10 g", 10.0, null, false, null, "$PRODUCT-10", null),
                CatalogPackage("XY0100-50", "50 g", 50.0, 790, false, null, "$PRODUCT-50", false),
                CatalogPackage("XY0100", "100 g", 100.0, 1240, false, null, PRODUCT, true),
                CatalogPackage("XY0100-250", "250 g · 3 % gespart", 250.0, 3007, true, 3, "$PRODUCT-250", null),
            ),
        )
    }
}
