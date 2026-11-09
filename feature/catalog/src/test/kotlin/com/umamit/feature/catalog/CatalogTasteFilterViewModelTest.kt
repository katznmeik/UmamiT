package com.umamit.feature.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.TasteScale
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.catalogTea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Choosing and resetting the flavour filter (v0.5.0, #204, D-108 point 8, CAT-10). Invented teas (D-016). */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogTasteFilterViewModelTest {
    private val teas = listOf(
        catalogTea("XY0100", "Morgennebel")
            .copy(tasteNotes = listOf("fruchtig", "mild"), tasteScales = listOf(TasteScale("Süße", 3))),
        catalogTea("XY0200", "Abendrot").copy(tasteNotes = listOf("mild"), tasteScales = listOf(TasteScale("Süße", 1))),
        catalogTea("XS0100", "Bergfeuer", "Assam")
            .copy(kind = CatalogKind.BLACK, tasteNotes = listOf("malzig", "mild")),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CatalogViewModel(
        FakeCatalogRepository(CatalogOverview(teas, FakeClock.DEFAULT_WALL_MS)),
        FakeCatalogRefreshRunner(),
    )

    @Test
    fun choosingNotesAndScalesNarrowsTheListAndResetClearsThem() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.state.collect { } }
        fun names() = viewModel.state.value.listing.teas.map { it.name }

        assertThat(viewModel.state.value.listing.tasteNotes.map { it.label }).containsExactly("mild", "fruchtig")
        viewModel.changeFilter { it.toggleNote("fruchtig") }
        assertThat(names()).containsExactly("Morgennebel")
        viewModel.changeFilter { it.toggleNote("fruchtig").withMinScale("suße", 1) }
        assertThat(names()).containsExactly("Abendrot", "Morgennebel")
        viewModel.changeFilter { it.toggleNote("mild").withMinScale("suße", 3) }
        assertThat(names()).containsExactly("Morgennebel")
        assertThat(viewModel.state.value.filter.changedFilters).isEqualTo(2)

        viewModel.changeFilter { it.resetFilters() }
        assertThat(viewModel.state.value.filter.notes).isEmpty()
        assertThat(viewModel.state.value.filter.minScales).isEmpty()
        assertThat(names()).containsExactly("Abendrot", "Morgennebel")
        collector.cancel()
    }

    @Test
    fun aKindChangeKeepsOnlyTheNotesTheNewKindHas() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.state.collect { } }

        viewModel.changeFilter { it.toggleNote("fruchtig").toggleNote("mild").withMinScale("suße", 2) }
        viewModel.changeFilter { it.withKind(CatalogKind.BLACK) }

        assertThat(viewModel.state.value.filter.notes).containsExactly("mild")
        assertThat(viewModel.state.value.filter.minScales).isEmpty()
        assertThat(viewModel.state.value.listing.teas.map { it.name }).containsExactly("Bergfeuer")
        collector.cancel()
    }
}
