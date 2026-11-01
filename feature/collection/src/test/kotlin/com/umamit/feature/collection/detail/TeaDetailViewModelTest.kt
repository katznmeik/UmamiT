package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.StockLevel
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.feature.collection.detail.TeaDetailFixture.CATALOG_KEY
import com.umamit.feature.collection.detail.TeaDetailFixture.COLD_ID
import com.umamit.feature.collection.detail.TeaDetailFixture.LAST_BREW_MS
import com.umamit.feature.collection.detail.TeaDetailFixture.PRODUCT_URL
import com.umamit.feature.collection.detail.TeaDetailFixture.STANDARD_ID
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
import java.time.LocalDate

/** The tea detail's view model (SCR-09, TEA-04, TEA-06, TEA-13, INV-09; D-100). */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val catalog = FakeCatalogRepository(details = listOf(TeaDetailFixture.catalogTea))

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
        catalog,
        archive,
        photos,
    )

    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

    private val TeaDetailViewModel.tea get() = (state.value as TeaDetailUiState.Ready).tea

    @Test
    fun `the top shows names, Sorte, umami and the sessions, last brew and stock`() = runTest {
        TeaDetailFixture.seed(collection)
        TeaDetailFixture.brewed(sessions, "s1", at = LAST_BREW_MS - 86_400_000L * 5)
        TeaDetailFixture.brewed(sessions, "s2", at = LAST_BREW_MS)

        val tea = viewModel().tea

        assertThat(tea.name).isEqualTo("Morgennebel")
        assertThat(tea.secondaryName).isEqualTo("Asagiri")
        assertThat(tea.teaType).isEqualTo("Sencha")
        assertThat(tea.umamiLevel).isEqualTo(UmamiLevel.HIGH)
        assertThat(tea.sessionCount).isEqualTo(2)
        assertThat(tea.lastBrewed).isEqualTo(LocalDate.of(2026, 10, 21))
        // 50 g bought, 5 g deducted by each session.
        assertThat(tea.stockGrams).isEqualTo(40.0)
        assertThat(tea.stockLevel).isEqualTo(StockLevel.AVAILABLE)
    }

    @Test
    fun `a tea never brewed has no last brew`() = runTest {
        TeaDetailFixture.seed(collection)

        val tea = viewModel().tea

        assertThat(tea.sessionCount).isEqualTo(0)
        assertThat(tea.lastBrewed).isNull()
    }

    @Test
    fun `REZEPTE lists the recipes by position with their key values, the default marked`() = runTest {
        TeaDetailFixture.seed(collection)

        val recipes = viewModel().tea.recipes

        assertThat(recipes.map { it.id }).containsExactly(STANDARD_ID, COLD_ID).inOrder()
        assertThat(recipes[0]).isEqualTo(
            RecipeSummary(STANDARD_ID, "Kyusu", true, BrewMethod.KYUSU, "Kyusu 250 ml", 5.0, 250, 70, 3),
        )
        // A rinse brews nothing: one infusion at the recipe's 5 °C, no vessel.
        assertThat(recipes[1]).isEqualTo(
            RecipeSummary(COLD_ID, "Kaltaufguss", false, BrewMethod.COLD_BREW, null, 10.0, 1000, 5, 1),
        )
        assertThat(viewModel().tea.canAddRecipe).isTrue()
    }

    @Test
    fun `Rezept hinzufuegen goes at 10 recipes`() = runTest {
        TeaDetailFixture.seed(collection)
        (2 until 10).forEach { i ->
            collection.recipes.saveRecipe(TeaDetailFixture.recipes[1].copy(id = "r$i", isDefault = false, position = i))
        }

        assertThat(viewModel().tea.recipes).hasSize(10)
        assertThat(viewModel().tea.canAddRecipe).isFalse()
    }

    @Test
    fun `BESCHREIBUNG, NOTIZEN and DETAILS come from the tea, empty ones are left out`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        assertThat(vm.tea.description).startsWith("Ein erfundener Sencha")
        assertThat(vm.tea.notes).isEqualTo("Zweiter Aufguss etwas kürzer.")
        assertThat(vm.tea.details.cultivar).isEqualTo("Yabukita")
        assertThat(vm.tea.details.harvestSeason).isEqualTo(HarvestSeason.FIRST_FLUSH)
        assertThat(vm.tea.details.isEmpty).isFalse()

        collection.teas.saveTea(
            TeaDetailFixture.tea.copy(
                description = " ",
                notes = null,
                originCountry = null,
                originRegion = null,
                cultivar = null,
                harvestYear = null,
                harvestSeason = null,
                vendor = null,
            ),
        )

        assertThat(vm.tea.description).isNull()
        assertThat(vm.tea.notes).isNull()
        assertThat(vm.tea.details.isEmpty).isTrue()
    }

    @Test
    fun `the reorder link is a button when Knapp or Leer, a text link otherwise, with the size bought last`() =
        runTest {
            TeaDetailFixture.seed(collection)
            val vm = viewModel()

            assertThat(vm.tea.reorder).isEqualTo(Reorder("$PRODUCT_URL-50", prominent = false))

            vm.correctStock(12.0)
            assertThat(vm.tea.stockLevel).isEqualTo(StockLevel.LOW)
            assertThat(vm.tea.reorder).isEqualTo(Reorder("$PRODUCT_URL-50", prominent = true))

            vm.correctStock(0.0)
            assertThat(vm.tea.stockLevel).isEqualTo(StockLevel.EMPTY)
            assertThat(vm.tea.reorder?.prominent).isTrue()
        }

    @Test
    fun `no reorder link without a catalog key or for a discontinued catalog tea`() = runTest {
        TeaDetailFixture.seed(collection, TeaDetailFixture.tea.copy(catalogKey = null))
        val vm = viewModel()
        assertThat(vm.tea.reorder).isNull()

        collection.teas.saveTea(TeaDetailFixture.tea)
        assertThat(vm.tea.reorder).isNotNull()

        catalog.details.value = mapOf(CATALOG_KEY to TeaDetailFixture.catalogTea.copy(discontinued = true))
        assertThat(vm.tea.reorder).isNull()

        // Before the catalog was ever loaded the tea's own product page stands in.
        catalog.details.value = emptyMap()
        assertThat(vm.tea.reorder?.url).isEqualTo(PRODUCT_URL)
    }

    @Test
    fun `Favorit marks and unmarks the tea`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.toggleFavorite()
        assertThat(vm.tea.isFavorite).isTrue()
        assertThat(collection.teas.getTea(TEA_ID)!!.isFavorite).isTrue()

        vm.toggleFavorite()
        assertThat(vm.tea.isFavorite).isFalse()
    }

    @Test
    fun `Archivieren archives the tea and tells the host, Rueckgaengig is the host's restore`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.archive()

        assertThat(withTimeout(1_000) { vm.navigation.first() })
            .isEqualTo(TeaDetailEvent.Archived(TEA_ID, "Morgennebel"))
        assertThat(archive.archived).containsExactly(TEA_ID)
        assertThat(vm.tea.isArchived).isTrue()
        assertThat(vm.tea.canBrew).isFalse()
    }

    @Test
    fun `an archived tea offers Wiederherstellen and stays on the detail`() = runTest {
        TeaDetailFixture.seed(collection, TeaDetailFixture.tea.copy(isArchived = true))
        val vm = viewModel()
        assertThat(vm.tea.canBrew).isFalse()

        vm.restore()

        assertThat(vm.tea.isArchived).isFalse()
        assertThat(vm.tea.canBrew).isTrue()
    }

    @Test
    fun `Loeschen deletes the tea and closes the detail, the journal keeps its sessions`() = runTest {
        TeaDetailFixture.seed(collection)
        TeaDetailFixture.brewed(sessions, "s1", at = LAST_BREW_MS)
        val vm = viewModel()

        vm.delete()

        assertThat(withTimeout(1_000) { vm.navigation.first() }).isEqualTo(TeaDetailEvent.Deleted)
        assertThat(archive.deleted).containsExactly(TEA_ID)
        assertThat(vm.state.value).isEqualTo(TeaDetailUiState.Gone)
        assertThat(sessions.journal.observeSessions().first()).hasSize(1)
    }

    @Test
    fun `during the tea's brew neither archive nor delete happens`() = runTest {
        TeaDetailFixture.seed(collection)
        TeaDetailFixture.activeSession(sessions)
        val vm = viewModel()
        assertThat(vm.tea.inActiveBrew).isTrue()
        // The stock sheet says what the running session still deducts (D-096).
        assertThat(vm.tea.sessionLeafGrams).isEqualTo(5.0)

        vm.archive()
        vm.delete()

        assertThat(archive.archived).isEmpty()
        assertThat(archive.deleted).isEmpty()
        assertThat(vm.tea.isArchived).isFalse()
    }

    @Test
    fun `another tea's brew blocks nothing`() = runTest {
        TeaDetailFixture.seed(collection)
        TeaDetailFixture.activeSession(sessions, teaId = "other")

        val tea = viewModel().tea

        assertThat(tea.inActiveBrew).isFalse()
        assertThat(tea.sessionLeafGrams).isNull()
    }

    @Test
    fun `it updates at once after an edit, a brew and a correction`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        collection.teas.saveTea(TeaDetailFixture.tea.copy(name = "Abendnebel"))
        TeaDetailFixture.brewed(sessions, "s1", at = LAST_BREW_MS)
        vm.correctStock(38.0)

        assertThat(vm.tea.name).isEqualTo("Abendnebel")
        assertThat(vm.tea.sessionCount).isEqualTo(1)
        assertThat(vm.tea.stockGrams).isEqualTo(38.0)
    }

    @Test
    fun `a deleted tea's detail is gone`() = runTest {
        assertThat(viewModel().state.value).isEqualTo(TeaDetailUiState.Gone)
    }
}
