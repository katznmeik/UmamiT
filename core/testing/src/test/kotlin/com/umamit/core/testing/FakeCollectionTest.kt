package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.AddWithoutCatalogResult
import com.umamit.core.data.DeleteRecipeResult
import com.umamit.core.data.DeleteResult
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.data.TeaPurchase
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** The fakes behave like the Room repositories where tests rely on it. */
class FakeCollectionTest {
    private val collection = FakeCollection()

    private fun recipe(id: String, isDefault: Boolean) = Recipe(
        id = id,
        teaId = "t1",
        name = id,
        method = BrewMethod.KYUSU,
        leafGrams = 4.0,
        waterMl = 100,
        baseTempC = 58,
        steps = listOf(RecipeStep(StepKind.INFUSION, 60, 52)),
        isDefault = isDefault,
    )

    @Test
    fun keepsOneDefaultSnapsTemperaturesAndCascadesOnTeaDeletion() = runTest {
        collection.teas.saveTea(Tea("t1", "Hisui", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
        collection.recipes.saveRecipe(recipe("r1", isDefault = true))
        collection.recipes.saveRecipe(recipe("r2", isDefault = true))
        collection.stock.addEvent(InventoryEvent("e1", "t1", InventoryEventType.INITIAL, 100.0, 1L, priceCents = 1790))

        val overview = collection.teas.observeTea("t1").first()!!
        assertThat(overview.defaultRecipe?.id).isEqualTo("r2")
        assertThat(overview.defaultRecipe?.baseTempC).isEqualTo(60)
        assertThat(overview.defaultRecipe?.steps?.single()?.tempC).isEqualTo(50)
        assertThat(collection.stock.observeStock("t1").first().costPerGramCents).isWithin(1e-9).of(17.9)

        collection.teas.deleteTea("t1")

        assertThat(collection.recipes.observeRecipes("t1").first()).isEmpty()
        assertThat(collection.stock.observeEvents("t1").first()).isEmpty()
    }

    @Test
    fun followsTheRecipeRulesOfPrf01AndTea17() = runTest {
        collection.teas.saveTea(
            Tea("t1", "Hisui", TeaCategory.GREEN, TeaForm.LOOSE_LEAF, recipeCheckReason = RecipeCheckReason.FROM_TEXT),
        )
        collection.recipes.saveRecipe(recipe("r1", isDefault = false))
        collection.recipes.saveRecipe(recipe("r2", isDefault = false).copy(position = 1))
        // The first recipe became the default, and saving cleared „Rezept prüfen".
        assertThat(collection.teas.observeTea("t1").first()!!.defaultRecipe?.id).isEqualTo("r1")
        assertThat(collection.teas.getTea("t1")!!.recipeCheckReason).isNull()

        assertThat(collection.recipes.deleteRecipe("r1")).isEqualTo(DeleteRecipeResult.Deleted)
        assertThat(collection.teas.observeTea("t1").first()!!.defaultRecipe?.id).isEqualTo("r2")
        assertThat(collection.recipes.deleteRecipe("r2")).isEqualTo(DeleteRecipeResult.LastRecipe)
        assertThat(collection.recipes.deleteRecipe("gone")).isEqualTo(DeleteRecipeResult.NotFound)

        repeat(Recipe.MAX_PER_TEA - 1) { collection.recipes.saveRecipe(recipe("x$it", isDefault = false)) }
        assertThat(collection.recipes.saveRecipe(recipe("x10", isDefault = false)))
            .isEqualTo(SaveRecipeResult.LimitReached)
    }

    @Test
    fun deletingATeaKeepsItsSessionsByNameAndTheActiveTeaStays() = runTest {
        val sessions = FakeSessions(collection)
        val archive = FakeTeaArchive(sessions)
        collection.teas.saveTea(Tea("t1", "Hisui", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
        collection.recipes.saveRecipe(recipe("r1", isDefault = true))

        assertThat(archive.delete("t1")).isEqualTo(DeleteResult.Deleted)
        assertThat(archive.delete("t1")).isEqualTo(DeleteResult.NotFound)
        assertThat(collection.recipes.getRecipe("r1")).isNull()
    }

    @Test
    fun addingWithoutTheCatalogWritesTheStyleRecipesAndStockOnlyWithAPackage() = runTest {
        val add = FakeAddWithoutCatalog(collection)
        val draft = Tea("", "Geschenk", TeaCategory.GREEN, TeaForm.LOOSE_LEAF, styleKey = "sencha")

        val withPackage = add.add(draft, TeaPurchase(50.0, 990, LocalDate.of(2026, 10, 31)))
        val without = add.add(draft.copy(styleKey = null), null)

        val first = (withPackage as AddWithoutCatalogResult.Added).teaId
        val second = (without as AddWithoutCatalogResult.Added).teaId
        assertThat(collection.teas.observeTea(first).first()!!.defaultRecipe?.name).isEqualTo("Kyusu")
        assertThat(collection.stock.observeEvents(first).first().single().deltaGrams).isEqualTo(50.0)
        assertThat(collection.recipes.observeRecipes(second).first().single().method).isEqualTo(BrewMethod.WESTERN)
        assertThat(collection.stock.observeEvents(second).first()).isEmpty()
    }

    @Test
    fun settingsFakeStartsWithTheSpecDefaults() = runTest {
        val settings = FakeSettingsRepository()

        assertThat(settings.settings.first().preAlertSeconds).isEqualTo(10)
        assertThat(settings.seedDataCreated.first()).isFalse()
    }
}
