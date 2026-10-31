package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeExactPrices
import com.umamit.core.testing.FakeTeaLinkLoader
import com.umamit.core.testing.catalogTea
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogTeaRoute
import com.umamit.feature.catalog.CatalogTeaViewModel
import com.umamit.feature.catalog.CatalogViewModel
import com.umamit.feature.catalog.TEA_LINK_FIELD_TAG
import com.umamit.feature.catalog.TEA_LINK_LOAD_TAG
import com.umamit.feature.catalog.TEA_LINK_SHEET_TAG
import com.umamit.feature.catalog.TeaLinkShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tea from a shop link through the real root and shell (v0.2.1, #98; CAT-17 AC1, SCR-19,
 * SCR-21, D-094; spec #81 seam 5): a link shared to the app opens the tea's detail in Sammlung,
 * *Link einfügen* in Sammlung's ⋮ menu with a valid and an invalid link, and the timer first
 * (rule 8). The real view models on fakes; no shop request. Invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaLinkFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val catalog = FakeCatalogRepository(
        initial = CatalogOverview(listOf(catalogTea("XY0100", "Morgennebel Sencha")), lastRefreshAtMillis = 1L),
        details = listOf(TEA),
    )
    private val links = FakeTeaLinkLoader { text ->
        if (text.contains(LINK)) TeaLinkOutcome.Opened(KEY) else TeaLinkOutcome.NotATeaLink
    }
    private val collection = FakeCollection()
    private val share = MutableStateFlow<TeaLinkShare?>(null)
    private val taken = mutableListOf<Long>()
    private val brew = MutableStateFlow<ActiveBrew?>(null)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = MutableStateFlow(null),
                    teaLinkShare = share,
                    onTakeTeaLinkShare = {
                        taken += it
                        if (share.value?.token == it) share.value = null
                    },
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    settings = testSettingsScreens(),
                    catalogScreen = { tab -> Catalog(tab) },
                    catalogScreens = CatalogStackScreens(tea = { host -> Tea(host) }),
                )
            }
        }
        compose.waitForIdle()
    }

    /** The app's catalog segment on the fakes, wired as [CatalogTabContent] wires it. */
    @Composable
    private fun Catalog(tab: CatalogTab) {
        CatalogRoute(
            alerting = tab.alerting,
            onOpenTea = tab.openTea,
            modifier = tab.modifier,
            pasteLinkRequest = tab.pasteLinkRequest,
            sharedLink = tab.sharedLink,
            onTakeSharedLink = tab.onTakeSharedLink,
            viewModel = viewModel { CatalogViewModel(catalog, FakeCatalogRefreshRunner(), links = links) },
        )
    }

    @Composable
    private fun Tea(host: CatalogTeaHost) {
        CatalogTeaRoute(
            onBack = host.onBack,
            alerting = host.alerting,
            onPurchaseSave = host.onPurchaseSave,
            viewModel = viewModel {
                CatalogTeaViewModel(
                    createSavedStateHandle(),
                    catalog,
                    collection.stock,
                    FakeAddFromCatalog(collection),
                    FakeExactPrices(),
                    FakeClock(),
                )
            },
        )
    }

    private fun detailShown() = compose.onNodeWithText(TEA.name).assertIsDisplayed()

    @Test
    fun aSharedLinkOpensTheTeasDetailInSammlung() {
        show()

        share.value = TeaLinkShare(token = 3, text = "Nebelhang Sencha | Tee-Kontor Kiel $LINK")
        compose.waitForIdle()

        detailShown()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        assertThat(links.loaded).containsExactly("Nebelhang Sencha | Tee-Kontor Kiel $LINK")
        assertThat(taken).containsExactly(3L)
    }

    @Test
    fun aSharedLinkThatIsNoTeaLinkSaysSoOverTheCatalog() {
        show()

        share.value = TeaLinkShare(token = 4, text = "https://www.example.com/tee")
        compose.waitForIdle()

        compose.onNodeWithTag(TEA_LINK_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Das ist kein Tee-Link von Tee-Kontor Kiel.").assertIsDisplayed()
        compose.onNodeWithText(TEA.name).assertDoesNotExist()
    }

    @Test
    fun linkEinfuegenWithAValidLinkOpensTheDetail() {
        show()
        toCatalog()

        pasteLink(LINK)

        detailShown()
        compose.onNodeWithTag(TEA_LINK_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun linkEinfuegenWithAnInvalidLinkKeepsTheSheetWithItsMessage() {
        show()
        toCatalog()

        pasteLink("https://www.example.com/tee")

        compose.onNodeWithText("Das ist kein Tee-Link von Tee-Kontor Kiel.").assertIsDisplayed()
        compose.onNodeWithTag(TEA_LINK_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText(TEA.name).assertDoesNotExist()
    }

    @Test
    fun aShareWaitsWhileTheTimerIsShownAndNeverCoversIt() {
        brew.value = ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        share.value = TeaLinkShare(token = 5, text = LINK)
        compose.waitForIdle()

        // The timer stays on top and nothing is fetched behind it.
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(links.loaded).isEmpty()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        detailShown()
        assertThat(links.loaded).containsExactly(LINK)
    }

    private fun toCatalog() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Katalog").performClick()
        compose.waitForIdle()
    }

    private fun pasteLink(text: String) {
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Link einfügen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_LINK_FIELD_TAG).performTextInput(text)
        compose.onNodeWithTag(TEA_LINK_LOAD_TAG).performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val KEY = "XY0170"
        const val LINK = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"

        val TEA = CatalogTeaDetail(
            key = KEY,
            name = "Nebelhang Sencha",
            secondaryName = null,
            productUrl = LINK,
            teaType = "Sencha",
            available = false,
        )
    }
}
