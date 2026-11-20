package com.umamit.feature.collection.receive

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.Recipe
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeTeaLinkLoader
import com.umamit.feature.collection.receive.ReceivedRecipes.CATALOG_KEY
import com.umamit.feature.collection.receive.ReceivedRecipes.SHOP_URL
import com.umamit.feature.collection.receive.ReceivedRecipes.bytes
import com.umamit.feature.collection.receive.ReceivedRecipes.kyusu
import com.umamit.feature.collection.receive.ReceivedRecipes.plain
import com.umamit.feature.collection.receive.ReceivedRecipes.tea
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * *Rezept übernehmen* on a failing database (rule 8): the take-over ends as a failure SCR-32 says, never a crash,
 * and a new tea is saved together with its recipe, so a recipe that can't be saved leaves no new tea (#342).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeTakeOverFailureTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val fromCatalog = FakeAddFromCatalog(collection)
    private val withoutCatalog = FakeAddWithoutCatalog(collection)

    /** The take-over's recipe save fails on the disk; the new tea's own writes go through. */
    private val failing = object : RecipeRepository by collection.recipes {
        override suspend fun saveRecipe(recipe: Recipe): SaveRecipeResult = throw SQLException("disk full")
    }

    private val takeOver = RecipeTakeOver(failing, fromCatalog, withoutCatalog) { null }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReceiveRecipeViewModel(
        teas = collection.teas,
        recipes = collection.recipes,
        catalog = FakeCatalogRepository().apply {
            details.value += CATALOG_KEY to CatalogTeaDetail(
                key = CATALOG_KEY,
                name = "Gyokuro Asahi",
                productUrl = SHOP_URL,
            )
        },
        links = FakeTeaLinkLoader(),
        takeOver = takeOver,
        photos = PhotoFiles(folder.newFolder()),
        readDispatcher = Dispatchers.Main,
    )

    @Test
    fun `a failing recipe save ends as Failed`() = runTest {
        collection.teas.saveTea(tea("t1", "Gyokuro Asahi"))

        assertThat(takeOver.takeOver(kyusu, TakeOverTarget.Existing("t1"))).isEqualTo(TakeOverResult.Failed)
    }

    @Test
    fun `a failing recipe save leaves no new tea from the catalog and keeps the sheet with its message`() = runTest {
        val viewModel = viewModel()
        viewModel.open(1L, bytes())
        viewModel.choose(TeaChoice.NewTea)
        viewModel.takeOver()

        viewModel.savePackage(CatalogPurchase(CATALOG_KEY, 100.0, null, LocalDate.of(2026, 11, 18)))

        val ready = viewModel.state.value as ReceiveRecipeUiState.Ready
        assertThat(ready.packageSheet).isEqualTo(PackageSheet(CATALOG_KEY, failed = true))
        assertThat(ready.saving).isFalse()
        assertThat(collection.teas.observeTeas().first()).isEmpty()
        assertThat(collection.stock.observeEvents("tea-$CATALOG_KEY").first()).isEmpty()
        assertThat(fromCatalog.purchases).isEmpty()
    }

    @Test
    fun `a failing recipe save leaves no new tea without catalog`() = runTest {
        val viewModel = viewModel()
        viewModel.open(1L, bytes(plain))
        viewModel.choose(TeaChoice.NewTea)

        viewModel.takeOver()

        val ready = viewModel.state.value as ReceiveRecipeUiState.Ready
        assertThat(ready.failure).isEqualTo(TakeOverResult.Failed)
        assertThat(collection.teas.observeTeas().first()).isEmpty()
        assertThat(collection.recipes.observeRecipes("manual-1").first()).isEmpty()
    }
}
