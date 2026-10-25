package com.umamit.feature.collection

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

/** Sammlung's ⋮ menu (SCR-19, CAT-07): *Katalog aktualisieren* on the *Katalog* segment, the settings always. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CollectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var refreshes = 0
    private var settingsOpened = 0

    private fun show(segment: CollectionSegment) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CollectionScreen(
                    segment = segment,
                    onSegmentChange = {},
                    onOpenSettings = { settingsOpened++ },
                    catalogContent = { _, _ -> },
                    onRefreshCatalog = { refreshes++ },
                )
            }
        }
    }

    private fun openMenu() = compose.onNodeWithContentDescription("Weitere Optionen").performClick()

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
    fun myTeasHasNoCatalogEntryButTheSettings() {
        show(CollectionSegment.MyTeas)

        openMenu()
        compose.onNodeWithText("Katalog aktualisieren").assertDoesNotExist()
        compose.onNodeWithText("Einstellungen").performClick()

        assertThat(settingsOpened).isEqualTo(1)
        assertThat(refreshes).isEqualTo(0)
    }
}
