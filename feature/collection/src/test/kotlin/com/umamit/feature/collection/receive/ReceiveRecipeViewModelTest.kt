package com.umamit.feature.collection.receive

import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.RecipeRefusal
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.TeaCategory
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeTeaLinkLoader
import com.umamit.feature.collection.editor.SorteNames
import com.umamit.feature.collection.receive.ReceivedRecipes.CATALOG_KEY
import com.umamit.feature.collection.receive.ReceivedRecipes.SHOP_URL
import com.umamit.feature.collection.receive.ReceivedRecipes.bytes
import com.umamit.feature.collection.receive.ReceivedRecipes.kyusu
import com.umamit.feature.collection.receive.ReceivedRecipes.plain
import com.umamit.feature.collection.receive.ReceivedRecipes.recipe
import com.umamit.feature.collection.receive.ReceivedRecipes.tea
import kotlinx.coroutines.CompletableDeferred
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
import java.time.LocalDate
import com.umamit.core.backup.R as BackupR

/**
 * SCR-32 „Rezept übernehmen" (PRF-08 AC5–AC9, D-130 Q4, Q13) on fakes: the reader's refusals, the preselected
 * tea, the 10-recipe limit, the new tea from the catalog or without, and the shop page only on the tap. Synthetic
 * teas and links (D-016).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveRecipeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val catalog = FakeCatalogRepository()
    private val links = FakeTeaLinkLoader()
    private val fromCatalog = FakeAddFromCatalog(collection)
    private val withoutCatalog = FakeAddWithoutCatalog(collection)
    private val sorteNames = SorteNames { key -> if (key == "bancha") "Bancha" else null }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReceiveRecipeViewModel(
        teas = collection.teas,
        recipes = collection.recipes,
        catalog = catalog,
        links = links,
        takeOver = RecipeTakeOver(collection.recipes, fromCatalog, withoutCatalog, sorteNames),
        photos = PhotoFiles(folder.newFolder()),
        readDispatcher = Dispatchers.Main,
    )

    private fun ReceiveRecipeViewModel.ready() = state.value as ReceiveRecipeUiState.Ready

    private fun inCatalog(key: String = CATALOG_KEY) {
        catalog.details.value += key to CatalogTeaDetail(key = key, name = "Gyokuro Asahi", productUrl = SHOP_URL)
    }

    @Test
    fun `a file that is not a recipe file is refused and nothing is written`() = runTest {
        val viewModel = viewModel()

        viewModel.open(1L, """{"format":"something-else"}""".toByteArray())

        assertThat(viewModel.state.value).isEqualTo(ReceiveRecipeUiState.Refused(RecipeRefusal.NOT_A_RECIPE_FILE))
        assertThat(collection.teas.observeTeas().first()).isEmpty()
    }

    @Test
    fun `a file over 64 KB and an empty one are damaged`() = runTest {
        val viewModel = viewModel()

        // 03 §5: „broken or too large" reads „Die Rezeptdatei ist beschädigt.".
        viewModel.open(1L, ByteArray(RecipeFile.MAX_BYTES + 1) { ' '.code.toByte() })
        assertThat(viewModel.state.value).isEqualTo(ReceiveRecipeUiState.Refused(RecipeRefusal.DAMAGED))
        assertThat(RecipeRefusal.DAMAGED.messageRes).isEqualTo(BackupR.string.recipe_file_refused_damaged)

        viewModel.open(2L, ByteArray(0))
        assertThat(viewModel.state.value).isEqualTo(ReceiveRecipeUiState.Refused(RecipeRefusal.DAMAGED))
    }

    @Test
    fun `the tea with the file's catalog key is preselected over a tea of the same name`() = runTest {
        collection.teas.saveTea(tea("byName", "Gyokuro Asahi"))
        collection.teas.saveTea(tea("byKey", "Asahi", catalogKey = CATALOG_KEY))
        val viewModel = viewModel()

        viewModel.open(1L, bytes())

        val ready = viewModel.ready()
        assertThat(ready.match?.id).isEqualTo("byKey")
        assertThat(ready.matchKind).isEqualTo(MatchKind.CATALOG)
        assertThat(ready.choice).isEqualTo(TeaChoice.Existing("byKey"))
        assertThat(ready.canTakeOver).isTrue()
    }

    @Test
    fun `a tea of the same name in other case is preselected`() = runTest {
        collection.teas.saveTea(tea("t1", "gyokuro asahi"))
        val viewModel = viewModel()

        viewModel.open(1L, bytes())

        assertThat(viewModel.ready().matchKind).isEqualTo(MatchKind.NAME)
        assertThat(viewModel.ready().choice).isEqualTo(TeaChoice.Existing("t1"))
    }

    @Test
    fun `without a match nothing is chosen and Rezept uebernehmen waits`() = runTest {
        collection.teas.saveTea(tea("t1", "Sencha"))
        val viewModel = viewModel()

        viewModel.open(1L, bytes())

        assertThat(viewModel.ready().match).isNull()
        assertThat(viewModel.ready().choice).isNull()
        assertThat(viewModel.ready().canTakeOver).isFalse()
    }

    @Test
    fun `a tea with 10 recipes is not preselected and can't be chosen`() = runTest {
        collection.teas.saveTea(tea("full", "Gyokuro Asahi"))
        repeat(10) { collection.recipes.saveRecipe(recipe("full", it)) }
        val viewModel = viewModel()

        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.Existing("full"))
        viewModel.pick("full")

        val ready = viewModel.ready()
        assertThat(ready.match?.full).isTrue()
        assertThat(ready.choice).isNull()
        assertThat(ready.teas.single().full).isTrue()
    }

    @Test
    fun `Rezept uebernehmen adds a new non-default recipe without a vessel to the chosen tea`() = runTest {
        collection.teas.saveTea(tea("t1", "Gyokuro Asahi"))
        collection.recipes.saveRecipe(recipe("t1", 0, name = "Kyusu"))
        collection.teas.saveTea(tea("other", "Sencha", archived = true))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.pick("other")
        viewModel.choose(TeaChoice.Existing("t1"))

        viewModel.takeOver()

        val event = viewModel.navigation.first()
        assertThat(event).isEqualTo(ReceiveRecipeEvent.TakenOver("t1", "Kyusu (2)"))
        val added = collection.recipes.observeRecipes("t1").first().single { it.name == "Kyusu (2)" }
        assertThat(added.isDefault).isFalse()
        assertThat(added.vesselId).isNull()
        assertThat(added.leafGrams).isEqualTo(4.0)
        assertThat(added.waterMl).isEqualTo(120)
        assertThat(added.steps).isEqualTo(kyusu.steps)
        assertThat(added.position).isEqualTo(1)
    }

    @Test
    fun `an archived tea can be picked from the sheet`() = runTest {
        collection.teas.saveTea(tea("old", "Sencha", archived = true))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())

        viewModel.pick("old")
        viewModel.takeOver()

        assertThat(viewModel.navigation.first()).isEqualTo(ReceiveRecipeEvent.TakenOver("old", "Kyusu"))
    }

    @Test
    fun `Neuen Tee anlegen aus dem Katalog asks for a package first and writes nothing until Aufnehmen`() = runTest {
        inCatalog()
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        assertThat(viewModel.ready().catalogKey).isEqualTo(CATALOG_KEY)

        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        assertThat(viewModel.ready().packageSheet).isEqualTo(PackageSheet(CATALOG_KEY))
        assertThat(collection.teas.observeTeas().first()).isEmpty()
        assertThat(fromCatalog.purchases).isEmpty()
        // A second *Rezept übernehmen* while the sheet is open writes nothing either.
        viewModel.takeOver()
        assertThat(collection.teas.observeTeas().first()).isEmpty()

        viewModel.savePackage(CatalogPurchase(CATALOG_KEY, 100.0, 1240, LocalDate.of(2026, 11, 18)))

        val event = viewModel.navigation.first() as ReceiveRecipeEvent.TakenOver
        assertThat(fromCatalog.purchases).containsExactly(
            CatalogPurchase(CATALOG_KEY, 100.0, 1240, LocalDate.of(2026, 11, 18)),
        )
        assertThat(withoutCatalog.added).isEmpty()
        assertThat(collection.teas.getTea(event.teaId)?.catalogKey).isEqualTo(CATALOG_KEY)
        assertThat(collection.stock.observeEvents(event.teaId).first().single().deltaGrams).isEqualTo(100.0)
        assertThat(collection.recipes.observeRecipes(event.teaId).first().map { it.name }).contains("Kyusu")
        assertThat(viewModel.ready().packageSheet).isNull()
    }

    @Test
    fun `closing the package sheet returns to SCR-32 with nothing written`() = runTest {
        inCatalog()
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        viewModel.dismissPackageSheet()

        val ready = viewModel.ready()
        assertThat(ready.packageSheet).isNull()
        assertThat(ready.choice).isEqualTo(TeaChoice.NewTea)
        assertThat(ready.canTakeOver).isTrue()
        assertThat(collection.teas.observeTeas().first()).isEmpty()
        assertThat(fromCatalog.purchases).isEmpty()
        assertThat(withoutCatalog.added).isEmpty()
    }

    @Test
    fun `an alert removes the package sheet at once and nothing is written`() = runTest {
        inCatalog()
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        viewModel.closePackageSheet()

        assertThat(viewModel.ready().packageSheet).isNull()
        assertThat(collection.teas.observeTeas().first()).isEmpty()
    }

    @Test
    fun `Neuen Tee anlegen is hidden when a collection tea has the file's catalog key, archived included`() = runTest {
        inCatalog()
        collection.teas.saveTea(tea("old", "Asahi", catalogKey = CATALOG_KEY, archived = true))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())

        viewModel.choose(TeaChoice.NewTea)

        val ready = viewModel.ready()
        assertThat(ready.offersNewTea).isFalse()
        assertThat(ready.choice).isEqualTo(TeaChoice.Existing("old"))
    }

    @Test
    fun `Neuen Tee anlegen stays for a tea of the same name without the key`() = runTest {
        collection.teas.saveTea(tea("t1", "Gyokuro Asahi"))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())

        assertThat(viewModel.ready().offersNewTea).isTrue()
        viewModel.choose(TeaChoice.NewTea)
        assertThat(viewModel.ready().choice).isEqualTo(TeaChoice.NewTea)
    }

    @Test
    fun `the shop link is hidden when a collection tea matched by key or by name`() = runTest {
        collection.teas.saveTea(tea("t1", "gyokuro asahi"))
        val byName = viewModel()
        byName.open(1L, bytes())
        assertThat(byName.ready().matchKind).isEqualTo(MatchKind.NAME)
        assertThat(byName.ready().offersShopLink).isFalse()

        collection.teas.saveTea(tea("t2", "Asahi", catalogKey = CATALOG_KEY))
        val byKey = viewModel()
        byKey.open(1L, bytes())
        assertThat(byKey.ready().matchKind).isEqualTo(MatchKind.CATALOG)
        assertThat(byKey.ready().offersShopLink).isFalse()
        assertThat(links.loaded).isEmpty()
    }

    @Test
    fun `Neuen Tee anlegen without catalog takes the file's name, Kategorie and Sorte, and no package`() = runTest {
        val viewModel = viewModel()
        viewModel.open(1L, bytes(plain))
        assertThat(viewModel.ready().catalogKey).isNull()

        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        val event = viewModel.navigation.first() as ReceiveRecipeEvent.TakenOver
        val (tea, purchase) = withoutCatalog.added.single()
        assertThat(tea.name).isEqualTo("Eigener Bancha")
        assertThat(tea.category).isEqualTo(TeaCategory.GREEN)
        assertThat(tea.styleKey).isEqualTo("bancha")
        assertThat(tea.teaType).isEqualTo("Bancha")
        assertThat(purchase).isNull()
        assertThat(fromCatalog.purchases).isEmpty()
        assertThat(viewModel.ready().packageSheet).isNull()
        val added = collection.recipes.observeRecipes(event.teaId).first().single { it.name == "Becher" }
        assertThat(added.isDefault).isFalse()
    }

    @Test
    fun `opening a file sends no request`() = runTest {
        collection.teas.saveTea(tea("t1", "Sencha"))
        val viewModel = viewModel()

        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.NewTea)

        assertThat(viewModel.ready().offersShopLink).isTrue()
        assertThat(links.loaded).isEmpty()
    }

    @Test
    fun `the shop link is offered only for a checked link whose tea is not in the local catalog`() = runTest {
        val noLink = viewModel()
        noLink.open(1L, bytes(plain))
        assertThat(noLink.ready().offersShopLink).isFalse()

        inCatalog()
        val known = viewModel()
        known.open(1L, bytes())
        assertThat(known.ready().offersShopLink).isFalse()
        assertThat(links.loaded).isEmpty()
    }

    @Test
    fun `the tap makes exactly one request for the file's link, and the loaded tea is then from the catalog`() =
        runTest {
            links.outcome = { TeaLinkOutcome.Opened(CATALOG_KEY) }
            val viewModel = viewModel()
            viewModel.open(1L, bytes())
            val hold = CompletableDeferred<Unit>().also { links.hold = it }

            viewModel.loadShopLink()
            assertThat(viewModel.ready().linkLoading).isTrue()
            // A second tap while it loads sends nothing more.
            viewModel.loadShopLink()
            inCatalog()
            hold.complete(Unit)

            assertThat(links.loaded).containsExactly(SHOP_URL)
            val ready = viewModel.ready()
            assertThat(ready.linkLoading).isFalse()
            assertThat(ready.catalogKey).isEqualTo(CATALOG_KEY)
            assertThat(ready.offersShopLink).isFalse()
        }

    @Test
    fun `a merged tea's key from the fetch is the one Neuen Tee anlegen copies`() = runTest {
        links.outcome = { TeaLinkOutcome.Opened("XY0100") }
        inCatalog("XY0100")
        val viewModel = viewModel()
        viewModel.open(1L, bytes())

        viewModel.loadShopLink()

        assertThat(viewModel.ready().catalogKey).isEqualTo("XY0100")
    }

    @Test
    fun `a failed fetch keeps its CAT-17 outcome and the link offered`() = runTest {
        links.outcome = { TeaLinkOutcome.Offline }
        val viewModel = viewModel()
        viewModel.open(1L, bytes())

        viewModel.loadShopLink()

        assertThat(viewModel.ready().linkFailure).isEqualTo(TeaLinkOutcome.Offline)
        assertThat(viewModel.ready().offersShopLink).isTrue()
        assertThat(links.loaded).containsExactly(SHOP_URL)
    }

    @Test
    fun `a loaded tea you already own is preselected and gets the recipe, with no sheet and no purchase`() = runTest {
        links.outcome = { TeaLinkOutcome.Opened("XY0100") }
        inCatalog("XY0100")
        collection.teas.saveTea(tea("own", "Ganz anders", catalogKey = "XY0100"))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        assertThat(viewModel.ready().offersShopLink).isTrue()
        assertThat(links.loaded).isEmpty()

        viewModel.loadShopLink()

        assertThat(links.loaded).containsExactly(SHOP_URL)
        val ready = viewModel.ready()
        assertThat(ready.match?.id).isEqualTo("own")
        assertThat(ready.matchKind).isEqualTo(MatchKind.CATALOG)
        assertThat(ready.choice).isEqualTo(TeaChoice.Existing("own"))
        assertThat(ready.offersNewTea).isFalse()
        assertThat(ready.offersShopLink).isFalse()

        viewModel.takeOver()

        assertThat(viewModel.navigation.first()).isEqualTo(ReceiveRecipeEvent.TakenOver("own", "Kyusu"))
        assertThat(viewModel.ready().packageSheet).isNull()
        assertThat(fromCatalog.purchases).isEmpty()
        assertThat(collection.stock.observeEvents("own").first()).isEmpty()
        assertThat(collection.teas.observeTeas().first().map { it.tea.id }).containsExactly("own")
        assertThat(collection.recipes.observeRecipes("own").first().map { it.name }).containsExactly("Kyusu")
    }

    @Test
    fun `an archived owner of the loaded tea gets the recipe, also after Neuen Tee anlegen was chosen`() = runTest {
        links.outcome = { TeaLinkOutcome.Opened("XY0100") }
        inCatalog("XY0100")
        collection.teas.saveTea(tea("old", "Ganz anders", catalogKey = "XY0100", archived = true))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.NewTea)
        assertThat(links.loaded).isEmpty()

        viewModel.loadShopLink()

        assertThat(links.loaded).containsExactly(SHOP_URL)
        assertThat(viewModel.ready().offersNewTea).isFalse()
        assertThat(viewModel.ready().choice).isEqualTo(TeaChoice.Existing("old"))
        viewModel.takeOver()
        assertThat(viewModel.navigation.first()).isEqualTo(ReceiveRecipeEvent.TakenOver("old", "Kyusu"))
        assertThat(fromCatalog.purchases).isEmpty()
        assertThat(collection.stock.observeEvents("old").first()).isEmpty()
    }

    @Test
    fun `a loaded tea you own with 10 recipes is greyed out and leaves only Anderen Tee waehlen`() = runTest {
        links.outcome = { TeaLinkOutcome.Opened("XY0100") }
        inCatalog("XY0100")
        collection.teas.saveTea(tea("full", "Ganz anders", catalogKey = "XY0100"))
        repeat(10) { collection.recipes.saveRecipe(recipe("full", it)) }
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        assertThat(links.loaded).isEmpty()

        viewModel.loadShopLink()
        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        assertThat(links.loaded).containsExactly(SHOP_URL)
        val ready = viewModel.ready()
        assertThat(ready.match?.id).isEqualTo("full")
        assertThat(ready.match?.full).isTrue()
        assertThat(ready.choice).isNull()
        assertThat(ready.canTakeOver).isFalse()
        assertThat(ready.offersNewTea).isFalse()
        assertThat(ready.offersShopLink).isFalse()
        assertThat(ready.packageSheet).isNull()
        assertThat(fromCatalog.purchases).isEmpty()
        assertThat(collection.teas.observeTeas().first()).hasSize(1)
        assertThat(collection.recipes.observeRecipes("full").first()).hasSize(10)
    }

    @Test
    fun `the same token is read once, so a recreated screen keeps the choice`() = runTest {
        collection.teas.saveTea(tea("t1", "Sencha"))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.pick("t1")

        viewModel.open(1L, bytes())

        assertThat(viewModel.ready().choice).isEqualTo(TeaChoice.Existing("t1"))
    }

    @Test
    fun `a tea filled up meanwhile ends with the limit and writes nothing`() = runTest {
        collection.teas.saveTea(tea("t1", "Gyokuro Asahi"))
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        val takeOver = RecipeTakeOver(collection.recipes, fromCatalog, withoutCatalog, sorteNames)
        repeat(10) { collection.recipes.saveRecipe(recipe("t1", it)) }

        assertThat(takeOver.takeOver(kyusu, TakeOverTarget.Existing("t1"))).isEqualTo(TakeOverResult.LimitReached)
        assertThat(collection.recipes.observeRecipes("t1").first()).hasSize(10)
    }
}
