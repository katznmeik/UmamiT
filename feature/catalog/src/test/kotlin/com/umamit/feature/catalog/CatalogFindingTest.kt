package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
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
 * Finding teas in Sammlung › Katalog (CAT-10, #69) on fake data: the default view (D-073),
 * toggling „Pur", the search with its hidden hits, the badges, the filter sheet and alerts
 * first (rule 8). Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogFindingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var alerting by mutableStateOf(false)

    private val repository = FakeCatalogRepository(
        CatalogOverview(
            teas = listOf(
                catalogTea("XY0100", "Morgennebel Sencha").copy(isOrganic = true, owned = true),
                catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro").copy(isNew = true, needsRecipeCheck = true),
                catalogTea("XY0300", "Kirsch Sencha").copy(isFlavored = true),
                catalogTea("XY0400", "Stiller See Gyokuro", "Gyokuro").copy(available = false),
            ),
            lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS,
        ),
    )

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show() {
        val viewModel = CatalogViewModel(repository, FakeCatalogRefreshRunner())
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = alerting, onOpenTea = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private val isChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun chip(@StringRes id: Int) = compose.onNode(hasText(str(id)) and isChip)

    private fun shown(name: String) = compose.onNodeWithText(name).assertIsDisplayed()

    private fun hidden(name: String) = compose.onNodeWithText(name).assertDoesNotExist()

    @Test
    fun theDefaultViewGroupsAvailablePureTeasBySorte() {
        show()

        compose.onNodeWithText("Tee-Kontor Kiel · 4 Grüntees · aktualisiert am 22. Okt.").assertIsDisplayed()
        shown("GYOKURO")
        shown("SENCHA")
        shown("Morgennebel Sencha")
        shown("Tautropfen Gyokuro")
        hidden("Kirsch Sencha")
        hidden("Stiller See Gyokuro")
        chip(R.string.catalog_filter_pure).assertIsSelected()
        chip(R.string.catalog_filter_available).assertIsSelected()
    }

    @Test
    fun oneTapOnPurShowsTheFlavouredTeas() {
        show()

        chip(R.string.catalog_filter_pure).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_pure).assertIsNotSelected()
        shown("Kirsch Sencha")
        compose.onNodeWithText(str(R.string.catalog_filter_button_count, 1)).assertIsDisplayed()
    }

    @Test
    fun theSearchNarrowsTheListAndCountsTheHitsTheFiltersHide() {
        show()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("sencha")
        compose.waitForIdle()

        shown("Morgennebel Sencha")
        hidden("Tautropfen Gyokuro")
        val hiddenHits = compose.activity.resources.getQuantityString(R.plurals.catalog_hidden_matches, 1, 1)
        compose.onNodeWithText(hiddenHits).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.catalog_show_all)).performClick()
        compose.waitForIdle()
        shown("Kirsch Sencha")
    }

    @Test
    fun rowsCarryTheirBadges() {
        show()

        // Each row is one TalkBack item: its badges are part of it.
        compose.onNode(
            hasText("Tautropfen Gyokuro") and hasText(str(R.string.catalog_badge_new)) and
                hasText(str(R.string.catalog_badge_recipe_check)),
        ).assertIsDisplayed()
        compose.onNode(
            hasText("Morgennebel Sencha") and hasText(str(R.string.catalog_badge_owned)) and
                hasText(str(R.string.catalog_tea_organic)),
        ).assertIsDisplayed()
    }

    @Test
    fun aChipNarrowsTheListAndNothingMatchingOffersTheReset() {
        show()

        chip(R.string.catalog_filter_new).performClick()
        compose.waitForIdle()
        shown("Tautropfen Gyokuro")
        hidden("Morgennebel Sencha")

        chip(R.string.catalog_filter_organic).performClick()
        compose.waitForIdle()
        shown(str(R.string.catalog_no_match))
        compose.onNodeWithText(str(R.string.catalog_reset_filters)).performClick()
        compose.waitForIdle()
        shown("Morgennebel Sencha")
        shown("Tautropfen Gyokuro")
    }

    @Test
    fun theFilterSheetSortsAndFiltersAndAnAlertRemovesIt() {
        show()

        chip(R.string.catalog_filter_button).performClick()
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag(CATALOG_FILTER_SHEET_TAG)
        sheet.assertIsDisplayed()
        compose.onNode(hasText(str(R.string.catalog_sort_name)) and isChip).performClick()
        compose.waitForIdle()
        // Sorted by name: no groups any more.
        hidden("GYOKURO")

        alerting = true
        compose.waitForIdle()
        sheet.assertDoesNotExist()
        alerting = false
        compose.waitForIdle()
        sheet.assertDoesNotExist()
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText("Tautropfen Gyokuro"))
    }
}
