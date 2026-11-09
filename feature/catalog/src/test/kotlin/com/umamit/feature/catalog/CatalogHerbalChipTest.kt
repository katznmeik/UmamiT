package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The *Grüntee* and *Kräutertee* chips of Sammlung › Katalog (v0.2.1, #89; CAT-10, SCR-19, D-093, D-103) on fake
 * data: the list opens on the green teas with *Grüntee* selected, *Kräutertee* switches to the Kräutertees grouped by
 * *Sorte* (without „Pur", Q14) and back, and *Grüntee* returns too. Invented teas (D-016).
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

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun hidden(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun theChipSwitchesToTheKraeuterteesBySorteAndBack() {
        show()

        // The list opens on the green teas (D-093 Q11).
        chip(R.string.catalog_filter_herbal).assertIsNotSelected()
        shown("Morgennebel Sencha")
        hidden("Wiesenzauber")

        chip(R.string.catalog_filter_herbal).performScrollTo().performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        compose.onNodeWithText("Tee-Kontor Kiel · 3 Kräutertees · aktualisiert am 22. Okt.").assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Einzelkräuter")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Mischungen")).assertIsDisplayed()
        shown("Bergminze")
        shown("Wiesenzauber")
        // „Pur" doesn't apply: the flavoured blend shows and the chip is gone (Q14).
        shown("Zitronenwiese")
        chip(R.string.catalog_filter_pure).assertDoesNotExist()
        hidden("Morgennebel Sencha")
        shown(str(R.string.catalog_filter_button_count, 1))

        chip(R.string.catalog_filter_herbal).performScrollTo().performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsNotSelected()
        chip(R.string.catalog_filter_pure).assertIsSelected()
        shown("Morgennebel Sencha")
        hidden("Wiesenzauber")
        compose.onNodeWithText("Tee-Kontor Kiel · 2 Grüntees · aktualisiert am 22. Okt.").assertIsDisplayed()
    }

    /** D-103: the green range has its own chip, selected by default; a tap returns from the Kräutertees. */
    @Test
    fun grunteeIsSelectedByDefaultAndReturnsFromTheKraeutertees() {
        show()
        chip(R.string.catalog_filter_green).assertIsSelected()

        // A tap on the selected range changes nothing.
        chip(R.string.catalog_filter_green).performScrollTo().performClick()
        compose.waitForIdle()
        chip(R.string.catalog_filter_green).assertIsSelected()
        shown("Morgennebel Sencha")

        chip(R.string.catalog_filter_herbal).performScrollTo().performClick()
        compose.waitForIdle()
        chip(R.string.catalog_filter_green).assertIsNotSelected()
        hidden("Morgennebel Sencha")

        chip(R.string.catalog_filter_green).performScrollTo().performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_green).assertIsSelected()
        chip(R.string.catalog_filter_herbal).assertIsNotSelected()
        shown("Morgennebel Sencha")
        hidden("Wiesenzauber")
        compose.onNodeWithText("Tee-Kontor Kiel · 2\u00A0Grüntees · aktualisiert am 22. Okt.").assertIsDisplayed()
    }

    @Test
    fun aSearchHitAmongTheKraeuterteesIsHiddenAndShowAllOpensTheirView() {
        show()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("bergminze")
        compose.waitForIdle()
        val hiddenHits = compose.activity.resources.getQuantityString(R.plurals.catalog_hidden_matches, 1, 1)
        shown(hiddenHits)

        compose.onNodeWithText(str(R.string.catalog_show_all)).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        shown("Bergminze")
    }

    @Test
    fun anEmptyKraeuterteeViewIsNoEmptyCatalog() {
        // Before the first refresh after the update the catalog has green teas only (Q20 is #91's hint).
        show(teas.filterNot { (it.kind == CatalogKind.HERBAL) })

        chip(R.string.catalog_filter_herbal).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Tee-Kontor Kiel · 0 Kräutertees · aktualisiert am 22. Okt.").assertIsDisplayed()
        hidden(str(R.string.catalog_empty_title))
        chip(R.string.catalog_filter_herbal).assertIsSelected()
    }
}
