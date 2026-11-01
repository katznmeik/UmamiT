package com.umamit.feature.teaware

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeVesselArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The teaware list (SCR-13; WARE-01, D-100) on fake repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeawareViewModelTest {
    private val collection = FakeCollection()
    private val archive = FakeVesselArchive(FakeSessions(collection))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TeawareViewModel(collection.vessels, archive)

    private val TeawareViewModel.ready get() = state.value as TeawareUiState.Ready

    @Test
    fun `favourites come first, then A to Z, and archived vessels follow on their own`() = runTest {
        collection.vessels.saveVessel(Vessel("b", "Becher 300 ml", VesselType.MUG_INFUSER, 350, 300))
        collection.vessels.saveVessel(Vessel("o", "Öko-Glas", VesselType.GLASS, 400))
        collection.vessels.saveVessel(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250, isFavorite = true))
        collection.vessels.saveVessel(Vessel("g", "Gaiwan", VesselType.GAIWAN, 120))
        collection.vessels.saveVessel(Vessel("a", "Alte Kanne", VesselType.CLAY_TEAPOT, 500, isArchived = true))

        val state = viewModel().ready

        // „Öko" sorts with O (German collation), not after Z.
        assertThat(state.vessels.map { it.id }).containsExactly("k", "b", "g", "o").inOrder()
        assertThat(state.archived.map { it.id }).containsExactly("a")
        assertThat(state.vessels.first().isFavorite).isTrue()
    }

    @Test
    fun `a row shows the volume and the usable volume when it differs`() = runTest {
        collection.vessels.saveVessel(Vessel("k", "Kyusu", VesselType.KYUSU, 300, 250))
        collection.vessels.saveVessel(Vessel("b", "Becher", VesselType.MUG_INFUSER, 400, 400))

        val rows = viewModel().ready.vessels.associateBy { it.id }

        assertThat(rows.getValue("k").capacityMl).isEqualTo(300)
        assertThat(rows.getValue("k").usableMl).isEqualTo(250)
        assertThat(rows.getValue("b").usableMl).isNull()
    }

    @Test
    fun `Wiederherstellen brings an archived vessel back to the list`() = runTest {
        collection.vessels.saveVessel(Vessel("a", "Alte Kanne", VesselType.CLAY_TEAPOT, 500, isArchived = true))
        val vm = viewModel()

        vm.restore("a")

        assertThat(vm.ready.vessels.map { it.id }).containsExactly("a")
        assertThat(vm.ready.archived).isEmpty()
    }

    @Test
    fun `no vessels at all is an empty list`() = runTest {
        val state = viewModel().ready

        assertThat(state.vessels).isEmpty()
        assertThat(state.archived).isEmpty()
    }
}
