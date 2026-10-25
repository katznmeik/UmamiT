package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.testing.catalogTea
import com.umamit.feature.brew.picker.TEA_PICKER_TAG
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.brew.setup.BrewSetupViewModel
import com.umamit.feature.catalog.CATALOG_ADD_SAVE_TAG
import com.umamit.feature.catalog.CATALOG_ADD_SHEET_TAG
import com.umamit.feature.catalog.CATALOG_TEA_ADD_TAG
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogTeaRoute
import com.umamit.feature.catalog.CatalogTeaViewModel
import com.umamit.feature.catalog.CatalogViewModel
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
                    catalogTeaScreen = { host -> CatalogTea(host) },
                    setupScreen = { onBack, onOpenTimer -> Setup(onBack, onOpenTimer) },
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
                HomeViewModel(collection.teas, sessions.sessions, starter, clock, photos, FakeTimerReliability())
            },
            pickerViewModel = viewModel { TeaPickerViewModel(collection.teas, sessions.sessions, photos) },
        )
    }

    @Composable
    private fun Catalog(tab: CatalogTab) {
        CatalogRoute(
            alerting = tab.alerting,
            onOpenTea = tab.openTea,
            modifier = tab.modifier,
            viewModel = viewModel { CatalogViewModel(catalog, FakeCatalogRefreshRunner()) },
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
    private fun Setup(onBack: () -> Unit, onOpenTimer: () -> Unit) {
        BrewSetupScreenRoute(
            onBack = onBack,
            onOpenTimer = onOpenTimer,
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
        const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"

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
