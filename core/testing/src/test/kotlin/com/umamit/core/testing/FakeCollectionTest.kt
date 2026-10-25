package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
    fun settingsFakeStartsWithTheSpecDefaults() = runTest {
        val settings = FakeSettingsRepository()

        assertThat(settings.settings.first().preAlertSeconds).isEqualTo(10)
        assertThat(settings.seedDataCreated.first()).isFalse()
    }
}
