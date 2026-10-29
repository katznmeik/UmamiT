package com.umamit.feature.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeTeaLinkLoader
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * A tea from a shop link in the catalog (v0.2.1, #98; CAT-17, SCR-19, D-094; spec #81 seam 5):
 * *Link einfügen* with a valid and an invalid link, the clipboard, a shared link, and alerts
 * first (rule 8). The shop is faked; invented numbers (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaLinkSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val links = FakeTeaLinkLoader()
    private val runner = FakeCatalogRefreshRunner()
    private var alerting by mutableStateOf(false)
    private var pasteRequest by mutableIntStateOf(0)
    private var shared by mutableStateOf<TeaLinkShare?>(null)
    private val taken = mutableListOf<Long>()
    private val opened = mutableListOf<String>()

    private fun str(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show() {
        val viewModel = CatalogViewModel(FakeCatalogRepository(), runner, links = links)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(
                    alerting = alerting,
                    onOpenTea = { opened += it },
                    pasteLinkRequest = pasteRequest,
                    sharedLink = shared,
                    onTakeSharedLink = { taken += it },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun openPasteSheet() {
        pasteRequest++
        compose.waitForIdle()
    }

    private fun sheet() = compose.onNodeWithTag(TEA_LINK_SHEET_TAG)

    private fun field() = compose.onNodeWithTag(TEA_LINK_FIELD_TAG)

    private fun clipboard(text: String) =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Link", text))

    @Test
    fun aValidLinkOpensTheTeasDetail() {
        links.outcome = { TeaLinkOutcome.Opened(KEY) }
        show()
        openPasteSheet()
        sheet().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.tea_link_title)).assertIsDisplayed()

        field().performTextInput(LINK)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()

        assertThat(links.loaded).containsExactly(LINK)
        assertThat(opened).containsExactly(KEY)
        sheet().assertDoesNotExist()
    }

    @Test
    fun aTeaInTheCatalogThatItsPageCouldNotRefreshOpensWithTheMessage() {
        // CAT-17 AC6 (D-096): offline, no tea page or an unreadable page: the detail opens anyway, with the reason.
        val reasons = mapOf(
            TeaLinkOutcome.Offline to str(R.string.catalog_refresh_offline),
            TeaLinkOutcome.NoTeaPage to str(R.string.tea_link_no_tea_page),
            TeaLinkOutcome.Unreadable to str(R.string.tea_link_unreadable),
        )
        show()
        reasons.forEach { (reason, message) ->
            links.outcome = { TeaLinkOutcome.Opened(KEY, notRefreshed = reason) }
            opened.clear()
            ShadowToast.reset()
            openPasteSheet()
            field().performTextReplacement(LINK)
            compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
            compose.waitForIdle()

            assertThat(opened).containsExactly(KEY)
            sheet().assertDoesNotExist()
            assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(message)
        }
    }

    @Test
    fun aTeaThatItsPageRefreshedOpensWithoutAMessage() {
        links.outcome = { TeaLinkOutcome.Opened(KEY) }
        show()
        openPasteSheet()
        field().performTextInput(LINK)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()

        assertThat(opened).containsExactly(KEY)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun anInvalidLinkSaysSoAndTheSheetStays() {
        links.outcome = { TeaLinkOutcome.NotATeaLink }
        show()
        openPasteSheet()

        field().performTextInput("https://www.example.com/tee")
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.tea_link_not_a_tea_link)).assertIsDisplayed()
        sheet().assertIsDisplayed()
        assertThat(opened).isEmpty()

        // Editing the link clears the message.
        field().performTextReplacement(LINK)
        compose.onNodeWithText(str(R.string.tea_link_not_a_tea_link)).assertDoesNotExist()
    }

    @Test
    fun eachOutcomeShowsItsMessage() {
        show()
        val messages = mapOf(
            TeaLinkOutcome.NotInCatalog to str(R.string.tea_link_not_in_catalog),
            TeaLinkOutcome.RefreshRunning to str(R.string.tea_link_refresh_running),
            TeaLinkOutcome.NoTeaPage to str(R.string.tea_link_no_tea_page),
            TeaLinkOutcome.Unreadable to str(R.string.tea_link_unreadable),
            TeaLinkOutcome.Offline to str(R.string.catalog_refresh_offline),
            TeaLinkOutcome.Blocked to str(R.string.catalog_refresh_blocked),
            TeaLinkOutcome.ShopUnavailable to str(R.string.catalog_refresh_unavailable),
            TeaLinkOutcome.StorageFailed to str(R.string.catalog_refresh_storage),
        )
        openPasteSheet()
        field().performTextInput(LINK)
        messages.forEach { (outcome, message) ->
            links.outcome = { outcome }
            compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
            compose.waitForIdle()

            compose.onNodeWithTag(TEA_LINK_MESSAGE_TAG, useUnmergedTree = true).assertTextContains(message)
        }
        assertThat(opened).isEmpty()
    }

    @Test
    fun whileItLoadsTheSheetSaysSoAndTheButtonWaits() {
        links.outcome = { TeaLinkOutcome.Opened(KEY) }
        links.hold = CompletableDeferred()
        show()
        openPasteSheet()
        field().performTextInput(LINK)

        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.tea_link_loading)).assertIsDisplayed()
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).assertIsNotEnabled()
        links.hold?.complete(Unit)
        compose.waitForIdle()
        assertThat(opened).containsExactly(KEY)
    }

    @Test
    fun theFieldIsFilledFromTheClipboardWhenItHoldsATeaLink() {
        clipboard(LINK)
        show()

        openPasteSheet()

        field().assertTextContains(LINK)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).assertIsEnabled()
    }

    @Test
    fun anythingElseOnTheClipboardStaysThere() {
        // Not a tea link: the field stays empty, so nothing private from the clipboard shows.
        clipboard("Meine Notiz zum Tee")
        show()

        openPasteSheet()

        compose.onNodeWithText("Meine Notiz zum Tee", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).assertIsNotEnabled()
    }

    @Test
    fun aSharedLinkLoadsWithoutTheFieldAndOpensTheDetail() {
        links.outcome = { TeaLinkOutcome.Opened(KEY) }
        show()

        shared = TeaLinkShare(token = 7, text = "Nebelhang Sencha $LINK")
        compose.waitForIdle()

        assertThat(links.loaded).containsExactly("Nebelhang Sencha $LINK")
        assertThat(taken).containsExactly(7L)
        assertThat(opened).containsExactly(KEY)
        field().assertDoesNotExist()
    }

    @Test
    fun aSharedLinkThatFailsShowsTheMessageInASmallSheet() {
        links.outcome = { TeaLinkOutcome.NotATeaLink }
        show()

        shared = TeaLinkShare(token = 8, text = "https://www.example.com/tee")
        compose.waitForIdle()

        sheet().assertIsDisplayed()
        field().assertDoesNotExist()
        compose.onNodeWithText(str(R.string.tea_link_not_a_tea_link)).assertIsDisplayed()
        compose.onNodeWithTag(TEA_LINK_CLOSE_TAG).performClick()
        compose.waitForIdle()
        sheet().assertDoesNotExist()
        assertThat(opened).isEmpty()
    }

    @Test
    fun aSharedLinkIsLoadedOnceEvenWhenRecomposed() {
        links.outcome = { TeaLinkOutcome.NoTeaPage }
        show()
        val share = TeaLinkShare(token = 9, text = LINK)

        shared = share
        compose.waitForIdle()
        shared = null
        compose.waitForIdle()
        shared = share
        compose.waitForIdle()

        assertThat(links.loaded).containsExactly(LINK)
    }

    @Test
    fun pasteSheetClosesARefreshSheetItWouldCover() {
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
        show()

        openPasteSheet()

        compose.onNodeWithTag(CATALOG_REFRESH_SHEET_TAG).assertDoesNotExist()
        sheet().assertIsDisplayed()
    }

    @Test
    fun anAlertRemovesTheSheetAndItComesBackAfterwards() {
        links.outcome = { TeaLinkOutcome.Offline }
        show()
        openPasteSheet()
        field().performTextInput(LINK)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()

        alerting = true
        compose.waitForIdle()
        sheet().assertDoesNotExist()

        alerting = false
        compose.waitForIdle()
        sheet().assertIsDisplayed()
        field().assertTextContains(LINK)
        compose.onNodeWithText(str(R.string.catalog_refresh_offline)).assertIsDisplayed()
    }

    @Test
    fun aTeaLoadedDuringAnAlertOpensOnceTheScreenIsBack() {
        links.outcome = { TeaLinkOutcome.Opened(KEY) }
        links.hold = CompletableDeferred()
        show()
        openPasteSheet()
        field().performTextInput(LINK)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()
        alerting = true
        compose.waitForIdle()

        links.hold?.complete(Unit)
        compose.waitForIdle()

        // The detail opens in the catalog's own stack, under the timer; the sheet is gone for good.
        assertThat(opened).containsExactly(KEY)
        alerting = false
        compose.waitForIdle()
        sheet().assertDoesNotExist()
    }

    private companion object {
        const val KEY = "XY0170"
        const val LINK = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"
    }
}
