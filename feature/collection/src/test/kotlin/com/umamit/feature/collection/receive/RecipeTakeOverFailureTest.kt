package com.umamit.feature.collection.receive

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.model.Recipe
import com.umamit.core.testing.FakeAddFromCatalog
import com.umamit.core.testing.FakeAddWithoutCatalog
import com.umamit.core.testing.FakeCollection
import com.umamit.feature.collection.receive.ReceivedRecipes.kyusu
import com.umamit.feature.collection.receive.ReceivedRecipes.tea
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Rezept übernehmen* on a failing database (rule 8): the take-over ends as a failure SCR-32 says, never a crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeTakeOverFailureTest {
    private val collection = FakeCollection()

    @Test
    fun `a failing recipe save ends as Failed`() = runTest {
        collection.teas.saveTea(tea("t1", "Gyokuro Asahi"))
        val failing = object : RecipeRepository by collection.recipes {
            override suspend fun saveRecipe(recipe: Recipe): SaveRecipeResult = throw SQLException("disk full")
        }
        val takeOver = RecipeTakeOver(
            failing,
            FakeAddFromCatalog(collection),
            FakeAddWithoutCatalog(collection),
        ) { null }

        assertThat(takeOver.takeOver(kyusu, TakeOverTarget.Existing("t1"))).isEqualTo(TakeOverResult.Failed)
    }
}
