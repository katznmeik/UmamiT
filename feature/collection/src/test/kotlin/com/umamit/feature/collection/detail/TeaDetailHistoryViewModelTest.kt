package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId

/** VORRAT's stock history (INV-03, SCR-29, D-104; spec #171 seam 2): its rows, newest first, and their delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailHistoryViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val clock = FakeClock(zone = ZoneId.of("Europe/Berlin"))
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TeaDetailViewModel(
        SavedStateHandle(mapOf("teaId" to TEA_ID)),
        collection.teas,
        collection.recipes,
        collection.vessels,
        collection.stock,
        sessions.journal,
        settings,
        FakeCatalogRepository(details = listOf(TeaDetailFixture.catalogTea)),
        FakeTeaArchive(sessions),
        photos,
        FakeTeaPhotos(collection, photos),
        clock,
    )

    private val TeaDetailViewModel.tea get() = (state.value as TeaDetailUiState.Ready).tea

    /** The fixture's 50 g package (1 Jan 1970), then a purchase, a brew and a correction in Oct 2026. */
    private suspend fun seedHistory() {
        TeaDetailFixture.seed(collection)
        collection.stock.addEvent(
            InventoryEvent("p1", TEA_ID, InventoryEventType.PURCHASE, 100.0, OCT_20, priceCents = 1890),
        )
        collection.stock.addEvent(
            InventoryEvent("b1", TEA_ID, InventoryEventType.BREW, -5.0, OCT_21, sessionId = "s1", currency = null),
        )
        // 00:30 on 23 Oct in Berlin, still 22 Oct in UTC: the row's day is the phone's.
        collection.stock.addEvent(InventoryEvent("a1", TEA_ID, InventoryEventType.ADJUSTMENT, -15.0, OCT_23_BERLIN))
    }

    @Test
    fun `the history lists every event newest first with its kind, grams, price and day`() = runTest {
        seedHistory()

        assertThat(viewModel().tea.history).containsExactly(
            StockEntry("a1", StockEntryKind.CORRECTION, -15.0, null, LocalDate.of(2026, 10, 23)),
            StockEntry("b1", StockEntryKind.BREW, -5.0, null, LocalDate.of(2026, 10, 21), sessionId = "s1"),
            StockEntry("p1", StockEntryKind.PURCHASE, 100.0, 1890, LocalDate.of(2026, 10, 20)),
            StockEntry("initial", StockEntryKind.PURCHASE, 50.0, 990, LocalDate.of(1970, 1, 1)),
        ).inOrder()
    }

    @Test
    fun `only a brew row can't be deleted`() = runTest {
        seedHistory()

        assertThat(viewModel().tea.history.associate { it.id to it.canDelete }).containsExactly(
            "a1",
            true,
            "b1",
            false,
            "p1",
            true,
            "initial",
            true,
        )
    }

    @Test
    fun `deleting a purchase updates the stock and the history at once`() = runTest {
        seedHistory()
        val vm = viewModel()

        vm.deleteStockEntry("p1")

        assertThat(vm.tea.history.map { it.id }).containsExactly("a1", "b1", "initial").inOrder()
        assertThat(vm.tea.stockGrams).isEqualTo(30.0)
    }

    @Test
    fun `deleting a correction gives its grams back`() = runTest {
        seedHistory()
        val vm = viewModel()

        vm.deleteStockEntry("a1")

        assertThat(vm.tea.stockGrams).isEqualTo(145.0)
    }

    @Test
    fun `a brew is refused and stays in the history`() = runTest {
        seedHistory()
        val vm = viewModel()

        vm.deleteStockEntry("b1")

        assertThat(vm.tea.history.map { it.id }).contains("b1")
        assertThat(vm.tea.stockGrams).isEqualTo(130.0)
    }

    @Test
    fun `a tea without stock events has no history`() = runTest {
        TeaDetailFixture.seed(collection, grams = 0.0)

        assertThat(viewModel().tea.history).isEmpty()
    }

    private companion object {
        /** 20 Oct 2026, 10:00 UTC. */
        const val OCT_20 = 1_792_490_400_000L

        /** 21 Oct 2026, 08:00 UTC. */
        const val OCT_21 = 1_792_569_600_000L

        /** 23 Oct 2026, 00:30 in Berlin (22 Oct, 22:30 UTC). */
        const val OCT_23_BERLIN = 1_792_708_200_000L
    }
}
