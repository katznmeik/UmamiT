package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeExactPrices
import com.umamit.core.testing.FakeGuideRepository
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.catalogTea
import com.umamit.feature.brew.picker.TEA_PICKER_TAG
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.brew.setup.BrewSetupViewModel
import com.umamit.feature.catalog.CATALOG_ADD_SAVE_TAG
import com.umamit.feature.catalog.CATALOG_ADD_SHEET_TAG
import com.umamit.feature.catalog.CATALOG_GUIDES_ROW_TAG
import com.umamit.feature.catalog.CATALOG_TEA_ADD_TAG
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogTeaRoute
import com.umamit.feature.catalog.CatalogTeaViewModel
import com.umamit.feature.catalog.CatalogViewModel
import com.umamit.feature.catalog.GuideArticleRoute
import com.umamit.feature.catalog.GuideListRoute
import com.umamit.feature.catalog.GuideListViewModel
import com.umamit.feature.catalog.GuideViewModel
import com.umamit.feature.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.feature.brew.setup.BrewSetupRoute as BrewSetupScreenRoute

/**
 * Brewing a tea just bought (#72, D-073, D-088): the tea picker's *Aus dem Katalog* leads to
 * Sammlung › Katalog, the tea's detail and the add sheet, and the save opens the setup (SCR-04)
 * of that tea; a repurchase does the same with the owned tea. Backing out anywhere returns to
 * the picker and changes nothing. The real root and shell navigation and the real view models
 * on fakes; no shop request (the exact price is faked as offline). Invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogBrewFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()
    private val catalog = FakeCatalogRepository(
        initial = CatalogOverview(listOf(catalogTea(KEY, NAME)), lastRefreshAtMillis = 1L),
        details = listOf(TEA),
    )
    private val guides = FakeGuideRepository(listOf(GUIDE))
    private val add = FakeAddFromCatalog(collection)
    private val prices = FakeExactPrices()
    private val alertKey = MutableStateFlow<String?>(null)
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 0L

        override fun bootCount() = 1
    }

    @Before
    fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    startScreen = { tab -> Start(tab, photos) },
                    catalogScreen = { tab -> Catalog(tab) },
                    catalogScreens = CatalogStackScreens(
                        tea = { host -> CatalogTea(host) },
                        guides = { onBack, onOpenGuide ->
                            GuideListRoute(onBack, onOpenGuide, viewModel = viewModel { GuideListViewModel(guides) })
                        },
                        guide = { onBack, onOpenTea, onOpenGuide ->
                            GuideArticleRoute(
                                onBack,
                                onOpenTea,
                                onOpenGuide,
                                viewModel = viewModel { GuideViewModel(createSavedStateHandle(), guides) },
                            )
                        },
                    ),
                    setupScreen = { onBack, onOpenTimer, onOpenCatalog -> Setup(onBack, onOpenTimer, onOpenCatalog) },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The app's Start tab with its picker, on view models built on the fakes. */
    @Composable
    private fun Start(tab: StartTab, photos: PhotoFiles) {
        StartTabContent(
            tab = tab,
            homeViewModel = viewModel {
                HomeViewModel(
                    collection.teas,
                    sessions.sessions,
                    starter,
                    clock,
                    photos,
                    FakeTimerReliability(),
                    FakeLowStockRepository(),
                )
            },
            pickerViewModel = viewModel {
                TeaPickerViewModel(collection.teas, sessions.sessions, FakeTeaArchive(sessions), photos)
            },
        )
    }

    @Composable
    private fun Catalog(tab: CatalogTab) {
        CatalogRoute(
            alerting = tab.alerting,
            onOpenTea = tab.openTea,
            onOpenGuides = tab.openGuides,
            modifier = tab.modifier,
            viewModel = viewModel { CatalogViewModel(catalog, FakeCatalogRefreshRunner(), guides) },
        )
    }

    /** The detail of the route's catalog key (its saved state), with the shell's callbacks. */
    @Composable
    private fun CatalogTea(host: CatalogTeaHost) {
        CatalogTeaRoute(
            onBack = host.onBack,
            alerting = host.alerting,
            onPurchaseSave = host.onPurchaseSave,
            viewModel = viewModel {
                CatalogTeaViewModel(createSavedStateHandle(), catalog, collection.stock, add, prices, FakeClock())
            },
        )
    }

    /** The setup of the route's tea (its saved state). */
    @Composable
    private fun Setup(onBack: () -> Unit, onOpenTimer: () -> Unit, onOpenCatalog: (String) -> Unit) {
        BrewSetupScreenRoute(
            onBack = onBack,
            onOpenTimer = onOpenTimer,
            onOpenCatalog = onOpenCatalog,
            viewModel = viewModel {
                BrewSetupViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    collection.recipes,
                    collection.vessels,
                    collection.stock,
                    sessions.sessions,
                    settings,
                    starter,
                    catalog,
                )
            },
        )
    }

    private fun openPicker() {
        compose.onNodeWithText("Aufguss starten").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertIsDisplayed()
    }

    private fun toCatalog() {
        compose.onNodeWithText("Aus dem Katalog").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun toDetail() {
        compose.onNodeWithText(NAME).performClick()
        compose.waitForIdle()
    }

    private fun openAddSheet() {
        compose.onNodeWithTag(CATALOG_TEA_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun save() {
        compose.onNodeWithTag(CATALOG_ADD_SAVE_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun teas(): List<Tea> = runBlocking { collection.teas.observeTeas().first().map { it.tea } }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Two taps on the screen's own back button before the next frame, as a quick double tap
     * lands while the screen still leaves (03 §3.6).
     */
    private fun doubleTapOnScreenBack() {
        val back = compose.onNodeWithContentDescription("Zurück").fetchSemanticsNode()
        val click = requireNotNull(back.config[SemanticsActions.OnClick].action)
        compose.runOnUiThread {
            click()
            click()
        }
        compose.waitForIdle()
    }

    private fun toCatalogInSammlung() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Katalog").performClick()
        compose.waitForIdle()
    }

    /** Every text the screen shows now, in order: the setup's values, read back after a round trip. */
    private fun shownTexts(): List<String> = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }

    private fun openHisuiSetup() {
        openPicker()
        compose.onNode(hasText("Bio Sencha Hisui") and hasAnyAncestor(hasTestTag(TEA_PICKER_TAG)))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Brühen").assertIsDisplayed()
    }

    @Test
    fun `the setup's Im Katalog ansehen opens the tea's catalog page and back keeps every value (SCR-04, D-098)`() {
        catalog.details.value += HISUI_KEY to TEA.copy(key = HISUI_KEY, name = HISUI_CATALOG_NAME, discontinued = true)
        show()
        openHisuiSetup()
        compose.onNodeWithText("Westlich").performClick()
        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.waitForIdle()
        val before = shownTexts()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Im Katalog ansehen").performClick()
        compose.waitForIdle()

        // SCR-21 of the setup's tea above the setup, the discontinued one too.
        compose.onNodeWithText(HISUI_CATALOG_NAME).assertIsDisplayed()
        compose.onNodeWithText("Mehr Wasser").assertDoesNotExist()

        back()

        compose.onNodeWithText("Brühen").assertIsDisplayed()
        assertThat(shownTexts()).isEqualTo(before)
    }

    @Test
    fun `the catalog page's own back returns to the setup too (SCR-04, D-098)`() {
        catalog.details.value += HISUI_KEY to TEA.copy(key = HISUI_KEY, name = HISUI_CATALOG_NAME)
        show()
        openHisuiSetup()
        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.waitForIdle()
        val before = shownTexts()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Im Katalog ansehen").performClick()
        compose.waitForIdle()

        doubleTapOnScreenBack()

        // One back: the setup as it was, never Start under it (D-090).
        compose.onNodeWithText("Brühen").assertIsDisplayed()
        assertThat(shownTexts()).isEqualTo(before)
    }

    @Test
    fun `a quick double tap on the tea detail's back leaves Sammlung on the catalog (D-090)`() {
        show()
        toCatalogInSammlung()
        toDetail()

        doubleTapOnScreenBack()

        // Never a blank tab: the second tap must not pop the tab's own start screen.
        compose.onNodeWithText(NAME).assertIsDisplayed()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `a quick double tap on the guides' back leaves Sammlung on the catalog (D-090)`() {
        show()
        toCatalogInSammlung()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        doubleTapOnScreenBack()

        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `a quick double tap on a catalog row opens one detail (D-090)`() {
        show()
        toCatalogInSammlung()
        val row = compose.onNodeWithText(NAME).fetchSemanticsNode()
        val click = requireNotNull(row.config[SemanticsActions.OnClick].action)
        compose.runOnUiThread {
            click()
            click()
        }
        compose.waitForIdle()

        back()

        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `a link of a guide to itself opens nothing (D-090)`() {
        show()
        toCatalogInSammlung()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(GUIDE.title).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Nach oben").performScrollTo().performFirstLinkClick()
        compose.waitForIdle()
        back()

        // One back from the article: the guides list, not the same article again.
        compose.onNodeWithText("Nach oben").assertDoesNotExist()
        compose.onNodeWithText(GUIDE.title).assertIsDisplayed()
    }

    @Test
    fun `Start's Katalog oeffnen shows the catalog list, not a screen left open in Sammlung (D-090)`() {
        // An empty collection: Start offers *Katalog öffnen* (GEN-09).
        runBlocking { SeedData.teas.forEach { collection.teas.saveTea(it.copy(isArchived = true)) } }
        show()
        toCatalogInSammlung()
        toDetail()
        compose.onNodeWithTag(tabTag(Tab.Start)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Katalog öffnen").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `a quick double tap on the setup's back leaves the app on Start (D-090)`() {
        show()
        openPicker()
        toCatalog()
        toDetail()
        openAddSheet()
        save()
        compose.onNodeWithText("Brühen").assertIsDisplayed()

        doubleTapOnScreenBack()

        // Never a blank app: the second tap must not pop the shell under the setup.
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithText("Aufguss starten").assertIsDisplayed()
    }

    @Test
    fun `picker - catalog - detail - add ends in the setup of the new tea`() {
        show()
        openPicker()

        toCatalog()
        // Sammlung › Katalog (SCR-19), in the Sammlung tab (D-088).
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
        toDetail()
        openAddSheet()
        save()

        // The setup (SCR-04) of the tea just added, with the shell back on Start underneath.
        val teaId = add.purchases.single().let { "tea-${it.catalogKey}" }
        assertThat(runBlocking { collection.teas.getTea(teaId) }?.catalogKey).isEqualTo(KEY)
        compose.onNodeWithText("Brühen").assertIsDisplayed()
        compose.onNodeWithText(KEY).assertIsDisplayed()

        back()
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithText("Aufguss starten").assertIsDisplayed()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun `a tea linked from a guide in the flow ends in the setup too`() {
        show()
        openPicker()
        toCatalog()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(GUIDE.title).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Probier den", substring = true).performScrollTo().performFirstLinkClick()
        compose.waitForIdle()
        openAddSheet()
        save()

        assertThat(add.purchases.single().catalogKey).isEqualTo(KEY)
        compose.onNodeWithText("Brühen").assertIsDisplayed()
        compose.onNodeWithText(KEY).assertIsDisplayed()
        // Back from the setup: Start, and the flow's screens are gone from Sammlung.
        back()
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun `backing out through the Ratgeber returns to the picker`() {
        show()
        openPicker()
        toCatalog()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(GUIDE.title).performClick()
        compose.waitForIdle()

        back() // the article
        back() // the guides
        back() // the catalog: back to the picker on Start
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertIsDisplayed()
        assertThat(add.purchases).isEmpty()
    }

    @Test
    fun `a repurchase from the picker ends in the setup of the owned tea`() {
        runBlocking {
            collection.teas.saveTea(
                Tea("owned", "Mein Morgennebel", TeaCategory.GREEN, TeaForm.LOOSE_LEAF, catalogKey = KEY),
            )
        }
        show()
        openPicker()
        toCatalog()
        toDetail()
        openAddSheet()
        save()

        assertThat(add.purchases).hasSize(1)
        assertThat(teas().count { it.catalogKey == KEY }).isEqualTo(1)
        compose.onNodeWithText("Brühen").assertIsDisplayed()
        compose.onNodeWithText("Mein Morgennebel").assertIsDisplayed()
    }

    @Test
    fun `backing out of the flow returns to the picker and changes nothing`() {
        show()
        val teasBefore = teas()
        openPicker()
        toCatalog()
        toDetail()
        openAddSheet()

        // The sheet is a window of its own: the system's back reaches it first and closes it.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(CATALOG_ADD_SHEET_TAG).assertDoesNotExist()
        back() // the detail
        compose.onNodeWithText(NAME).assertIsDisplayed()
        back() // the catalog: back to the picker on Start
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertIsDisplayed()

        assertThat(add.purchases).isEmpty()
        assertThat(teas()).isEqualTo(teasBefore)
    }

    @Test
    fun `an add from Sammlung itself stays in Sammlung`() {
        show()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Katalog").performClick()
        compose.waitForIdle()
        toDetail()
        openAddSheet()
        save()

        assertThat(add.purchases).hasSize(1)
        compose.onNodeWithText("Brühen").assertDoesNotExist()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
    }

    @Test
    fun `leaving the flow for another tab ends it - a later add stays in Sammlung`() {
        show()
        openPicker()
        toCatalog()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        toDetail()
        openAddSheet()
        save()

        assertThat(add.purchases).hasSize(1)
        compose.onNodeWithText("Brühen").assertDoesNotExist()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun `an alert during the flow removes the add sheet and never opens the setup (rule 8)`() {
        show()
        openPicker()
        toCatalog()
        toDetail()
        openAddSheet()

        alertKey.value = "s1:0:1"
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()

        compose.onNodeWithTag(CATALOG_ADD_SHEET_TAG).assertDoesNotExist()
        assertThat(add.purchases).isEmpty()
        compose.onNodeWithText("Brühen").assertDoesNotExist()
        // Backing out during the alert returns to Start, but the picker stays closed (it never covers an alert).
        back()
        back()
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithTag(TEA_PICKER_TAG).assertDoesNotExist()
    }

    private companion object {
        const val KEY = "XY0100"
        const val NAME = "Morgennebel Sencha"
        const val HISUI_KEY = "TG0557"
        const val HISUI_CATALOG_NAME = "Hisui im Katalog"
        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"

        val GUIDE = GuideDetail(
            slug = "ein-guide",
            title = "Ein Guide für Sencha",
            url = "https://shop.example/tee-info/ein-guide",
            publishedAtMillis = null,
            heroPath = null,
            body = GuideDocument(
                listOf(
                    GuideBlock.Paragraph(
                        listOf(
                            GuideInline.Span("Probier den "),
                            GuideInline.Span("Morgennebel", link = "umamit://catalog/$KEY"),
                        ),
                    ),
                    // An anchor in the article itself (D-087): it resolves to the same guide.
                    GuideBlock.Paragraph(
                        listOf(GuideInline.Span("Nach oben", link = "umamit://guide/ein-guide")),
                    ),
                ),
            ),
        )

        val TEA = CatalogTeaDetail(
            key = KEY,
            name = NAME,
            productUrl = PRODUCT,
            teaType = "Sencha",
            basePriceCentsPer100g = 1240,
            available = true,
            packages = listOf(CatalogPackage(KEY, "100 g", 100.0, 1240, false, null, PRODUCT, true)),
        )
    }
}
