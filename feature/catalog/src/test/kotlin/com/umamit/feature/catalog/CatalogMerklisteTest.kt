package com.umamit.feature.catalog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.testing.FakeCatalogBookmarkRepository
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
 * The Merkliste of Sammlung › Katalog (v0.2.2, #117; CAT-18, SCR-19, D-097) on fake data: the
 * bookmark toggle of a row, and the *Gemerkt* chip with every marked tea, the other chips hidden,
 * the search applying and its empty state. Invented teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogMerklisteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel Sencha"),
        catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro"),
        catalogTea("XY0300", "Kirschblüte Sencha").copy(isFlavored = true),
        catalogTea("XY0400", "Schattenblatt Kabusecha", "Kabusecha").copy(owned = true),
        catalogTea("XY0170", "Nebelhang Sencha").copy(discontinued = true),
        catalogTea("XK0300", "Wiesenzauber", "Mischungen").copy(isHerbal = true, originCountry = null),
    )
    private val bookmarks = FakeCatalogBookmarkRepository()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(catalog: List<CatalogTeaSummary> = teas) {
        val repository = FakeCatalogRepository(
            CatalogOverview(catalog, FakeClock.DEFAULT_WALL_MS),
            bookmarks = bookmarks,
        )
        val viewModel = CatalogViewModel(repository, FakeCatalogRefreshRunner(), bookmarks = bookmarks)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = false, onOpenTea = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private val isChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun chip(@StringRes id: Int) = compose.onNode(hasText(str(id)) and isChip)

    private fun toggle(key: String) = compose.onNodeWithTag(catalogMarkTag(key))

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun hidden(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun aRowsToggleMarksAndUnmarksTheTea() {
        show()
        toggle("XY0100").assertIsOff().assertContentDescriptionEquals(str(R.string.catalog_mark))

        toggle("XY0100").performClick()
        compose.waitForIdle()

        assertThat(bookmarks.marked.value).containsExactly("XY0100")
        toggle("XY0100").assertIsOn().assertContentDescriptionEquals(str(R.string.catalog_marked))

        toggle("XY0100").performClick()
        compose.waitForIdle()

        assertThat(bookmarks.marked.value).isEmpty()
        toggle("XY0100").assertIsOff()
    }

    @Test
    fun aTeaInTheCollectionHasNoToggle() {
        // CAT-18 AC2: an owned tea offers no *Merken*; the teas beside it do.
        show()

        toggle("XY0200").assertIsDisplayed()
        shown("Schattenblatt Kabusecha")
        toggle("XY0400").assertDoesNotExist()
    }

    @Test
    fun gemerktShowsEveryMarkedTeaWithTheOtherChipsHidden() {
        bookmarks.marked.value = setOf("XY0200", "XY0300", "XY0170", "XK0300")
        show()
        chip(R.string.catalog_filter_marked).assertIsNotSelected()
        hidden("Nebelhang Sencha")

        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        // Green and herbal, flavoured and „Nicht mehr im Sortiment", grouped by Sorte (CAT-18 AC1).
        chip(R.string.catalog_filter_marked).assertIsSelected()
        compose.onNodeWithText("Tee-Kontor Kiel · 4 gemerkt · aktualisiert am 22. Okt.").assertIsDisplayed()
        shown("Tautropfen Gyokuro")
        shown("Kirschblüte Sencha")
        shown("Nebelhang Sencha")
        shown(str(R.string.catalog_tea_discontinued))
        shown("Wiesenzauber")
        hidden("Morgennebel Sencha")
        // Only the search applies: every other chip is hidden, *Filter* too.
        chip(R.string.catalog_filter_herbal).assertDoesNotExist()
        chip(R.string.catalog_filter_pure).assertDoesNotExist()
        chip(R.string.catalog_filter_available).assertDoesNotExist()
        chip(R.string.catalog_filter_owned).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.catalog_filter_button)).assertDoesNotExist()

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("sencha")
        compose.waitForIdle()

        shown("Kirschblüte Sencha")
        shown("Nebelhang Sencha")
        hidden("Tautropfen Gyokuro")
        // Morgennebel matches too, but it isn't marked: no hidden hits in this view.
        hidden("Morgennebel Sencha")
        hidden(str(R.string.catalog_show_all))

        compose.onNodeWithTag(CATALOG_SEARCH_TAG).performTextInput("x")
        compose.waitForIdle()
        shown(str(R.string.catalog_marked_no_match))
    }

    @Test
    fun leavingGemerktReturnsToTheViewBefore() {
        bookmarks.marked.value = setOf("XK0300")
        show()
        chip(R.string.catalog_filter_herbal).performClick()
        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        chip(R.string.catalog_filter_herbal).assertIsSelected()
        val herbalCount = compose.activity.resources.getQuantityString(R.plurals.catalog_herbal_count, 1, 1)
        shown(str(R.string.catalog_header, herbalCount, "22. Okt."))
        shown("Wiesenzauber")
    }

    @Test
    fun unmarkingInGemerktTakesTheTeaOffTheList() {
        bookmarks.marked.value = setOf("XY0200", "XY0300")
        show()
        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        toggle("XY0200").performClick()
        compose.waitForIdle()

        hidden("Tautropfen Gyokuro")
        compose.onNodeWithText("Tee-Kontor Kiel · 1 gemerkt · aktualisiert am 22. Okt.").assertIsDisplayed()
    }

    @Test
    fun gemerktWithoutMarksShowsItsEmptyState() {
        show()

        chip(R.string.catalog_filter_marked).performClick()
        compose.waitForIdle()

        shown(str(R.string.catalog_marked_empty_title))
        shown(str(R.string.catalog_marked_empty_body))
        compose.onNodeWithText("Tee-Kontor Kiel · 0 gemerkt · aktualisiert am 22. Okt.").assertIsDisplayed()
        hidden(str(R.string.catalog_no_match))
        // Still the list, not the empty catalog.
        hidden(str(R.string.catalog_empty_title))
    }
}
