package com.umamit.feature.catalog

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.catalog.CatalogBrowser
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.TasteScale
import com.umamit.core.testing.catalogTea
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The filter sheet's taste notes and scales (v0.5.0, #204, D-108 point 8, SCR-19): each chip states whether it is
 * chosen, a scale chip names its scale for TalkBack, and *Zurücksetzen* clears both. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogTasteFilterSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel")
            .copy(tasteNotes = listOf("mild", "fruchtig"), tasteScales = listOf(TasteScale("Süße", 3))),
        catalogTea("XY0200", "Abendrot").copy(tasteNotes = listOf("mild")),
    )

    @Test
    fun notesAndScalesReadTheirStateAndResetClearsThem() {
        var filter by mutableStateOf(CatalogFilter())
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogFilterSheet(
                    filter = filter,
                    listing = CatalogBrowser.listing(teas, filter, null, null),
                    onFilterChange = { change -> filter = change(filter) },
                    onDismissRequest = {},
                )
            }
        }

        compose.onNodeWithText("fruchtig").performScrollTo().assertIsNotSelected().performClick()
        compose.onNodeWithText("fruchtig").assertIsSelected()
        assertThat(filter.notes).containsExactly("fruchtig")

        compose.onNodeWithContentDescription("Süße egal").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Süße ab 3").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Süße ab 3").assertIsSelected()
        compose.onNodeWithContentDescription("Süße egal").assertIsNotSelected()
        assertThat(filter.minScales).containsExactly("suße", 3)
        assertThat(filter.changedFilters).isEqualTo(2)

        compose.onNodeWithText("Zurücksetzen").performScrollTo().performClick()
        assertThat(filter.notes).isEmpty()
        assertThat(filter.minScales).isEmpty()
        compose.onNodeWithText("fruchtig").assertIsNotSelected()
    }

    @Test
    fun fertigIsPinnedClosesTheSheetFromTheListEndAndResetKeepsSearchAndKind() {
        var filter by mutableStateOf(CatalogFilter(search = "nebel", kind = CatalogKind.BLACK, notes = setOf("mild")))
        var dismissed = 0
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogFilterSheet(
                    filter = filter,
                    listing = CatalogBrowser.listing(teas, filter, null, null),
                    onFilterChange = { change -> filter = change(filter) },
                    onDismissRequest = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("Zurücksetzen").performScrollTo().performClick()
        assertThat(filter.notes).isEmpty()
        assertThat(filter.search).isEqualTo("nebel")
        assertThat(filter.kind).isEqualTo(CatalogKind.BLACK)

        compose.onAllNodesWithText("Fertig").assertCountEquals(1)
        compose.onNodeWithText("Fertig").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(1)
    }
}
