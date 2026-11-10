package com.umamit.feature.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.SettingsCatalogFoldRepository
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.catalogTea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The catalog kind in the view model and the list's helpers (#199, D-108): a third kind needs only an entry in the
 * kind list, folds are kept per kind and the stored ones of v0.4.1 read back. Invented teas (D-016).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogKindViewModelTest {
    private val oolong = CatalogKind("oolong", setOf("oolong"))
    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel Sencha"),
        catalogTea("XO0100", "Bergnebel", "Oolong").copy(kind = oolong),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(settings: FakeSettingsRepository) = CatalogViewModel(
        FakeCatalogRepository(CatalogOverview(teas, FakeClock.DEFAULT_WALL_MS)),
        FakeCatalogRefreshRunner(),
        folds = SettingsCatalogFoldRepository(settings),
    )

    @Test
    fun foldsOfAThirdKindAreKeptApartAndTheStoredOnesOfV041ReadBack() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Sencha", "herbal/Mischungen")))
        val viewModel = viewModel(settings)
        val collector = backgroundScope.launch { viewModel.state.collect { } }

        assertThat(viewModel.state.value.isFolded("Sencha")).isTrue()

        viewModel.changeFilter { it.withKind(oolong) }
        assertThat(viewModel.state.value.listing.teas.map { it.name }).containsExactly("Bergnebel")
        assertThat(viewModel.state.value.isFolded("Oolong")).isFalse()
        viewModel.toggleFold("Oolong")
        assertThat(settings.settings.first().catalogFolded)
            .containsExactly("green/Sencha", "herbal/Mischungen", "oolong/Oolong")
        assertThat(viewModel.state.value.isFolded("Oolong")).isTrue()

        viewModel.unfoldAll()
        assertThat(settings.settings.first().catalogFolded).containsExactly("green/Sencha", "herbal/Mischungen")
        collector.cancel()
    }

    @Test
    fun showAllSwitchesToTheKindWithTheHiddenHits() {
        val listing = CatalogListing(
            sections = emptyList(),
            grouped = true,
            totalCount = 1,
            hiddenMatches = 3,
            types = emptyList(),
            origins = emptyList(),
            lastRefreshAtMillis = null,
            hiddenInOtherKinds = mapOf(CatalogKind.HERBAL to 1, oolong to 2),
        )
        // Every hidden hit is of another kind: the one with the most is shown, without filters.
        val all = showAll(CatalogFilter(search = "x"), listing)
        assertThat(all.search).isEqualTo("x")
        assertThat(all.kind).isEqualTo(oolong)
        // Some hidden hits are of the same kind: stay, show them all.
        assertThat(
            showAll(CatalogFilter(search = "x"), listing.copy(hiddenMatches = 4)).kind,
        ).isEqualTo(CatalogKind.GREEN)
    }
}
