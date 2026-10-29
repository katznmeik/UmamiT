package com.umamit.core.testing

import com.umamit.core.common.snap5
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StockSummary
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.Vessel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.math.abs

// In-memory fakes of the core:data repositories for tests that need no database.
// They follow the Room implementations' observable behaviour: name ordering, one
// default recipe per tea, temperatures snapped to 5 °C, cascades on deletion (05 §3).

/**
 * An in-memory collection shared by its four repositories, so that deleting a tea
 * cascades to its recipes, photos and stock events, and deleting a vessel clears it
 * from recipes, as in the database.
 */
class FakeCollection {
    internal val teaRows = MutableStateFlow<Map<String, Tea>>(emptyMap())
    internal val recipeRows = MutableStateFlow<Map<String, Recipe>>(emptyMap())
    internal val vesselRows = MutableStateFlow<Map<String, Vessel>>(emptyMap())
    internal val photoRows = MutableStateFlow<Map<String, Photo>>(emptyMap())
    internal val eventRows = MutableStateFlow<Map<String, InventoryEvent>>(emptyMap())

    val teas = FakeTeaRepository(this)
    val recipes = FakeRecipeRepository(this)
    val vessels = FakeVesselRepository(this)
    val stock = FakeStockRepository(this)
}

/** Fake [TeaRepository]; create it through [FakeCollection]. */
class FakeTeaRepository internal constructor(private val c: FakeCollection) : TeaRepository {
    private val overviews: Flow<List<TeaOverview>> = combine(c.teaRows, c.recipeRows, c.photoRows) { t, r, p ->
        t.values.sortedBy { it.name.lowercase() }.map { tea ->
            TeaOverview(
                tea = tea,
                defaultRecipe = r.values.firstOrNull { it.teaId == tea.id && it.isDefault },
                coverPhoto = tea.coverPhotoId?.let(p::get),
            )
        }
    }

    override fun observeTeas(): Flow<List<TeaOverview>> = overviews

    override fun observeOwnedTeas(): Flow<List<TeaOverview>> = overviews.map { list ->
        list.filter { !it.tea.isArchived }
    }

    override fun observeTea(id: String): Flow<TeaOverview?> = overviews.map { list -> list.find { it.tea.id == id } }

    override suspend fun getTea(id: String): Tea? = c.teaRows.value[id]

    override suspend fun saveTea(tea: Tea) = c.teaRows.update { it + (tea.id to tea) }

    override suspend fun deleteTea(id: String) {
        c.recipeRows.update { map -> map.filterValues { it.teaId != id } }
        c.photoRows.update { map -> map.filterValues { it.owner.id != id } }
        c.eventRows.update { map -> map.filterValues { it.teaId != id } }
        c.teaRows.update { it - id }
    }

    override suspend fun savePhoto(photo: Photo) = c.photoRows.update { it + (photo.id to photo) }

    override suspend fun setCoverPhoto(teaId: String, photoId: String?) = c.teaRows.update { map ->
        map[teaId]?.let { map + (teaId to it.copy(coverPhotoId = photoId)) } ?: map
    }

    override suspend fun clearRecipeCheck(teaId: String) = c.teaRows.update { map ->
        map[teaId]?.let { map + (teaId to it.copy(recipeCheckReason = null)) } ?: map
    }
}

/** Fake [RecipeRepository]; create it through [FakeCollection]. */
class FakeRecipeRepository internal constructor(private val c: FakeCollection) : RecipeRepository {
    override fun observeRecipes(teaId: String): Flow<List<Recipe>> = c.recipeRows.map { map ->
        map.values.filter { it.teaId == teaId }.sortedWith(compareBy({ it.position }, { it.name }))
    }

    override suspend fun getRecipe(id: String): Recipe? = c.recipeRows.value[id]

    override suspend fun saveRecipe(recipe: Recipe) {
        val snapped = recipe.copy(baseTempC = snap5(recipe.baseTempC), steps = recipe.steps.snapped())
        c.recipeRows.update { map ->
            val others = if (snapped.isDefault) {
                map.mapValues { (_, r) -> if (r.teaId == snapped.teaId) r.copy(isDefault = false) else r }
            } else {
                map
            }
            others + (snapped.id to snapped)
        }
    }

    override suspend fun replaceSteps(recipeId: String, steps: List<RecipeStep>) = c.recipeRows.update { map ->
        val recipe = map[recipeId] ?: return@update map
        map + (recipeId to recipe.copy(steps = steps.snapped()))
    }

    override suspend fun deleteRecipe(id: String) = c.recipeRows.update { it - id }
}

/** Fake [VesselRepository]; create it through [FakeCollection]. */
class FakeVesselRepository internal constructor(private val c: FakeCollection) : VesselRepository {
    override fun observeVessels(): Flow<List<Vessel>> =
        c.vesselRows.map { map -> map.values.sortedBy { it.name.lowercase() } }

    override suspend fun getVessel(id: String): Vessel? = c.vesselRows.value[id]

    override suspend fun saveVessel(vessel: Vessel) = c.vesselRows.update { it + (vessel.id to vessel) }

    override suspend fun deleteVessel(id: String) {
        c.recipeRows.update { map -> map.mapValues { (_, r) -> if (r.vesselId == id) r.copy(vesselId = null) else r } }
        c.vesselRows.update { it - id }
    }
}

/** Fake [StockRepository]; create it through [FakeCollection]. */
class FakeStockRepository internal constructor(private val c: FakeCollection) : StockRepository {
    override fun observeEvents(teaId: String): Flow<List<InventoryEvent>> =
        c.eventRows.map { map -> map.values.filter { it.teaId == teaId }.sortedBy { it.occurredAtMillis } }

    override fun observeStock(teaId: String): Flow<StockSummary> = observeEvents(teaId).map(StockSummary::of)

    override suspend fun addEvent(event: InventoryEvent) = c.eventRows.update { it + (event.id to event) }

    override suspend fun deleteEvent(id: String) = c.eventRows.update { it - id }

    /** As on Room: one ADJUSTMENT of the difference at [now], none when the stock is unchanged. */
    override suspend fun correctStock(teaId: String, newTotalGrams: Double): InventoryEvent? {
        require(newTotalGrams.isFinite() && newTotalGrams >= 0.0) { "A stock total is at least 0 g" }
        val delta = newTotalGrams - c.eventRows.value.values.filter { it.teaId == teaId }.sumOf { it.deltaGrams }
        if (abs(delta) < UNCHANGED_GRAMS) return null
        val event = InventoryEvent(
            id = "adjustment-${c.eventRows.value.size + 1}",
            teaId = teaId,
            type = InventoryEventType.ADJUSTMENT,
            deltaGrams = delta,
            occurredAtMillis = now,
            currency = null,
        )
        addEvent(event)
        return event
    }

    /** The time of the next [correctStock]. */
    var now: Long = 0L

    private companion object {
        const val UNCHANGED_GRAMS = 1e-6
    }
}

/** Half-up snap to 5 °C (GEN-12), as the Room repositories do. */
private fun List<RecipeStep>.snapped() = map { step -> step.copy(tempC = step.tempC?.let { snap5(it) }) }
