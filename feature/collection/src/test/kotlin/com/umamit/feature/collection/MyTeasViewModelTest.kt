package com.umamit.feature.collection

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StockLevel
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
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

/** *Meine Tees* (SCR-08; TEA-08–TEA-11, D-100) on fake repositories. Invented teas (D-016). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyTeasViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 20.0))
    private val sessions = FakeSessions(collection, settings)
    private val list = FakeCollectionList(sessions)
    private val archive = FakeTeaArchive(sessions)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MyTeasViewModel(list, settings, archive, PhotoFiles(folder.newFolder()))

    private val MyTeasViewModel.ids get() = state.value.rows.map { it.id }

    private suspend fun save(tea: Tea) = collection.teas.saveTea(tea)

    private suspend fun stock(teaId: String, grams: Double) = collection.stock.addEvent(
        InventoryEvent("e-$teaId-$grams", teaId, InventoryEventType.PURCHASE, grams, occurredAtMillis = 1L),
    )

    @Test
    fun `the list shows the teas that are not archived, with name, Sorte, stock, star and Rezept pruefen`() = runTest {
        save(tea("a", "Asamushi", secondaryName = "Fukamushi", teaType = "Sencha", isFavorite = true))
        save(tea("b", "Bancha").copy(recipeCheckReason = RecipeCheckReason.FROM_TEXT))
        save(tea("x", "Alter Tee").copy(isArchived = true))
        stock("a", 38.0)

        val state = viewModel().state.value
        val rows = state.rows.associateBy { it.id }

        assertThat(state.loading).isFalse()
        assertThat(rows.keys).containsExactly("a", "b")
        with(rows.getValue("a")) {
            assertThat(name).isEqualTo("Asamushi")
            assertThat(secondaryName).isEqualTo("Fukamushi")
            assertThat(sorte).isEqualTo("Sencha")
            assertThat(remainingGrams).isEqualTo(38.0)
            assertThat(stockLevel).isEqualTo(StockLevel.AVAILABLE)
            assertThat(isFavorite).isTrue()
            assertThat(checkRecipe).isFalse()
        }
        assertThat(rows.getValue("b").checkRecipe).isTrue()
        assertThat(rows.getValue("b").stockLevel).isNull()
    }

    @Test
    fun `the search finds a tea by its secondary name, ignoring case and accents`() = runTest {
        save(tea("a", "Asamushi", secondaryName = "Hōjō"))
        save(tea("b", "Bancha"))
        val vm = viewModel()

        vm.updateFilter { it.copy(search = "hojo") }

        assertThat(vm.ids).containsExactly("a")
    }

    @Test
    fun `Favoriten shows only favourites`() = runTest {
        save(tea("a", "Asamushi", isFavorite = true))
        save(tea("b", "Bancha"))
        val vm = viewModel()

        vm.updateFilter { it.copy(favoritesOnly = true) }

        assertThat(vm.ids).containsExactly("a")
    }

    @Test
    fun `Kategorie and Sorte offer only the values present and filter by them`() = runTest {
        save(tea("a", "Asamushi", teaType = "Sencha"))
        save(tea("b", "Bancha", teaType = "Bancha"))
        save(tea("o", "Oriental", teaType = "Oolong", category = TeaCategory.OOLONG))
        val vm = viewModel()

        assertThat(vm.state.value.categories).containsExactly(TeaCategory.GREEN, TeaCategory.OOLONG).inOrder()
        assertThat(vm.state.value.sorten).containsExactly("Bancha", "Oolong", "Sencha").inOrder()

        vm.updateFilter { it.copy(categories = setOf(TeaCategory.GREEN)) }
        assertThat(vm.ids).containsExactly("a", "b")

        vm.updateFilter { it.copy(sorten = setOf("Sencha")) }
        assertThat(vm.ids).containsExactly("a")
    }

    @Test
    fun `Vorrat shows the teas that are Knapp or Leer`() = runTest {
        save(tea("a", "Asamushi"))
        save(tea("b", "Bancha"))
        save(tea("c", "Chumushi"))
        stock("a", 10.0)
        stock("b", 100.0)
        val vm = viewModel()

        vm.updateFilter { it.copy(stock = setOf(StockLevel.LOW, StockLevel.EMPTY)) }

        assertThat(vm.ids).containsExactly("a")
    }

    @Test
    fun `Aromatisiert shows only flavoured teas`() = runTest {
        save(tea("a", "Asamushi").copy(isFlavored = true))
        save(tea("b", "Bancha"))
        val vm = viewModel()

        vm.updateFilter { it.copy(flavoredOnly = true) }

        assertThat(vm.ids).containsExactly("a")
    }

    @Test
    fun `Archiviert lists only archived teas and Wiederherstellen brings one back`() = runTest {
        save(tea("a", "Asamushi"))
        save(tea("x", "Alter Tee").copy(isArchived = true))
        val vm = viewModel()

        vm.updateFilter { it.copy(archived = true) }
        assertThat(vm.ids).containsExactly("x")
        assertThat(vm.state.value.rows.single().isArchived).isTrue()

        vm.restore("x")

        assertThat(vm.ids).isEmpty()
        assertThat(collection.teas.getTea("x")!!.isArchived).isFalse()
        vm.updateFilter { it.copy(archived = false) }
        assertThat(vm.ids).containsExactly("a", "x")
    }

    @Test
    fun `the sort is Zuletzt gebrueht by default, empty teas last`() = runTest {
        save(tea("a", "Asamushi"))
        save(tea("b", "Bancha"))
        save(tea("e", "Empty"))
        stock("e", 0.0)
        list.setAddedAt("a", 1L)
        brewed("b", startedAt = 5_000L)

        val vm = viewModel()

        assertThat(vm.state.value.sort).isEqualTo(CollectionSort.LAST_BREWED)
        assertThat(vm.ids).containsExactly("b", "a", "e").inOrder()
    }

    @Test
    fun `a chosen sort is remembered in the settings and used by the next list`() = runTest {
        save(tea("a", "Asamushi"))
        save(tea("b", "Bancha"))
        list.setAddedAt("a", 1L)
        list.setAddedAt("b", 2L)

        viewModel().setSort(CollectionSort.RECENTLY_ADDED)

        assertThat(settings.settings.first().collectionSort).isEqualTo(CollectionSort.RECENTLY_ADDED)
        // A new view model (the app started again) reads it.
        val next = viewModel()
        assertThat(next.state.value.sort).isEqualTo(CollectionSort.RECENTLY_ADDED)
        assertThat(next.ids).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `no tea at all is Noch keine Tees, a search without hits is Kein Tee passt`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.empty).isEqualTo(MyTeasEmpty.NO_TEAS)

        save(tea("a", "Asamushi"))
        assertThat(vm.state.value.empty).isNull()

        vm.updateFilter { it.copy(search = "zzz") }
        assertThat(vm.state.value.empty).isEqualTo(MyTeasEmpty.NO_MATCH)

        vm.resetFilters()
        assertThat(vm.state.value.empty).isNull()
        assertThat(vm.state.value.filter.search).isEmpty()
    }

    @Test
    fun `only archived teas is Noch keine Tees, with the chips to reach them`() = runTest {
        save(tea("x", "Alter Tee").copy(isArchived = true))

        val state = viewModel().state.value

        assertThat(state.empty).isEqualTo(MyTeasEmpty.NO_TEAS)
        assertThat(state.showControls).isTrue()
    }

    private suspend fun brewed(teaId: String, startedAt: Long) {
        sessions.sessions.startSession(
            SessionStart(
                id = "s-$teaId",
                snapshot = SessionSnapshot(
                    teaId = teaId,
                    teaName = teaId,
                    teaSecondaryName = null,
                    teaType = null,
                    styleKey = null,
                    category = null,
                    profileId = null,
                    profileName = null,
                    method = BrewMethod.KYUSU,
                    vesselId = null,
                    vesselName = null,
                    leafGrams = null,
                    waterMl = null,
                    baseTempC = null,
                ),
                steps = emptyList(),
                preAlertSeconds = 10,
                startedAtMs = startedAt,
                zoneId = "Europe/Berlin",
            ),
        )
    }

    private fun tea(
        id: String,
        name: String,
        secondaryName: String? = null,
        teaType: String? = null,
        isFavorite: Boolean = false,
        category: TeaCategory = TeaCategory.GREEN,
    ) = Tea(
        id = id,
        name = name,
        category = category,
        form = TeaForm.LOOSE_LEAF,
        secondaryName = secondaryName,
        teaType = teaType,
        isFavorite = isFavorite,
    )
}
