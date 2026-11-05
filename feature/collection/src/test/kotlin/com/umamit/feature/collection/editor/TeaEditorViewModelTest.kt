package com.umamit.feature.collection.editor

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.TeaPurchase
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
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
import java.time.LocalDate

/** Tee bearbeiten (SCR-10; TEA-01–TEA-03, PRF-02, INV-01, D-100) on fake repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaEditorViewModelTest {
    private val collection = FakeCollection()
    private val adder = FakeAddWithoutCatalog(collection)
    private val clock = FakeClock() // 22 Oct 2026, Berlin

    private val catalogTea = Tea(
        id = "t1",
        name = "Asahi Gyokuro",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = "12345",
        styleKey = "gyokuro",
        teaType = "Gyokuro",
        umamiLevel = UmamiLevel.VERY_HIGH,
        originCountry = "JP",
        description = "Synthetic description",
        notes = "Morgens",
    )

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collection.teas.saveTea(catalogTea)
        adder.add(catalogTea.copy(name = "seed"), null) // gives the recipes below to manual-1
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        teaId: String? = null,
        styleKey: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = TeaEditorViewModel(
        saved.apply {
            if (teaId != null) set(TeaEditorViewModel.TEA_ID, teaId)
            if (styleKey != null) set(TeaEditorViewModel.STYLE_KEY, styleKey)
        },
        collection.teas,
        adder,
        FakeSorteNames,
        clock,
    )

    private val TeaEditorViewModel.ready get() = state.value as TeaEditorUiState.Ready

    @Test
    fun `a new tea starts with its Sorte's umami level, today's purchase date and no changes`() {
        val vm = viewModel(styleKey = "kabusecha")
        assertThat(vm.ready.isNew).isTrue()
        assertThat(vm.ready.form.styleKey).isEqualTo("kabusecha")
        assertThat(vm.ready.form.umami).isEqualTo(UmamiLevel.HIGH)
        assertThat(vm.ready.form.purchaseDate).isEqualTo(LocalDate.of(2026, 10, 22))
        assertThat(vm.ready.form.vendor).isEmpty()
        assertThat(vm.ready.hasChanges).isFalse()
        assertThat(vm.ready.errors).isEmpty()
    }

    @Test
    fun `Speichern refuses an empty name and shows the error only after the tap`() = runTest {
        val vm = viewModel(styleKey = "sencha")
        vm.update { it.copy(name = "  ") }
        assertThat(vm.ready.errors).isEmpty()

        vm.save()

        assertThat(vm.ready.errors).containsExactly(TeaFieldError.NameEmpty)
        assertThat(adder.added).hasSize(1) // only setUp's
        // Kept up to date as you type.
        vm.update { it.copy(name = "Tanabata") }
        assertThat(vm.ready.errors).isEmpty()
    }

    @Test
    fun `Speichern refuses a harvest year in the future, shading over 60 days and a bad package`() {
        val form = TeaEditorForm(name = "Tanabata", harvestYear = "2027", shadingDays = "61", packageGrams = "0")
        assertThat(form.errors(today = LocalDate.of(2026, 10, 22)))
            .containsExactly(TeaFieldError.HarvestYearFuture, TeaFieldError.ShadingDays, TeaFieldError.PackageGrams)
        val ok = form.copy(harvestYear = "2026", shadingDays = "60", packageGrams = "37,5", price = "12,40")
        assertThat(ok.errors(today = LocalDate.of(2026, 10, 22))).isEmpty()
    }

    @Test
    fun `a price without a package and a name over 80 characters are refused`() {
        val form = TeaEditorForm(name = "x".repeat(81), price = "9,90")
        assertThat(form.errors(today = LocalDate.of(2026, 10, 22)))
            .containsExactly(TeaFieldError.NameTooLong, TeaFieldError.PriceWithoutPackage)
        assertThat(TeaEditorForm(name = "a", packageGrams = "50", price = "9,9x").errors(LocalDate.of(2026, 10, 22)))
            .containsExactly(TeaFieldError.Price)
    }

    @Test
    fun `a new tea without a package is added with its Sorte and no stock`() = runTest {
        val events = mutableListOf<TeaEditorEvent>()
        val vm = viewModel(styleKey = "oolong_generic")
        backgroundCollect(vm, events)
        vm.update {
            it.copy(
                name = " Milky Oolong ",
                secondaryName = "Jin Xuan",
                originCountry = "TW",
                region = "Nantou",
                harvestYear = "2025",
                harvestSeason = HarvestSeason.SECOND_FLUSH,
                vendor = "Teeladen",
                notes = "Ein Geschenk",
            )
        }

        vm.save()

        val (tea, purchase) = adder.added.last()
        assertThat(purchase).isNull()
        assertThat(tea.name).isEqualTo("Milky Oolong")
        assertThat(tea.secondaryName).isEqualTo("Jin Xuan")
        assertThat(tea.styleKey).isEqualTo("oolong_generic")
        assertThat(tea.teaType).isEqualTo("Oolong")
        assertThat(tea.category).isEqualTo(TeaCategory.OOLONG)
        assertThat(tea.umamiLevel).isEqualTo(UmamiLevel.LOW)
        assertThat(tea.originCountry).isEqualTo("TW")
        assertThat(tea.originRegion).isEqualTo("Nantou")
        assertThat(tea.harvestYear).isEqualTo(2025)
        assertThat(tea.harvestSeason).isEqualTo(HarvestSeason.SECOND_FLUSH)
        assertThat(tea.vendor).isEqualTo("Teeladen")
        assertThat(tea.notes).isEqualTo("Ein Geschenk")
        assertThat(tea.cultivar).isNull()
        val newId = "manual-${adder.added.size}"
        assertThat(collection.stock.observeEvents(newId).first()).isEmpty()
        assertThat(collection.recipes.observeRecipes(newId).first().single().baseTempC).isEqualTo(95)
        assertThat(events).containsExactly(TeaEditorEvent.Saved(newId, isNew = true))
    }

    @Test
    fun `a new tea with a package gets its INITIAL stock event`() = runTest {
        val vm = viewModel(styleKey = "sencha")
        vm.update {
            it.copy(name = "Tanabata", packageGrams = "50", price = "12,40", purchaseDate = LocalDate.of(2026, 10, 1))
        }

        vm.save()

        assertThat(adder.added.last().second).isEqualTo(TeaPurchase(50.0, 1240, LocalDate.of(2026, 10, 1)))
        val event = collection.stock.observeEvents("manual-${adder.added.size}").first().single()
        assertThat(event.type).isEqualTo(InventoryEventType.INITIAL)
        assertThat(event.deltaGrams).isEqualTo(50.0)
    }

    @Test
    fun `Andere - unbekannt adds a tea without a style in the category Andere`() = runTest {
        val vm = viewModel(styleKey = "other")
        assertThat(vm.ready.form.styleKey).isNull()
        assertThat(vm.ready.form.umami).isNull()
        vm.update { it.copy(name = "Mate") }

        vm.save()

        val tea = adder.added.last().first
        assertThat(tea.styleKey).isNull()
        assertThat(tea.teaType).isNull()
        assertThat(tea.category).isEqualTo(TeaCategory.OTHER)
    }

    @Test
    fun `a powder Sorte adds a powder`() = runTest {
        val vm = viewModel(styleKey = "matcha")
        vm.update { it.copy(name = "Aoi") }
        vm.save()
        assertThat(adder.added.last().first.form).isEqualTo(TeaForm.POWDER)
    }

    @Test
    fun `a failed add says so and keeps the form`() = runTest {
        val vm = viewModel(styleKey = "sencha")
        vm.update { it.copy(name = "Tanabata") }
        adder.failNext = true

        vm.save()

        assertThat(vm.ready.saveFailed).isTrue()
        assertThat(vm.ready.form.name).isEqualTo("Tanabata")
    }

    @Test
    fun `an edit stores the fields and keeps what the editor does not show`() = runTest {
        val events = mutableListOf<TeaEditorEvent>()
        val vm = viewModel(teaId = "t1")
        backgroundCollect(vm, events)
        assertThat(vm.ready.isNew).isFalse()
        // The vendor of a catalog tea defaults to the shop (TEA-02), without counting as a change.
        assertThat(vm.ready.form.vendor).isEqualTo("Tee-Kontor Kiel")
        assertThat(vm.ready.hasChanges).isFalse()

        vm.update { it.copy(name = "Asahi", cultivar = "Asahi", shadingDays = "21", notes = "") }
        assertThat(vm.ready.hasChanges).isTrue()
        vm.save()

        val saved = collection.teas.getTea("t1")!!
        assertThat(saved.name).isEqualTo("Asahi")
        assertThat(saved.cultivar).isEqualTo("Asahi")
        assertThat(saved.shadingDays).isEqualTo(21)
        assertThat(saved.notes).isNull()
        assertThat(saved.vendor).isEqualTo("Tee-Kontor Kiel")
        assertThat(saved.teaType).isEqualTo("Gyokuro")
        assertThat(saved.catalogKey).isEqualTo("12345")
        assertThat(saved.description).isEqualTo("Synthetic description")
        assertThat(events).containsExactly(TeaEditorEvent.Saved("t1", isNew = false))
    }

    @Test
    fun `an edit offers no package fields`() {
        val vm = viewModel(teaId = "t1")
        assertThat(vm.ready.isNew).isFalse()
        assertThat(vm.ready.form.purchaseDate).isNull()
    }

    @Test
    fun `a Sorte change keeps the recipes and takes the new Sorte's name, Kategorie and umami level`() = runTest {
        val teaId = "manual-1"
        val recipesBefore = collection.recipes.observeRecipes(teaId).first()
        val vm = viewModel(teaId = teaId)

        vm.update { it.withStyle("black_generic") }
        assertThat(vm.ready.form.umami).isEqualTo(UmamiLevel.NONE)
        vm.save()

        val saved = collection.teas.getTea(teaId)!!
        assertThat(saved.styleKey).isEqualTo("black_generic")
        assertThat(saved.teaType).isEqualTo("Schwarzer Tee")
        assertThat(saved.category).isEqualTo(TeaCategory.BLACK)
        assertThat(collection.recipes.observeRecipes(teaId).first()).isEqualTo(recipesBefore)
    }

    @Test
    fun `the form survives a process death`() {
        val saved = SavedStateHandle()
        viewModel(styleKey = "sencha", saved = saved).update { it.copy(name = "Halb getippt") }

        val again = viewModel(styleKey = "sencha", saved = saved)

        assertThat(again.ready.form.name).isEqualTo("Halb getippt")
        assertThat(again.ready.hasChanges).isTrue()
    }

    @Test
    fun `a tea that is gone shows not found`() {
        assertThat(viewModel(teaId = "nope").state.value).isEqualTo(TeaEditorUiState.NotFound)
    }

    private fun kotlinx.coroutines.test.TestScope.backgroundCollect(
        vm: TeaEditorViewModel,
        into: MutableList<TeaEditorEvent>,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { into += it } }
    }
}

/** German Sorte names of the keys the tests use. */
internal object FakeSorteNames : SorteNames {
    private val names = mapOf(
        "gyokuro" to "Gyokuro",
        "kabusecha" to "Kabusecha",
        "sencha" to "Sencha",
        "matcha" to "Matcha",
        "oolong_generic" to "Oolong",
        "black_generic" to "Schwarzer Tee",
    )

    override fun name(styleKey: String): String? = names[styleKey]
}
