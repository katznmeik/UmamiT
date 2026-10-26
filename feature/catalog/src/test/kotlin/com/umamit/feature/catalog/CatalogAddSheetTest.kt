package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeExactPrices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * *In Sammlung aufnehmen* / *Nachkauf erfassen* (SCR-23, CAT-12; #71) from a catalog tea's detail
 * on fakes: the sizes and their prices, the exact price replacing an estimate (fetched once per
 * size, never waited for), a custom amount, the repurchase of an owned tea and the sheet giving
 * way to an alert (rule 8). Invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogAddSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val catalog = FakeCatalogRepository(details = listOf(SENCHA))
    private val add = FakeAddFromCatalog(collection)
    private val prices = FakeExactPrices()
    private val alerting = mutableStateOf(false)
    private val added = mutableListOf<String>()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show() {
        val viewModel = CatalogTeaViewModel(
            SavedStateHandle(mapOf(CatalogTeaViewModel.KEY to SENCHA.key)),
            catalog,
            collection.stock,
            add,
            prices,
            FakeClock(),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogTeaRoute(onBack = {
                }, alerting = alerting.value, onPurchaseSave = { added += it }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun openSheet() {
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The radio row of the [grams] size. */
    private fun size(grams: String) =
        compose.onNode(isSelectable() and hasText(str(R.string.catalog_package_grams, grams), substring = true))

    private fun select(grams: String) {
        size(grams).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun price() = compose.onNodeWithTag(CATALOG_ADD_PRICE_TAG)

    private fun save() {
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun sheetShown() = compose.onNodeWithTag(CATALOG_ADD_SHEET_TAG)

    @Test
    fun addingRecordsTheChosenSizeItsPriceAndToday() {
        show()
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).assertTextContains(str(R.string.catalog_tea_add))

        openSheet()

        compose.onNode(isHeading() and hasText(str(R.string.catalog_add_title))).assertIsDisplayed()
        // Smallest first: the sample priced on adding, the shop's own size, an estimate, a custom amount.
        size("10").assertTextContains(str(R.string.catalog_package_price_on_add), substring = true)
        size("250").assertTextContains(str(R.string.catalog_package_estimate, "30,07"), substring = true)
        size("250").assertTextContains(str(R.string.catalog_package_savings, 3), substring = true)
        compose.onNodeWithText(str(R.string.catalog_add_custom)).assertIsDisplayed()
        // The size whose price the shop showed is chosen; its price is exact, so nothing is fetched.
        size("100").assertIsSelected()
        price().assertTextContains("12,40")
        compose.onNodeWithText(str(R.string.catalog_add_price_shop)).assertIsDisplayed()
        compose.onNodeWithText("22. Okt. 2026").assertIsDisplayed()
        assertThat(prices.requested).isEmpty()

        save()

        assertThat(add.purchases).containsExactly(CatalogPurchase(SENCHA.key, 100.0, 1240, TODAY))
        sheetShown().assertDoesNotExist()
        assertThat(added).containsExactly("tea-${SENCHA.key}")
        // Now in the collection: the action records a repurchase.
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).assertTextContains(str(R.string.catalog_tea_repurchase))
    }

    @Test
    fun anEstimateIsReplacedByTheExactPriceFetchedOncePerSize() {
        prices.prices[VARIANT_250] = 3010
        show()
        openSheet()

        select("250")
        select("100")
        select("250")

        price().assertTextContains("30,10")
        compose.onNodeWithText(str(R.string.catalog_add_price_shop)).assertIsDisplayed()
        size("250").assertTextContains(str(R.string.catalog_package_price, "30,10"), substring = true)
        assertThat(prices.requested).containsExactly(VARIANT_250)
        assertThat(prices.sessions).isEqualTo(1)
        save()
        assertThat(add.purchases.single().priceCents).isEqualTo(3010)
    }

    @Test
    fun offlineTheEstimateStaysEditableAndSavingNeverWaits() {
        show()
        openSheet()

        select("250")
        price().assertTextContains("30,07")
        compose.onNodeWithText(str(R.string.catalog_add_price_estimate)).assertIsDisplayed()

        // A sample's page doesn't answer either: no price, one can be entered.
        prices.gate = CompletableDeferred()
        select("10")
        compose.onNodeWithText(str(R.string.catalog_add_price_fetching)).assertIsDisplayed()
        save()

        assertThat(add.purchases).containsExactly(CatalogPurchase(SENCHA.key, 10.0, null, TODAY))
    }

    @Test
    fun aPriceTypedWhileFetchingIsKeptWhenTheExactOneArrives() {
        prices.prices[VARIANT_250] = 3010
        val gate = CompletableDeferred<Unit>()
        prices.gate = gate
        show()
        openSheet()

        select("250")
        price().performTextReplacement("29,50")
        gate.complete(Unit)
        compose.waitForIdle()

        price().assertTextContains("29,50")
        save()
        assertThat(add.purchases.single().priceCents).isEqualTo(2950)
    }

    @Test
    fun aCustomAmountIsRecordedWithTheTypedPrice() {
        show()
        openSheet()

        compose.onNodeWithText(str(R.string.catalog_add_custom)).performScrollTo().performClick()
        compose.waitForIdle()
        price().assertTextContains("")
        compose.onNodeWithTag(CATALOG_ADD_GRAMS_TAG).performTextReplacement("37,5")
        price().performTextReplacement("5")
        save()

        assertThat(add.purchases).containsExactly(CatalogPurchase(SENCHA.key, 37.5, 500, TODAY))
    }

    @Test
    fun anInvalidPriceCannotBeSaved() {
        show()
        openSheet()

        price().performTextReplacement("zwölf")
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.catalog_add_price_invalid)).assertIsDisplayed()
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).assertIsNotEnabled()
        assertThat(add.purchases).isEmpty()
    }

    @Test
    fun anOwnedTeaRecordsARepurchaseOfTheSizeBoughtLast() {
        runBlocking {
            collection.teas.saveTea(
                Tea(id = "own", name = "Mein Sencha", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF)
                    .copy(catalogKey = SENCHA.key),
            )
            collection.stock.addEvent(InventoryEvent("a", "own", InventoryEventType.INITIAL, 100.0, 1))
            collection.stock.addEvent(InventoryEvent("b", "own", InventoryEventType.PURCHASE, 250.0, 2))
        }
        show()
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).assertTextContains(str(R.string.catalog_tea_repurchase))

        openSheet()

        compose.onNode(isHeading() and hasText(str(R.string.catalog_add_title_repurchase))).assertIsDisplayed()
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).assertTextContains(str(R.string.catalog_add_save_repurchase))
        size("250").assertIsSelected()
        save()
        assertThat(add.purchases.single().grams).isEqualTo(250.0)
        assertThat(added).containsExactly("own")
        assertThat(collection.teas.let { runBlocking { it.getTea("tea-${SENCHA.key}") } }).isNull()
    }

    @Test
    fun aFailedSaveKeepsTheSheetOpenWithAMessage() {
        add.failNext = true
        show()
        openSheet()

        save()

        compose.onNodeWithText(str(R.string.catalog_add_failed)).assertIsDisplayed()
        sheetShown().assertIsDisplayed()
        save()
        assertThat(add.purchases).hasSize(1)
    }

    @Test
    fun whileItSavesTheSheetCannotBeDismissedAndItsOutcomeIsKept() {
        // A save the user dismisses would lose its message (D-090): the sheet stays until it ends.
        val gate = CompletableDeferred<Unit>()
        add.gate = gate
        add.failNext = true
        show()
        openSheet()
        save()

        Espresso.pressBack()
        compose.waitForIdle()
        sheetShown().assertIsDisplayed()

        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.catalog_add_failed)).assertIsDisplayed()
    }

    @Test
    fun aSaveThatFailsAfterAnAlertRemovedTheSheetBringsItBackWithItsMessageAfterTheAlert() {
        val gate = CompletableDeferred<Unit>()
        add.gate = gate
        add.failNext = true
        show()
        openSheet()
        save()

        alerting.value = true
        compose.waitForIdle()
        sheetShown().assertDoesNotExist()
        gate.complete(Unit)
        compose.waitForIdle()
        // Never over the alert (rule 8).
        sheetShown().assertDoesNotExist()

        alerting.value = false
        compose.waitForIdle()
        sheetShown().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.catalog_add_failed)).assertIsDisplayed()
        price().assertTextContains("12,40")
    }

    @Test
    fun aSaveThatSucceedsAfterAnAlertRemovedTheSheetIsRecordedAndReported() {
        val gate = CompletableDeferred<Unit>()
        add.gate = gate
        show()
        openSheet()
        save()

        alerting.value = true
        compose.waitForIdle()
        gate.complete(Unit)
        compose.waitForIdle()
        alerting.value = false
        compose.waitForIdle()

        assertThat(add.purchases).hasSize(1)
        assertThat(added).containsExactly("tea-${SENCHA.key}")
        sheetShown().assertDoesNotExist()
    }

    @Test
    fun anAlertRemovesTheSheetAndItStaysClosed() {
        show()
        openSheet()
        sheetShown().assertIsDisplayed()

        alerting.value = true
        compose.waitForIdle()
        sheetShown().assertDoesNotExist()
        // During the alert the action opens nothing.
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        sheetShown().assertDoesNotExist()

        alerting.value = false
        compose.waitForIdle()
        sheetShown().assertDoesNotExist()
        assertThat(add.purchases).isEmpty()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 22)
        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"
        const val VARIANT_10 = "$PRODUCT-10"
        const val VARIANT_250 = "$PRODUCT-250"

        val SENCHA = CatalogTeaDetail(
            key = "XY0100",
            name = "Morgennebel Sencha",
            productUrl = PRODUCT,
            teaType = "Sencha",
            basePriceCentsPer100g = 1240,
            available = true,
            packages = listOf(
                CatalogPackage("XY0100-10", "10 g", 10.0, null, false, null, VARIANT_10, null),
                CatalogPackage("XY0100", "100 g", 100.0, 1240, false, null, PRODUCT, true),
                CatalogPackage("XY0100-250", "250 g · 3 % gespart", 250.0, 3007, true, 3, VARIANT_250, null),
            ),
        )
    }
}
