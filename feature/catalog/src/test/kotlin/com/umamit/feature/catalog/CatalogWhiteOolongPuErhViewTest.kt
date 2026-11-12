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
 * The *Weißer Tee*, *Oolong* and *Pu-Erh* views of Sammlung › Katalog (v0.5.0, #205; CAT-10, SCR-19, D-108) on fake
 * data: each chip with its header count, its *Sorte* groups folding apart from the other views', search hits hidden in
 * the green view, and each view's hint before the first refresh with parser v9. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogWhiteOolongPuErhViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun of(kind: CatalogKind, key: String, name: String, sorte: String?): CatalogTeaSummary =
        catalogTea(key, name, sorte).copy(kind = kind, originCountry = null)

    private val green = listOf(catalogTea("XY0100", "Morgennebel Sencha"))
    private val teas = green + listOf(
        of(CatalogKind.WHITE, "XW0100", "Nebelgarten Bai Mu Dan", "Bai Mu Dan"),
        of(CatalogKind.WHITE, "XW0200", "Mandelwolke", null),
        of(CatalogKind.OOLONG, "XO0100", "Bergnebel Oolong", "Taiwan"),
        of(CatalogKind.OOLONG, "XO0200", "Formosa Morgentau", "Taiwan"),
        of(CatalogKind.OOLONG, "XO0300", "Felsentee Nebelberg", "China"),
        of(CatalogKind.DARK, "XP0100", "Nebelberg Bing Cha", "Sheng"),
        of(CatalogKind.DARK, "XP0200", "Waldboden Nebelberg", "Shou"),
    )
    private val settings = FakeSettingsRepository()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(catalog: List<CatalogTeaSummary> = teas, pending: Set<CatalogKind> = emptySet()) {
        val repository = FakeCatalogRepository(CatalogOverview(catalog, FakeClock.DEFAULT_WALL_MS))
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

    private fun switchTo(@StringRes chip: Int) {
        chip(chip).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun hidden(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun eachChipShowsItsKindsTeasBySorteWithTheirCount() {
        show()
        hidden("Nebelberg Bing Cha")

        switchTo(R.string.catalog_filter_white)
        chip(R.string.catalog_filter_white).assertIsSelected()
        shown("Tee-Kontor Kiel · 2 Tees · aktualisiert am 22. Okt.")
        compose.onNodeWithTag(catalogGroupTag("Bai Mu Dan")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag(null)).assertIsDisplayed()
        hidden("Bergnebel Oolong")

        switchTo(R.string.catalog_filter_oolong)
        shown("Tee-Kontor Kiel · 3 Tees · aktualisiert am 22. Okt.")
        compose.onNodeWithTag(catalogGroupTag("Taiwan")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("China")).assertIsDisplayed()
        hidden("Mandelwolke")

        switchTo(R.string.catalog_filter_dark)
        shown("Tee-Kontor Kiel · 2 Tees · aktualisiert am 22. Okt.")
        compose.onNodeWithTag(catalogGroupTag("Sheng")).assertIsDisplayed()
        compose.onNodeWithTag(catalogGroupTag("Shou")).assertIsDisplayed()
        shown("Nebelberg Bing Cha")
        hidden("Morgennebel Sencha")
    }

    @Test
    fun aFoldedSorteIsKeptPerView() {
        show()
        switchTo(R.string.catalog_filter_oolong)

        compose.onNodeWithTag(catalogGroupTag("Taiwan")).performClick()
        compose.waitForIdle()
        hidden("Bergnebel Oolong")
        shown("Felsentee Nebelberg")

        switchTo(R.string.catalog_filter_dark)
        compose.onNodeWithTag(catalogGroupTag("Sheng")).performClick()
        compose.waitForIdle()
        hidden("Nebelberg Bing Cha")

        assertThat(runBlocking { settings.settings.first().catalogFolded })
            .containsExactly("oolong/Taiwan", "dark/Sheng")
    }

    @Test
    fun searchHitsAmongTheNewKindsAreHiddenInTheGreenViewAndShowAllShowsEveryKind() {
        show()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("nebelberg")
        compose.waitForIdle()
        // One oolong and two Pu-Erhs are hidden: *Alle zeigen* clears the kind (D-114).
        shown(compose.activity.resources.getQuantityString(R.plurals.catalog_hidden_matches, 3, 3))

        compose.onNodeWithText(str(R.string.catalog_show_all)).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_dark).assertIsNotSelected()
        shown("Felsentee Nebelberg")
        shown("Nebelberg Bing Cha")
        shown("Waldboden Nebelberg")
    }

    @Test
    fun beforeTheFirstRefreshWithParserV9EachNewViewShowsItsOwnHint() {
        show(green, pending = setOf(CatalogKind.WHITE, CatalogKind.OOLONG, CatalogKind.DARK))
        hidden(str(R.string.catalog_white_pending))

        switchTo(R.string.catalog_filter_white)
        shown(str(R.string.catalog_white_pending))
        hidden(str(R.string.catalog_no_match))

        switchTo(R.string.catalog_filter_oolong)
        shown(str(R.string.catalog_oolong_pending))
        hidden(str(R.string.catalog_white_pending))

        switchTo(R.string.catalog_filter_dark)
        shown(str(R.string.catalog_dark_pending))
        shown(str(R.string.catalog_herbal_load_now))

        switchTo(R.string.catalog_filter_black)
        hidden(str(R.string.catalog_black_pending))
        hidden(str(R.string.catalog_dark_pending))
    }
}
