package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.TeaPurchase
import com.umamit.core.domain.collection.LiterAverage
import com.umamit.core.domain.collection.TeaCost
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
import com.umamit.feature.collection.detail.TeaDetailFixture.LAST_BREW_MS
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
import java.time.Instant
import java.time.LocalDate

/** VORRAT's cost (INV-08, D-104; spec #171 seam 2): from the stock, the default recipe and the priced sessions. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailCostViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val clock = FakeClock()
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val today: LocalDate get() = Instant.ofEpochMilli(clock.wall).atZone(clock.zone).toLocalDate()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.stock.now = clock.wall
        collection.stock.zone = clock.zone
    }

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

    @Test
    fun `a priced tea has its price, the default recipe's estimate and no average before a priced session`() = runTest {
        // 50 g for 9,90 €: 19,8 ct per gram; the default Kyusu: 99 ct over 3 × 250 ml = 132 ct per litre.
        TeaDetailFixture.seed(collection)

        assertThat(viewModel().tea.cost).isEqualTo(TeaCost(pricePer100gCents = 1980, recipeLiterCents = 132))
    }

    @Test
    fun `a finished priced session gives the average`() = runTest {
        TeaDetailFixture.seed(collection)
        // 5 g × 19,8 ct = 99 ct for one poured infusion of 250 ml → 396 ct per litre.
        TeaDetailFixture.brewed(sessions, "s1", at = LAST_BREW_MS)

        assertThat(viewModel().tea.cost?.average).isEqualTo(LiterAverage(literCents = 396, sessionCount = 1))
    }

    @Test
    fun `a purchase updates the price and the estimate at once, the average keeps the sessions' stored cost`() =
        runTest {
            TeaDetailFixture.seed(collection)
            TeaDetailFixture.brewed(sessions, "s1", at = LAST_BREW_MS)
            val vm = viewModel()

            // + 100 g for 18,90 €: 28,80 € for 150 g = 19,2 ct per gram; 96 ct over 750 ml = 128 ct per litre.
            vm.recordPurchase(TeaPurchase(100.0, 1890, today))

            assertThat(vm.tea.cost).isEqualTo(TeaCost(1920, 128, LiterAverage(396, 1)))
        }

    @Test
    fun `deleting the purchase in the history restores the price`() = runTest {
        TeaDetailFixture.seed(collection)
        collection.stock.addEvent(
            InventoryEvent("p1", TEA_ID, InventoryEventType.PURCHASE, 100.0, 2_000, priceCents = 1890),
        )
        val vm = viewModel()
        assertThat(vm.tea.cost?.pricePer100gCents).isEqualTo(1920)

        vm.deleteStockEntry("p1")

        assertThat(vm.tea.cost?.pricePer100gCents).isEqualTo(1980)
    }

    @Test
    fun `a tea without a recorded price has no cost`() = runTest {
        TeaDetailFixture.seed(collection, grams = 0.0)
        collection.stock.addEvent(InventoryEvent("p0", TEA_ID, InventoryEventType.PURCHASE, 50.0, 1_000))

        assertThat(viewModel().tea.cost).isNull()
    }
}
