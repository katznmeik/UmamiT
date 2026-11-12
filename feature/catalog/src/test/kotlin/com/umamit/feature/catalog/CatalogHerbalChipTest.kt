package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogKind
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
 * The kind chips of Sammlung › Katalog (v0.2.1, #89; CAT-10, SCR-19, D-093, D-103; v0.6.1, #247, D-114) on fake data:
 * the list opens with no kind, every tea grouped by kind; *Kräutertee* narrows to the Kräutertees grouped by *Sorte*
 * (without „Pur", Q14), and a tap on the selected chip clears it again, never back to *Grüntee*. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogHerbalChipTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun herbal(key: String, name: String, sorte: String): CatalogTeaSummary =
        catalogTea(key, name, sorte).copy(kind = CatalogKind.HERBAL, originCountry = null)

    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel Sencha"),
        catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro"),
        herbal("XK0300", "Wiesenzauber", "Mischungen"),
        herbal("XK0400", "Zitronenwiese", "Mischungen").copy(isFlavored = true),
        herbal("XK0500", "Bergminze", "Einzelkräuter"),
    )

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(catalog: List<CatalogTeaSummary> = teas) {
        val repository = FakeCatalogRepository(CatalogOverview(catalog, FakeClock.DEFAULT_WALL_MS))
        val viewModel = CatalogViewModel(repository, FakeCatalogRefreshRunner())
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = false, onOpenTea = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private val isChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun chip(@StringRes id: Int) = compose.onNode(hasText(str(id)) and isChip)

    private fun tap(@StringRes id: Int) {
        chip(id).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    /** [text] somewhere in the list, scrolled to. */
    private fun listed(text: String) {
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(text))
        shown(text)
    }

    private fun hidden(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun theChipSwitchesToTheKraeuterteesBySorteAndBack() {
        show()

        // The list opens with no kind: every tea, the Kräutertees too (D-114 Q13).
        chip(R.string.catalog_filter_herbal).assertIsNotSelected()
        shown("Morgennebel Sencha")
        listed("Wiesenzauber")

        tap(R.string.catalog_filter_herbal)

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        compose.onNodeWithText("Tee-Kontor Kiel · 3 Tees · aktualisiert am 22. Okt.").assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Einzelkräuter")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Mischungen")).assertIsDisplayed()
        shown("Bergminze")
        shown("Wiesenzauber")
        // „Pur" doesn't apply: the flavoured blend shows and the chip is gone (Q14).
        shown("Zitronenwiese")
        chip(R.string.catalog_filter_pure).assertDoesNotExist()
        hidden("Morgennebel Sencha")
        // The kind doesn't count in „Filter (n)" (D-114).
        compose.onNodeWithText(str(R.string.catalog_filter_button)).performScrollTo().assertIsDisplayed()

        tap(R.string.catalog_filter_herbal)

        // Cleared: every tea again, never *Grüntee*.
        chip(R.string.catalog_filter_herbal).assertIsNotSelected()
        chip(R.string.catalog_filter_green).assertIsNotSelected()
        chip(R.string.catalog_filter_pure).assertDoesNotExist()
        shown("Morgennebel Sencha")
        compose.onNodeWithText("Tee-Kontor Kiel · 5 Tees · aktualisiert am 22. Okt.").assertIsDisplayed()
        listed("Zitronenwiese")
    }

    /** D-114: no chip is selected at first; clearing *Grüntee* shows every tea and the row goes back to its start. */
    @Test
    fun noKindIsSelectedByDefaultAndClearingTheLastChipShowsEveryTea() {
        show()
        CatalogKind.ALL.forEach { chip(it.chipLabel).assertIsNotSelected() }
        compose.onNodeWithTag(catalogKindGroupTag(CatalogKind.GREEN)).assertIsDisplayed()
        shown("Grüntee · 2")
        listed("Kräutertee · 3")

        tap(R.string.catalog_filter_green)
        chip(R.string.catalog_filter_green).assertIsSelected()
        chip(R.string.catalog_filter_pure).assertIsSelected()
        hidden("Wiesenzauber")
        compose.onNodeWithText("Tee-Kontor Kiel · 2 Tees · aktualisiert am 22. Okt.").assertIsDisplayed()

        tap(R.string.catalog_filter_green)
        chip(R.string.catalog_filter_green).assertIsNotSelected()
        shown("Morgennebel Sencha")
        listed("Wiesenzauber")
    }

    @Test
    fun clearingAChipOffScreenScrollsTheRowBackToItsStart() {
        show()
        tap(R.string.catalog_filter_chai)
        chip(R.string.catalog_filter_chai).assertIsSelected()
        // *Chai* closes the row: *Filter* is scrolled off to the left.
        compose.onNodeWithText(str(R.string.catalog_filter_button)).assertIsNotDisplayed()

        chip(R.string.catalog_filter_chai).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_chai).assertIsNotSelected()
        compose.onNodeWithText(str(R.string.catalog_filter_button)).assertIsDisplayed()
    }

    @Test
    fun aSearchHitAmongTheKraeuterteesIsHiddenAndShowAllClearsTheKind() {
        show()
        tap(R.string.catalog_filter_green)

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("bergminze")
        compose.waitForIdle()
        val hiddenHits = compose.activity.resources.getQuantityString(R.plurals.catalog_hidden_matches, 1, 1)
        shown(hiddenHits)

        compose.onNodeWithText(str(R.string.catalog_show_all)).performClick()
        compose.waitForIdle()

        // *Alle zeigen* clears the kind (D-114): no chip selected, every hit shown.
        CatalogKind.ALL.forEach { chip(it.chipLabel).assertIsNotSelected() }
        shown("Bergminze")
    }

    @Test
    fun anEmptyKraeuterteeViewIsNoEmptyCatalog() {
        // Before the first refresh after the update the catalog has green teas only (Q20 is #91's hint).
        show(teas.filterNot { (it.kind == CatalogKind.HERBAL) })

        tap(R.string.catalog_filter_herbal)

        compose.onNodeWithText("Tee-Kontor Kiel · 0 Tees · aktualisiert am 22. Okt.").assertIsDisplayed()
        hidden(str(R.string.catalog_empty_title))
        chip(R.string.catalog_filter_herbal).assertIsSelected()
    }
}
