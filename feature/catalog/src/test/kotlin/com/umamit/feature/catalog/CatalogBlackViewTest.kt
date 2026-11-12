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
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.SettingsCatalogFoldRepository
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.catalogTea
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The *Schwarztee* view of Sammlung › Katalog (v0.5.0, #203; CAT-10, SCR-19, D-108) on fake data: its chip, the
 * header's „N Schwarztees", its *Sorte* groups folding apart from the green view's, a search hit among the black teas
 * hidden in the green view, and its hint before the first refresh after the update. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogBlackViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun black(key: String, name: String, sorte: String?): CatalogTeaSummary =
        catalogTea(key, name, sorte).copy(kind = CatalogKind.BLACK, originCountry = null)

    private val green = listOf(
        catalogTea("XY0100", "Morgennebel Sencha"),
        catalogTea("XY0200", "Herbstlaub Bancha", "Bancha"),
    )
    private val teas = green + listOf(
        black("XB0100", "Nebelhang Darjeeling", "Darjeeling"),
        black("XB0200", "Hochweide Darjeeling", "Darjeeling"),
        black("XB0300", "Flussbiegung Assam", "Assam"),
        black("XB0400", "Hafenkontor Morgentee", null),
    )
    private val settings = FakeSettingsRepository()
    private lateinit var repository: FakeCatalogRepository

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(catalog: List<CatalogTeaSummary> = teas, pending: Set<CatalogKind> = emptySet()) {
        repository = FakeCatalogRepository(CatalogOverview(catalog, FakeClock.DEFAULT_WALL_MS))
        repository.pendingKinds.value = pending
        val viewModel = CatalogViewModel(
            repository,
            FakeCatalogRefreshRunner(),
            folds = SettingsCatalogFoldRepository(settings),
        )
        // From the Grüntee view: no kind is the default since v0.6.1 (D-114).
        viewModel.changeFilter { it.withKind(CatalogKind.GREEN) }
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

    private fun switchToBlack() {
        chip(R.string.catalog_filter_black).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private val hint get() = str(R.string.catalog_black_pending)

    @Test
    fun theChipShowsTheBlackTeasBySorteWithTheirCount() {
        show()
        hidden("Nebelhang Darjeeling")

        switchToBlack()

        chip(R.string.catalog_filter_black).assertIsSelected()
        shown("Tee-Kontor Kiel · 4 Tees · aktualisiert am 22. Okt.")
        compose.onNodeWithTag(catalogGroupTag("Assam")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Darjeeling")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag(null)).assertIsDisplayed()
        shown("Nebelhang Darjeeling")
        shown("Hafenkontor Morgentee")
        hidden("Morgennebel Sencha")
    }

    @Test
    fun aFoldedSorteOfTheBlackViewIsKeptApartFromTheGreenView() {
        show()
        switchToBlack()

        compose.onNodeWithTag(catalogGroupTag("Darjeeling")).performClick()
        compose.waitForIdle()

        hidden("Nebelhang Darjeeling")
        hidden("Hochweide Darjeeling")
        shown("Flussbiegung Assam")
        assertThat(runBlocking { settings.settings.first().catalogFolded }).containsExactly("black/Darjeeling")

        chip(R.string.catalog_filter_black).performScrollTo().performClick()
        compose.waitForIdle()
        shown("Herbstlaub Bancha")
    }

    @Test
    fun aSearchHitAmongTheBlackTeasIsHiddenInTheGreenViewAndShowAllShowsEveryKind() {
        show()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("darjeeling")
        compose.waitForIdle()
        shown(compose.activity.resources.getQuantityString(R.plurals.catalog_hidden_matches, 2, 2))

        compose.onNodeWithText(str(R.string.catalog_show_all)).performClick()
        compose.waitForIdle()

        // *Alle zeigen* clears the kind (D-114).
        chip(R.string.catalog_filter_black).assertIsNotSelected()
        chip(R.string.catalog_filter_green).assertIsNotSelected()
        shown("Nebelhang Darjeeling")
        shown("Hochweide Darjeeling")
    }

    @Test
    fun beforeTheFirstRefreshAfterTheUpdateTheBlackViewShowsItsHintAndTheOthersDoNot() {
        show(green, pending = setOf(CatalogKind.BLACK))
        hidden(hint)

        switchToBlack()

        shown(hint)
        shown(str(R.string.catalog_herbal_load_now))
        hidden(str(R.string.catalog_no_match))

        chip(R.string.catalog_filter_herbal).performScrollTo().performClick()
        compose.waitForIdle()
        hidden(hint)
        hidden(str(R.string.catalog_herbal_pending))
    }
}
