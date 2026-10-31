package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.SettingsCatalogFoldRepository
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogBookmarkRepository
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
 * Foldable *Sorte* groups of Sammlung › Katalog (v0.3.0, #131; CAT-10, SCR-19, D-098) on fake data and the real
 * settings-backed fold store: a tap on a header folds and unfolds its group, remembered per view across a new view
 * model (an app restart), a search shows the hits of folded groups, ⋮ *Alle einklappen* / *Alle ausklappen*,
 * no folding in *Gemerkt* and the flat sort orders, and TalkBack's text and state. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogFoldingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel Sencha"),
        catalogTea("XY0110", "Hanglage Sencha"),
        catalogTea("XY0200", "Herbstlaub Bancha", "Bancha"),
        catalogTea("XY0210", "Flussufer Bancha", "Bancha"),
        catalogTea("XY0300", "Röstreis Genmaicha", "Genmaicha"),
        catalogTea("XY0400", "Namenloser Grüner", null),
        catalogTea("XK0100", "Wiesenzauber", "Mischungen").copy(isHerbal = true, originCountry = null),
    )
    private var settings = FakeSettingsRepository()
    private val bookmarks = FakeCatalogBookmarkRepository()
    private var foldAll by mutableIntStateOf(0)
    private var unfoldAll by mutableIntStateOf(0)
    private var foldable by mutableStateOf<Boolean?>(null)

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun newViewModel() = CatalogViewModel(
        FakeCatalogRepository(CatalogOverview(teas, FakeClock.DEFAULT_WALL_MS), bookmarks = bookmarks),
        FakeCatalogRefreshRunner(),
        bookmarks = bookmarks,
        folds = SettingsCatalogFoldRepository(settings),
    )

    private fun show(viewModel: CatalogViewModel = newViewModel()) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(
                    alerting = false,
                    onOpenTea = {},
                    viewModel = viewModel,
                    foldAllRequest = foldAll,
                    unfoldAllRequest = unfoldAll,
                    onFoldableChange = { foldable = it },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun header(teaType: String?) = compose.onNodeWithTag(catalogGroupTag(teaType))

    private fun shown(text: String) {
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun hidden(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    private fun stored(): Set<String> = runBlocking { settings.settings.first().catalogFolded }

    private fun search(text: String) {
        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput(text)
        compose.waitForIdle()
    }

    private fun chip(@StringRes id: Int) =
        compose.onNode(hasText(str(id)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))

    @Test
    fun aTapOnAHeaderFoldsAndUnfoldsItsGroup() {
        show()
        shown("Herbstlaub Bancha")

        header("Bancha").performClick()
        compose.waitForIdle()

        hidden("Herbstlaub Bancha")
        hidden("Flussufer Bancha")
        shown("Morgennebel Sencha")
        header("Bancha").assertIsDisplayed()
        // The header keeps its count while folded („Bancha · 2").
        header("Bancha").assertContentDescriptionEquals("Bancha, 2 Tees")
        compose.onNodeWithText("BANCHA · 2").assertIsDisplayed()
        assertThat(stored()).containsExactly("green/Bancha")

        header("Bancha").performClick()
        compose.waitForIdle()

        shown("Herbstlaub Bancha")
        assertThat(stored()).isEmpty()
    }

    @Test
    fun foldedGroupsAreRememberedAcrossARestartPerView() {
        settings =
            FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Bancha", "green/-", "herbal/Mischungen")))
        show()

        // A new view model on the stored setting, as after an app start: Bancha and „Ohne Sorte" stay folded.
        hidden("Herbstlaub Bancha")
        hidden("Namenloser Grüner")
        shown("Röstreis Genmaicha")

        chip(R.string.catalog_filter_herbal).performClick()
        compose.waitForIdle()

        // The Kräutertee view keeps its own: Mischungen folded there.
        hidden("Wiesenzauber")
        header("Mischungen").performClick()
        compose.waitForIdle()
        shown("Wiesenzauber")
        assertThat(stored()).containsExactly("green/Bancha", "green/-")
    }

    @Test
    fun aSearchShowsTheHitsOfFoldedGroupsAndClearingItFoldsThemAgain() {
        settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Bancha")))
        show()
        hidden("Herbstlaub Bancha")

        search("herbstlaub")

        shown("Herbstlaub Bancha")
        // While searching the folding is ignored: the headers are no toggles.
        header("Bancha").assertDoesNotExist()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextClearance()
        compose.waitForIdle()

        hidden("Herbstlaub Bancha")
        assertThat(stored()).containsExactly("green/Bancha")
    }

    @Test
    fun foldAllAndUnfoldAllFromTheMenu() {
        settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("herbal/Mischungen")))
        show()
        assertThat(foldable).isTrue()

        foldAll++
        compose.waitForIdle()

        hidden("Morgennebel Sencha")
        hidden("Herbstlaub Bancha")
        hidden("Röstreis Genmaicha")
        hidden("Namenloser Grüner")
        assertThat(stored()).containsExactly(
            "herbal/Mischungen",
            "green/Sencha",
            "green/Bancha",
            "green/Genmaicha",
            "green/-",
        )

        unfoldAll++
        compose.waitForIdle()

        shown("Morgennebel Sencha")
        shown("Namenloser Grüner")
        // Only the shown view unfolds; the Kräutertee view keeps its folding.
        assertThat(stored()).containsExactly("herbal/Mischungen")
    }

    @Test
    fun gemerktAndTheFlatOrdersHaveNoFolding() {
        settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Bancha")))
        bookmarks.marked.value = setOf("XY0200")
        show()

        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        shown("Herbstlaub Bancha")
        header("Bancha").assertDoesNotExist()
        assertThat(foldable).isFalse()
    }

    @Test
    fun aFlatSortOrderHasNoFolding() {
        settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Bancha")))
        val viewModel = newViewModel()
        viewModel.changeFilter { it.copy(sort = CatalogSort.NAME) }
        show(viewModel)

        shown("Herbstlaub Bancha")
        header("Bancha").assertDoesNotExist()
        assertThat(foldable).isFalse()
    }

    @Test
    fun talkBackReadsTheGroupItsCountAndItsState() {
        settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Bancha")))
        show()

        header("Bancha")
            .assertContentDescriptionEquals(
                compose.activity.resources.getQuantityString(R.plurals.catalog_group_description, 2, "Bancha", 2),
            )
            .assert(state(str(R.string.catalog_group_folded)))
            .assert(clickLabel(str(R.string.catalog_group_unfold)))
        header("Sencha")
            .assert(state(str(R.string.catalog_group_unfolded)))
            .assert(clickLabel(str(R.string.catalog_group_fold)))
        assertThat(compose.activity.resources.getQuantityString(R.plurals.catalog_group_description, 12, "Bancha", 12))
            .isEqualTo("Bancha, 12 Tees")
        assertThat(str(R.string.catalog_group_folded)).isEqualTo("eingeklappt")
    }

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
}
