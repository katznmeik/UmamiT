package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.catalog.CatalogResetPlan
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCatalogReset
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
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ⋮ *Aus Katalog zurücksetzen* on the tea detail (PRF-06, CAT-13, SCR-09, D-104; spec #171 seam 2). */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailResetViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val reset = FakeCatalogReset()
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
        FakeClock(),
        reset,
    )

    private val TeaDetailViewModel.tea get() = (state.value as TeaDetailUiState.Ready).tea

    @Test
    fun `the reset is offered only while the catalog holds the tea`() = runTest {
        TeaDetailFixture.seed(collection)
        val viewModel = viewModel()
        assertThat(viewModel.tea.catalogReset).isNull()

        reset.plans.value = mapOf(TEA_ID to CatalogResetPlan(leftOut = 0))
        assertThat(viewModel.tea.catalogReset).isEqualTo(CatalogResetOffer(leftOut = 0))

        reset.plans.value = mapOf(TEA_ID to CatalogResetPlan(leftOut = 2))
        assertThat(viewModel.tea.catalogReset).isEqualTo(CatalogResetOffer(leftOut = 2))

        reset.plans.value = emptyMap()
        assertThat(viewModel.tea.catalogReset).isNull()
    }

    @Test
    fun `the confirmed reset resets the tea`() = runTest {
        TeaDetailFixture.seed(collection)
        reset.plans.value = mapOf(TEA_ID to CatalogResetPlan(leftOut = 0))

        viewModel().resetFromCatalog()

        assertThat(reset.resets).containsExactly(TEA_ID)
    }

    @Test
    fun `a failed reset is said once`() = runTest {
        TeaDetailFixture.seed(collection)
        reset.plans.value = mapOf(TEA_ID to CatalogResetPlan(leftOut = 0))
        reset.failNext = true
        val viewModel = viewModel()

        viewModel.resetFromCatalog()

        assertThat(withTimeout(1_000) { viewModel.navigation.first() }).isEqualTo(TeaDetailEvent.ResetFailed)
        assertThat(reset.resets).isEmpty()
    }
}
