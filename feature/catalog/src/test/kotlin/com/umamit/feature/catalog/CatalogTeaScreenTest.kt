package com.umamit.feature.catalog

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
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
import com.umamit.core.model.BrewTableCell
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.TasteScale
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
            collection.teas,
            collection.stock,
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
        ).forEach { compose.onNodeWithContentDescription(str(it)).assertDoesNotExist() }
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
                CatalogPackage("XY0100-50", "50 g", 50.0, 790, false, null, "$PRODUCT-50", true),
                CatalogPackage("XY0100-250", "250 g", 250.0, null, true, 3, "$PRODUCT-250", true),
            ),
        )
    }
}
