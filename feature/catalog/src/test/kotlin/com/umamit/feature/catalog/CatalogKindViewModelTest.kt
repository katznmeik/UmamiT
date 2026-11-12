package com.umamit.feature.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.SettingsCatalogFoldRepository
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.FlavourFilter
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
 * kind list, folds are kept per kind and the stored ones of v0.4.1 read back; without a kind (D-114, #247) the kind
 * groups fold as `all/<kind>`. Invented teas (D-016).
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

        viewModel.changeFilter { it.withKind(CatalogKind.GREEN) }
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
    fun withoutAKindTheKindGroupsFoldUnderTheirOwnKeys() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettingsRepository(UserSettings(catalogFolded = setOf("green/Sencha")))
        val viewModel = viewModel(settings)
        val collector = backgroundScope.launch { viewModel.state.collect { } }
        val state = { viewModel.state.value }

        // The default view has no kind (D-114): the *Sorte* folds of the green view don't apply in it.
        assertThat(state().filter.kind).isNull()
        assertThat(state().foldable).isTrue()
        assertThat(state().isFolded("Sencha")).isFalse()
        assertThat(state().isKindFolded(CatalogKind.GREEN)).isFalse()

        viewModel.toggleKindFold(CatalogKind.GREEN)
        assertThat(settings.settings.first().catalogFolded).containsExactly("green/Sencha", "all/green")
        assertThat(state().isKindFolded(CatalogKind.GREEN)).isTrue()
        // A *Sorte* header's tap does nothing without a kind.
        viewModel.toggleFold("Sencha")
        assertThat(settings.settings.first().catalogFolded).containsExactly("green/Sencha", "all/green")

        viewModel.foldAll()
        assertThat(settings.settings.first().catalogFolded)
            .containsExactlyElementsIn(setOf("green/Sencha") + CatalogKind.ALL.map { "all/${it.key}" })
        viewModel.unfoldAll()
        assertThat(settings.settings.first().catalogFolded).containsExactly("green/Sencha")

        // A flat sort keeps the kind groups and their folding; a typed search unfolds every group.
        viewModel.toggleKindFold(CatalogKind.GREEN)
        viewModel.changeFilter { it.copy(sort = CatalogSort.NAME) }
        assertThat(state().isKindFolded(CatalogKind.GREEN)).isTrue()
        viewModel.changeFilter { it.copy(search = "nebel") }
        assertThat(state().foldable).isFalse()
        assertThat(state().isKindFolded(CatalogKind.GREEN)).isFalse()
        collector.cancel()
    }

    @Test
    fun showAllClearsTheKindAndTheFilters() {
        val all = showAll(CatalogFilter(search = "x", kind = oolong, organicOnly = true))
        assertThat(all).isEqualTo(
            CatalogFilter(search = "x", flavour = FlavourFilter.ALL, availableOnly = false),
        )
        assertThat(all.kind).isNull()
    }
}
