package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaSummary
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
 * The *Kräutertee* view of Sammlung › Katalog (v0.2.1, #91; CAT-10, SCR-19, D-093 Q19, Q20) on
 * fake data: its filter sheet without Aroma and Umami, *Zurücksetzen* staying in the view, and the
 * hint before the first refresh after the update with *Jetzt laden*. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogHerbalViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun herbal(key: String, name: String, sorte: String): CatalogTeaSummary =
        catalogTea(key, name, sorte).copy(isHerbal = true, originCountry = "Deutschland")

    private val green = listOf(
        catalogTea("XY0100", "Morgennebel Sencha").copy(originCountry = "Japan"),
        catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro").copy(originCountry = "Japan"),
    )
    private val teas = green + listOf(
        herbal("XK0300", "Wiesenzauber", "Mischungen"),
        herbal("XK0500", "Bergminze", "Einzelkräuter").copy(isOrganic = true),
    )

    private val refresh = FakeCatalogRefreshRunner()
    private lateinit var repository: FakeCatalogRepository

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(catalog: List<CatalogTeaSummary> = teas, herbalPending: Boolean = false) {
        repository = FakeCatalogRepository(CatalogOverview(catalog, FakeClock.DEFAULT_WALL_MS))
        repository.herbalPending.value = herbalPending
        val viewModel = CatalogViewModel(repository, refresh)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = false, onOpenTea = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private val isChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private val inSheet = hasAnyAncestor(hasTestTag(CATALOG_FILTER_SHEET_TAG))

    private fun chip(@StringRes id: Int) = compose.onNode(hasText(str(id)) and isChip and !inSheet)

    private fun sheetNode(@StringRes id: Int) = compose.onNode(hasText(str(id)) and inSheet)

    private fun openSheet() {
        compose.onNode(hasText(str(R.string.catalog_filter_button), substring = true) and isChip).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CATALOG_FILTER_SHEET_TAG).assertIsDisplayed()
    }

    private fun switchToHerbal() {
        chip(R.string.catalog_filter_herbal).performClick()
        compose.waitForIdle()
    }

    private val hint get() = str(R.string.catalog_herbal_pending)

    @Test
    fun theGreenViewsFilterSheetOffersAromaAndUmami() {
        show()

        openSheet()

        sheetNode(R.string.catalog_filter_flavour).assertExists()
        sheetNode(R.string.catalog_filter_umami).assertExists()
        sheetNode(R.string.catalog_filter_origins).assertExists()
    }

    @Test
    fun theKraeuterteeViewsFilterSheetOffersHerkunftBioNeuImBesitzAndVorraetigButNoAromaOrUmami() {
        show()
        switchToHerbal()

        openSheet()

        sheetNode(R.string.catalog_filter_flavour).assertDoesNotExist()
        sheetNode(R.string.catalog_filter_umami).assertDoesNotExist()
        sheetNode(R.string.catalog_umami_any).assertDoesNotExist()
        sheetNode(R.string.catalog_flavour_flavoured).assertDoesNotExist()
        sheetNode(R.string.catalog_filter_origins).assertExists()
        listOf(
            R.string.catalog_filter_available,
            R.string.catalog_filter_organic,
            R.string.catalog_filter_new,
            R.string.catalog_filter_owned,
        ).forEach { compose.onNode(hasText(str(it)) and isChip and inSheet).assertExists() }
    }

    @Test
    fun zuruecksetzenInTheKraeuterteeViewResetsTheFiltersAndStaysInTheView() {
        show()
        switchToHerbal()
        chip(R.string.catalog_filter_organic).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wiesenzauber").assertDoesNotExist()

        openSheet()
        sheetNode(R.string.catalog_filter_reset).performScrollTo().performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        compose.onNode(hasText("Wiesenzauber") and !inSheet).assertExists()
        compose.onNode(hasText("Bergminze") and !inSheet).assertExists()
        compose.onNode(hasText("Morgennebel Sencha") and !inSheet).assertDoesNotExist()
    }

    @Test
    fun filterZuruecksetzenInTheKraeuterteeViewStaysInTheView() {
        show()
        switchToHerbal()
        chip(R.string.catalog_filter_new).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.catalog_no_match)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.catalog_reset_filters)).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        compose.onNodeWithText("Wiesenzauber").assertIsDisplayed()
        compose.onNodeWithText("Morgennebel Sencha").assertDoesNotExist()
    }

    @Test
    fun beforeTheFirstRefreshAfterTheUpdateTheEmptyKraeuterteeViewShowsTheHint() {
        show(green, herbalPending = true)

        // Not in the green view.
        compose.onNodeWithText(hint).assertDoesNotExist()
        switchToHerbal()

        compose.onNodeWithText(hint).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.catalog_herbal_load_now)).assertIsDisplayed()
        // The hint, not the no-match notice.
        compose.onNodeWithText(str(R.string.catalog_no_match)).assertDoesNotExist()
    }

    @Test
    fun jetztLadenStartsTheRefreshAndShowsItsProgress() {
        show(green, herbalPending = true)
        switchToHerbal()

        compose.onNodeWithText(str(R.string.catalog_herbal_load_now)).performClick()
        refresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
        compose.waitForIdle()

        assertThat(refresh.starts).isEqualTo(1)
        compose.onNodeWithTag(CATALOG_REFRESH_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun theHintIsGoneAfterTheRefresh() {
        show(green, herbalPending = true)
        switchToHerbal()
        compose.onNodeWithText(hint).assertIsDisplayed()

        repository.herbalPending.value = false
        repository.catalog.value = CatalogOverview(teas, FakeClock.DEFAULT_WALL_MS)
        compose.waitForIdle()

        compose.onNodeWithText(hint).assertDoesNotExist()
        compose.onNodeWithText("Wiesenzauber").assertIsDisplayed()
    }

    @Test
    fun withoutPendingKraeuterteesAnEmptyViewShowsTheNoMatchNotice() {
        show(green, herbalPending = false)
        switchToHerbal()

        compose.onNodeWithText(hint).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.catalog_no_match)).assertIsDisplayed()
    }
}
