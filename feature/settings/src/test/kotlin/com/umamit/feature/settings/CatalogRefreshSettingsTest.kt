package com.umamit.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.catalog.BlockCause
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.reliability.ReliabilityStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Katalog* in the settings (SCR-25): the running refresh's bar and line and its result line (SET-23, #319). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogRefreshSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val catalog = FakeCatalogRepository()
    private val catalogRefresh = FakeCatalogRefreshRunner()

    private fun show() {
        val viewModel = CatalogSettingsViewModel(catalog, catalogRefresh)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = UserSettings(),
                    status = ReliabilityStatus.AllOk,
                    alerting = false,
                    onBack = {},
                    actions = SettingsActions(),
                    catalog = state,
                )
            }
        }
    }

    @Test
    fun `Running shows the bar and the counts, and no Abbrechen`() {
        show()
        compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH).assertDoesNotExist()

        catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
        compose.onNodeWithText("Sitemap wird gelesen …").performScrollTo().assertIsDisplayed()
        catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(2, 4, 1))

        compose.onNodeWithText("Seiten: 2 von 4 · Produkte: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH).assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").assertDoesNotExist()
    }

    @Test
    fun `a refresh started elsewhere shows the same way`() {
        catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(1, 3, 0))
        show()

        compose.onNodeWithText("Wird gerade aktualisiert …").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seiten: 1 von 3 · Produkte: 0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `each outcome shows its result line when the refresh ends`() {
        show()
        val outcomes = listOf(
            CatalogRefreshOutcome.Success(teas = 274, newTeas = 12) to "274\u00A0Tees\u00A0· 12\u00A0neu",
            CatalogRefreshOutcome.Blocked(BlockCause.CHALLENGE, "details: challenge") to
                "Der Shop hat den Abruf vorübergehend blockiert – bitte später erneut versuchen.",
            CatalogRefreshOutcome.Offline("details: offline") to
                "Keine Verbindung zum Shop – bitte später erneut versuchen.",
            CatalogRefreshOutcome.Cancelled to "Laden abgebrochen",
        )
        for ((outcome, line) in outcomes) {
            catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(1, 3, 0))
            compose.waitForIdle()
            catalogRefresh.state.value = CatalogRefreshState.Finished(outcome)

            compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH_LINE).performScrollTo()
                .assertTextEquals(line, includeEditableText = false)
            compose.onNodeWithText("Jetzt aktualisieren").assertIsEnabled()
        }
    }

    @Test
    fun `the last line stays drawn while the status leaves (D-117)`() {
        show()
        catalogRefresh.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(2, 4, 1))
        compose.onNodeWithText("Seiten: 2 von 4 · Produkte: 1").performScrollTo().assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        catalogRefresh.state.value = CatalogRefreshState.Idle
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH_LINE).assertTextEquals("Seiten: 2 von 4 · Produkte: 1")

        compose.mainClock.advanceTimeBy(LEAVE_MS)
        compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH).assertDoesNotExist()
    }

    @Test
    fun `reopening the settings after a refresh shows the date only`() {
        catalog.catalog.value = CatalogOverview(emptyList(), lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS)
        catalogRefresh.state.value =
            CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 274, newTeas = 12))
        show()

        compose.onNodeWithText("22. Okt. 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.CATALOG_REFRESH).assertDoesNotExist()
    }

    private companion object {
        const val LEAVE_MS = 1_000L
    }
}
