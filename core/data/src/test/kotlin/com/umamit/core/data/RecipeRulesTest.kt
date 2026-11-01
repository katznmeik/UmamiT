package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The recipe rules of v0.4.0 (PRF-01, TEA-17, D-100) on the database in memory: exactly one default
 * and at most 10 recipes per tea, the next recipe becomes the default when the default goes, the last
 * recipe stays, and saving a recipe clears „Rezept prüfen" from both save paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeRulesTest {
    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    private suspend fun defaults(teaId: String = "t1") =
        data.recipes.observeRecipes(teaId).first().filter { it.isDefault }.map { it.id }

    @Test
    fun `deleting the default makes the first remaining recipe by position the default`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r0", "t1", isDefault = true).copy(position = 0))
        data.recipes.saveRecipe(recipe("r2", "t1").copy(position = 2))
        data.recipes.saveRecipe(recipe("r1", "t1").copy(position = 1))

        assertThat(data.recipes.deleteRecipe("r0")).isEqualTo(DeleteRecipeResult.Deleted)

        assertThat(defaults()).containsExactly("r1")
        assertThat(data.recipes.observeRecipes("t1").first().map { it.id }).containsExactly("r1", "r2").inOrder()
    }

    @Test
    fun `deleting another recipe keeps the default`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r0", "t1").copy(position = 0))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true).copy(position = 1))

        assertThat(data.recipes.deleteRecipe("r0")).isEqualTo(DeleteRecipeResult.Deleted)

        assertThat(defaults()).containsExactly("r1")
    }

    @Test
    fun `the last recipe of a tea can't be deleted`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))
        data.recipes.saveRecipe(recipe("r2", "t2", isDefault = true))

        assertThat(data.recipes.deleteRecipe("r1")).isEqualTo(DeleteRecipeResult.LastRecipe)

        assertThat(data.recipes.getRecipe("r1")).isNotNull()
        assertThat(defaults()).containsExactly("r1")
    }

    @Test
    fun `deleting a recipe that is gone reports it`() = runTest {
        assertThat(data.recipes.deleteRecipe("gone")).isEqualTo(DeleteRecipeResult.NotFound)
    }

    @Test
    fun `a tea's first recipe becomes its default even when saved without the flag`() = runTest {
        data.teas.saveTea(tea("t1"))

        data.recipes.saveRecipe(recipe("r1", "t1"))
        data.recipes.saveRecipe(recipe("r2", "t1").copy(position = 1))

        assertThat(defaults()).containsExactly("r1")
    }

    @Test
    fun `Als Standard moves the default, and the tea always keeps exactly one`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true).copy(position = 0))
        data.recipes.saveRecipe(recipe("r2", "t1").copy(position = 1))

        data.recipes.saveRecipe(data.recipes.getRecipe("r2")!!.copy(isDefault = true))
        assertThat(defaults()).containsExactly("r2")

        // Saving the default without the flag can't leave the tea without one: the first by position takes it.
        data.recipes.saveRecipe(data.recipes.getRecipe("r2")!!.copy(isDefault = false))
        assertThat(defaults()).containsExactly("r1")
    }

    @Test
    fun `a tea holds at most 10 recipes, an existing one can still be saved`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        repeat(Recipe.MAX_PER_TEA) { i ->
            assertThat(data.recipes.saveRecipe(recipe("r$i", "t1").copy(position = i)))
                .isEqualTo(SaveRecipeResult.Saved)
        }

        assertThat(data.recipes.saveRecipe(recipe("r10", "t1").copy(position = 10)))
            .isEqualTo(SaveRecipeResult.LimitReached)

        assertThat(data.recipes.getRecipe("r10")).isNull()
        assertThat(data.recipes.observeRecipes("t1").first()).hasSize(10)
        // Editing one of the ten, and another tea's recipe, still works.
        assertThat(data.recipes.saveRecipe(data.recipes.getRecipe("r3")!!.copy(name = "Kaltaufguss")))
            .isEqualTo(SaveRecipeResult.Saved)
        assertThat(data.recipes.getRecipe("r3")!!.name).isEqualTo("Kaltaufguss")
        assertThat(data.recipes.saveRecipe(recipe("x1", "t2"))).isEqualTo(SaveRecipeResult.Saved)
    }

    @Test
    fun `saving a recipe in the recipe editor clears the tea's Rezept pruefen and no other tea's`() = runTest {
        data.teas.saveTea(tea("t1").copy(recipeCheckReason = RecipeCheckReason.FROM_TEXT))
        data.teas.saveTea(tea("t2").copy(recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT))
        data.recipes.saveRecipe(recipe("r2", "t2", isDefault = true))
        data.teas.saveTea(data.teas.getTea("t2")!!.copy(recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT))

        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true).copy(name = "Kyusu"))

        assertThat(data.teas.getTea("t1")!!.recipeCheckReason).isNull()
        assertThat(data.teas.getTea("t2")!!.recipeCheckReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun `Im Rezept speichern of the steps clears the tea's Rezept pruefen`() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))
        data.teas.saveTea(data.teas.getTea("t1")!!.copy(recipeCheckReason = RecipeCheckReason.FROM_TEXT))

        data.recipes.replaceSteps("r1", listOf(RecipeStep(StepKind.INFUSION, 45)))

        assertThat(data.teas.getTea("t1")!!.recipeCheckReason).isNull()
        assertThat(data.recipes.getRecipe("r1")!!.steps).containsExactly(RecipeStep(StepKind.INFUSION, 45))
    }
}
