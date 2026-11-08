package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.TeaPurchase
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.StockLevel
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
import kotlinx.coroutines.flow.first
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

/** *Nachkauf erfassen* on the tea detail's VORRAT (INV-03, SCR-09, D-104; spec #171 seam 2). */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailPurchaseViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val clock = FakeClock()
    private val today: LocalDate get() = Instant.ofEpochMilli(clock.wall).atZone(clock.zone).toLocalDate()
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

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

    private suspend fun purchases() =
        collection.stock.observeEvents(TEA_ID).first().filter { it.type == InventoryEventType.PURCHASE }

    @Test
    fun `the sheet's day is the clock's today`() = runTest {
        TeaDetailFixture.seed(collection)

        assertThat(viewModel().tea.today).isEqualTo(today)
    }

    @Test
    fun `a priced purchase is written and the stock updates at once`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.recordPurchase(TeaPurchase(100.0, 1890, today))

        assertThat(purchases().single().priceCents).isEqualTo(1890)
        // 50 g bought before, 100 g now.
        assertThat(vm.tea.stockGrams).isEqualTo(150.0)
    }

    @Test
    fun `a purchase without a price is written without one`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.recordPurchase(TeaPurchase(20.0, null, today.minusDays(3)))

        assertThat(purchases().single().priceCents).isNull()
        assertThat(vm.tea.stockGrams).isEqualTo(70.0)
    }

    @Test
    fun `a tea without stock events gets its first stock from a purchase`() = runTest {
        TeaDetailFixture.seed(collection, TeaDetailFixture.tea.copy(catalogKey = null), grams = 0.0)
        val vm = viewModel()
        assertThat(vm.tea.hasStock).isFalse()

        vm.recordPurchase(TeaPurchase(40.0, null, today))

        assertThat(vm.tea.hasStock).isTrue()
        assertThat(vm.tea.stockGrams).isEqualTo(40.0)
        assertThat(vm.tea.stockLevel).isEqualTo(StockLevel.AVAILABLE)
    }

    @Test
    fun `a purchase dated after today is refused and nothing is written`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.recordPurchase(TeaPurchase(50.0, 990, today.plusDays(1)))

        assertThat(purchases()).isEmpty()
        assertThat(vm.tea.stockGrams).isEqualTo(50.0)
    }
}
