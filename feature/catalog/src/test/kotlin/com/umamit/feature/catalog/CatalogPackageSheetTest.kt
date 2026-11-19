package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeExactPrices
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The add sheet where another screen writes the purchase (SCR-32's *Neuen Tee anlegen* „aus dem Katalog", PRF-08):
 * the usual sizes, price and date; *Aufnehmen* only hands the purchase back, the exact price is fetched once per
 * size, and the caller's saving and failure show. Invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogPackageSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val prices = FakeExactPrices()
    private val saved = mutableListOf<CatalogPurchase>()
    private var dismissals = 0
    private var failed by mutableStateOf(false)
    private var open by mutableStateOf(true)

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show() {
        val viewModel =
            CatalogPackageSheetViewModel(FakeCatalogRepository(details = listOf(SENCHA)), prices, FakeClock())
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                if (open) {
                    CatalogPackageSheet(
                        catalogKey = SENCHA.key,
                        saving = false,
                        failed = failed,
                        onSave = { saved += it },
                        onDismiss = { dismissals++ },
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun size(grams: String) =
        compose.onNode(isSelectable() and hasText(str(R.string.catalog_package_grams, grams), substring = true))

    @Test
    fun aufnehmenHandsBackThePurchaseAsEntered() {
        show()

        compose.onNode(isHeading() and hasText(str(R.string.catalog_add_title))).assertIsDisplayed()
        size("100").assertIsSelected()
        compose.onNodeWithTag(CATALOG_ADD_PRICE_TAG).assertTextContains("12,40")
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(saved).containsExactly(CatalogPurchase(SENCHA.key, 100.0, 1240, TODAY))
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun anEstimateIsFetchedOnceAndTheCallersFailureShows() {
        prices.prices[VARIANT_250] = 3010
        show()

        size("250").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CATALOG_ADD_PRICE_TAG).assertTextContains("30,10")
        assertThat(prices.requested).containsExactly(VARIANT_250)

        failed = true
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.catalog_add_failed)).assertExists()
    }

    @Test
    fun aNewOpeningStartsAfreshWithoutTheEarlierInput() {
        show()
        compose.onNodeWithTag(CATALOG_ADD_PRICE_TAG).performTextReplacement("9,99")
        compose.waitForIdle()

        open = false
        compose.waitForIdle()
        open = true
        compose.waitForIdle()

        compose.onNodeWithTag(CATALOG_ADD_PRICE_TAG).assertTextContains("12,40")
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(saved).containsExactly(CatalogPurchase(SENCHA.key, 100.0, 1240, TODAY))
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 22)
        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"
        const val VARIANT_250 = "$PRODUCT-250"

        val SENCHA = CatalogTeaDetail(
            key = "XY0100",
            name = "Morgennebel Sencha",
            productUrl = PRODUCT,
            teaType = "Sencha",
            basePriceCentsPer100g = 1240,
            available = true,
            packages = listOf(
                CatalogPackage("XY0100", "100 g", 100.0, 1240, false, null, PRODUCT, true),
                CatalogPackage("XY0100-250", "250 g · 3 % gespart", 250.0, 3007, true, 3, VARIANT_250, null),
            ),
        )
    }
}
