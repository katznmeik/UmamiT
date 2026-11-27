package com.umamit.feature.collection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sammlung's ⋮ menu (SCR-08, SCR-19, CAT-07, CAT-17, D-102): *Tee aus dem Katalog hinzufügen* and *Tee ohne Katalog
 * hinzufügen* on *Meine Tees*, *Katalog aktualisieren* and *Link einfügen* on the *Katalog* segment, the settings
 * always.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CollectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var refreshes = 0
    private var pastes = 0
    private var settingsOpened = 0
    private var fromCatalog = 0
    private var withoutCatalog = 0
    private var imports = 0
    private val folds = mutableListOf<Boolean>()
    private val helps = mutableListOf<CollectionSegment>()

    private fun show(segment: CollectionSegment, foldable: Boolean = false) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CollectionScreen(
                    segment = segment,
                    onSegmentChange = {},
                    onOpenSettings = { settingsOpened++ },
                    catalogContent = { _, _ -> },
                    onRefreshCatalog = { refreshes++ },
                    onPasteLink = { pastes++ },
                    onFoldCatalog = if (foldable) ({ folds += it }) else null,
                    onAddFromCatalog = { fromCatalog++ },
                    onAddWithoutCatalog = { withoutCatalog++ },
                    onImportRecipe = { imports++ },
                    onOpenHelp = { helps += it },
                )
            }
        }
    }

    private fun openMenu() = compose.onNodeWithContentDescription("Weitere Optionen").performClick()

    /** ⋮ *Hilfe* (SCR-36 „Entry points"): the last item above *Einstellungen*, on both segments. */
    private fun assertMenuEndsWithHelp(segment: CollectionSegment) {
        show(segment)
        openMenu()
        val help = compose.onNodeWithText("Hilfe")
        help.assertIsDisplayed()
        val settingsTop = compose.onNodeWithText("Einstellungen").getBoundsInRoot().top
        assertThat(help.getBoundsInRoot().top).isLessThan(settingsTop)
        help.performClick()
        compose.waitForIdle()
        assertThat(helps).containsExactly(segment)
        compose.onNodeWithText("Hilfe").assertDoesNotExist()
    }

    @Test
    fun theMeineTeesMenuEndsWithHilfeAboveEinstellungen() = assertMenuEndsWithHelp(CollectionSegment.MyTeas)

    @Test
    fun theCatalogMenuEndsWithHilfeAboveEinstellungen() = assertMenuEndsWithHelp(CollectionSegment.Catalog)

    @Test
    fun theCatalogMenuOffersKatalogAktualisieren() {
        show(CollectionSegment.Catalog)

        openMenu()
        compose.onNodeWithText("Katalog aktualisieren").performClick()
        compose.waitForIdle()

        assertThat(refreshes).isEqualTo(1)
        compose.onNodeWithText("Katalog aktualisieren").assertDoesNotExist()
    }

    @Test
    fun theCatalogMenuOffersLinkEinfuegen() {
        show(CollectionSegment.Catalog)

        openMenu()
        compose.onNodeWithText("Link einfügen").performClick()
        compose.waitForIdle()

        assertThat(pastes).isEqualTo(1)
        assertThat(refreshes).isEqualTo(0)
        compose.onNodeWithText("Link einfügen").assertDoesNotExist()
    }

    @Test
    fun myTeasHasNoCatalogEntryButTheSettings() {
        show(CollectionSegment.MyTeas)

        openMenu()
        compose.onNodeWithText("Katalog aktualisieren").assertDoesNotExist()
        compose.onNodeWithText("Link einfügen").assertDoesNotExist()
        compose.onNodeWithText("Einstellungen").performClick()

        assertThat(settingsOpened).isEqualTo(1)
        assertThat(refreshes).isEqualTo(0)
        assertThat(pastes).isEqualTo(0)
    }

    /** The FAB of rc.1 moved into the menu (D-102). */
    @Test
    fun myTeasOffersTeeAusDemKatalogAndTeeOhneKatalogHinzufuegen() {
        show(CollectionSegment.MyTeas)

        openMenu()
        compose.onNodeWithText("Tee aus dem Katalog hinzufügen").performClick()
        openMenu()
        compose.onNodeWithText("Tee ohne Katalog hinzufügen").performClick()

        assertThat(fromCatalog).isEqualTo(1)
        assertThat(withoutCatalog).isEqualTo(1)
    }

    /** PRF-08, D-130 Q14: the third entry, only on *Meine Tees*. */
    @Test
    fun myTeasOffersRezeptImportieren() {
        show(CollectionSegment.MyTeas)

        openMenu()
        compose.onNodeWithText("Rezept importieren").performClick()

        assertThat(imports).isEqualTo(1)
    }

    @Test
    fun theCatalogSegmentHasNoAddEntries() {
        show(CollectionSegment.Catalog)

        openMenu()
        compose.onNodeWithText("Tee aus dem Katalog hinzufügen").assertDoesNotExist()
        compose.onNodeWithText("Tee ohne Katalog hinzufügen").assertDoesNotExist()
        compose.onNodeWithText("Rezept importieren").assertDoesNotExist()
    }

    @Test
    fun aFoldingCatalogOffersAlleEinklappenAndAlleAusklappen() {
        // D-098: only while the catalog shows a view whose groups fold.
        show(CollectionSegment.Catalog, foldable = true)

        openMenu()
        compose.onNodeWithText("Alle einklappen").performClick()
        compose.waitForIdle()
        openMenu()
        compose.onNodeWithText("Alle ausklappen").performClick()
        compose.waitForIdle()

        assertThat(folds).containsExactly(true, false).inOrder()
    }

    @Test
    fun aCatalogViewWithoutFoldingHasNoFoldEntries() {
        show(CollectionSegment.Catalog, foldable = false)

        openMenu()

        compose.onNodeWithText("Katalog aktualisieren").assertExists()
        compose.onNodeWithText("Alle einklappen").assertDoesNotExist()
        compose.onNodeWithText("Alle ausklappen").assertDoesNotExist()
    }
}
