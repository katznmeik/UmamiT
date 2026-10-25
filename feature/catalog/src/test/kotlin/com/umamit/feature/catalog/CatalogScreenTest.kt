package com.umamit.feature.catalog

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.BlockCause
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.catalogTea
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sammlung › Katalog (SCR-19) and its refresh sheet (SCR-22) on fake data (#64): the empty state
 * leads through the sheet to the list, *Abbrechen*, every error message with *Details kopieren*,
 * and alerts first (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeCatalogRepository()
    private val runner = FakeCatalogRefreshRunner()
    private var alerting by mutableStateOf(false)

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show() {
        val viewModel = CatalogViewModel(repository, runner)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = alerting, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun set(state: CatalogRefreshState) {
        runner.state.value = state
        compose.waitForIdle()
    }

    private fun sheet() = compose.onNodeWithTag(CATALOG_REFRESH_SHEET_TAG)

    @Test
    fun theEmptyStateLeadsThroughTheSheetToTheList() {
        show()
        compose.onNodeWithText(str(R.string.catalog_empty_title)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.catalog_load)).performClick()
        set(CatalogRefreshState.Running(CatalogRefreshProgress.STARTING))
        assertThat(runner.starts).isEqualTo(1)
        compose.onNodeWithText(str(R.string.catalog_refresh_starting)).assertIsDisplayed()

        set(CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = 2, pagesTotal = 4, products = 1)))
        compose.onNodeWithText(str(R.string.catalog_refresh_progress, 2, 4, 1)).assertIsDisplayed()

        repository.catalog.value = CatalogOverview(
            teas = listOf(
                catalogTea("XY0100", "Morgennebel Sencha"),
                catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro", 3600),
            ),
            lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS,
        )
        set(CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2)))
        compose.onNodeWithText(str(R.string.catalog_refresh_success_title)).assertIsDisplayed()
        compose.onNodeWithText("2 Grüntees · 2 neu").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.catalog_refresh_done)).performClick()
        compose.waitForIdle()

        assertThat(runner.acknowledgements).isEqualTo(1)
        sheet().assertDoesNotExist()
        compose.onNodeWithText("Tee-Kontor Kiel · 2 Grüntees · aktualisiert am 22. Okt.").assertIsDisplayed()
        compose.onNodeWithText("Morgennebel Sencha").assertIsDisplayed()
        compose.onNodeWithText("Sencha · 12,40 €/100 g").assertIsDisplayed()
        compose.onNodeWithText("Gyokuro · 36,00 €/100 g").assertIsDisplayed()
    }

    @Test
    fun abbrechenCancelsTheRunningRefresh() {
        show()
        compose.onNodeWithText(str(R.string.catalog_load)).performClick()
        set(CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = 1, pagesTotal = 3, products = 0)))

        compose.onNodeWithText(str(R.string.catalog_refresh_cancel)).performScrollTo().performClick()

        assertThat(runner.cancels).isEqualTo(1)
        set(CatalogRefreshState.Finished(CatalogRefreshOutcome.Cancelled))
        compose.onNodeWithText(str(R.string.catalog_refresh_cancelled_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.catalog_refresh_kept)).assertIsDisplayed()
    }

    @Test
    fun eachFailureShowsItsMessageAndCopiesItsDetails() {
        val failures = mapOf(
            CatalogRefreshOutcome.Blocked(BlockCause.CHALLENGE, "details: challenge") to
                R.string.catalog_refresh_blocked,
            CatalogRefreshOutcome.Blocked(BlockCause.TOO_MANY_REQUESTS, "details: 429") to
                R.string.catalog_refresh_blocked,
            CatalogRefreshOutcome.Offline("details: offline") to R.string.catalog_refresh_offline,
            CatalogRefreshOutcome.ShopUnavailable("details: 503") to R.string.catalog_refresh_unavailable,
            CatalogRefreshOutcome.ShopChanged("details: shop changed") to R.string.catalog_refresh_shop_changed,
            CatalogRefreshOutcome.StorageFailed("details: storage") to R.string.catalog_refresh_storage,
        )
        show()
        for ((failure, message) in failures) {
            compose.onNodeWithText(str(R.string.catalog_load)).performClick()
            set(CatalogRefreshState.Finished(failure))

            compose.onNodeWithText(str(R.string.catalog_refresh_failed_title)).assertIsDisplayed()
            compose.onNodeWithText(str(message)).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.catalog_refresh_copy_details)).performScrollTo().performClick()
            compose.waitForIdle()
            assertThat(clipboardText()).isEqualTo(failure.details)
            compose.onNodeWithText(str(R.string.catalog_refresh_close)).performScrollTo().performClick()
            compose.waitForIdle()
            sheet().assertDoesNotExist()
            compose.onNodeWithText(str(R.string.catalog_empty_title)).assertIsDisplayed()
        }
        assertThat(runner.starts).isEqualTo(failures.size)
    }

    @Test
    fun anAlertRemovesTheSheetAndTheRefreshGoesOn() {
        show()
        compose.onNodeWithText(str(R.string.catalog_load)).performClick()
        set(CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = 1, pagesTotal = 3, products = 0)))
        sheet().assertIsDisplayed()

        alerting = true
        compose.waitForIdle()
        sheet().assertDoesNotExist()
        alerting = false
        compose.waitForIdle()

        // The sheet stays closed; the running refresh can be shown again.
        sheet().assertDoesNotExist()
        assertThat(runner.cancels).isEqualTo(0)
        compose.onNodeWithText(str(R.string.catalog_show_progress)).performClick()
        compose.waitForIdle()
        sheet().assertIsDisplayed()
        assertThat(runner.starts).isEqualTo(1)
    }

    @Test
    fun theSheetOnlyOpensOnATap() {
        // A refresh's state from earlier (e.g. a result nobody saw) never opens the sheet by itself (CAT-07 AC7).
        runner.state.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Offline("x"))
        show()

        sheet().assertDoesNotExist()
        assertThat(runner.starts).isEqualTo(0)
    }

    private fun clipboardText(): String? = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
}
