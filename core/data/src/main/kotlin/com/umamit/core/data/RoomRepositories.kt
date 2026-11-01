package com.umamit.core.data

import com.umamit.core.common.WallClock
import com.umamit.core.database.dao.InventoryDao
import com.umamit.core.database.dao.RecipeDao
import com.umamit.core.database.dao.RecipeDeletion
import com.umamit.core.database.dao.TeaDao
import com.umamit.core.database.dao.VesselDao
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.Photo
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StockSummary
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.Vessel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/** [TeaRepository] on Room (05 §3.1, §3.9). */
class RoomTeaRepository @Inject constructor(private val dao: TeaDao, private val time: WallClock) : TeaRepository {
    override fun observeTeas(): Flow<List<TeaOverview>> = dao.observeAll().map { rows -> rows.map { it.toOverview() } }

    override fun observeOwnedTeas(): Flow<List<TeaOverview>> =
        dao.observeOwned().map { rows -> rows.map { it.toOverview() } }

    override fun observeTea(id: String): Flow<TeaOverview?> = dao.observe(id).map { it?.toOverview() }

    override suspend fun getTea(id: String): Tea? = dao.get(id)?.toModel()

    override suspend fun saveTea(tea: Tea) {
        val now = time.nowMs()
        dao.upsert(tea.toEntity(createdAt = dao.get(tea.id)?.createdAt ?: now, updatedAt = now))
    }

    override suspend fun deleteTea(id: String) = dao.delete(id)

    override suspend fun savePhoto(photo: Photo) {
        val now = time.nowMs()
        dao.upsertPhoto(photo.toEntity(createdAt = dao.photo(photo.id)?.createdAt ?: now, updatedAt = now))
    }

    override suspend fun setCoverPhoto(teaId: String, photoId: String?) =
        dao.setCoverPhoto(teaId, photoId, time.nowMs())

    override suspend fun clearRecipeCheck(teaId: String) = dao.clearRecipeCheck(teaId, time.nowMs())
}

/** [RecipeRepository] on Room (05 §3.2, §3.3); temperatures snapped to 5 °C (GEN-12). */
class RoomRecipeRepository @Inject constructor(private val dao: RecipeDao, private val time: WallClock) :
    RecipeRepository {
    override fun observeRecipes(teaId: String): Flow<List<Recipe>> =
        dao.observeForTea(teaId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getRecipe(id: String): Recipe? = dao.get(id)?.toModel()

    override suspend fun saveRecipe(recipe: Recipe): SaveRecipeResult {
        val now = time.nowMs()
        val createdAt = dao.get(recipe.id)?.profile?.createdAt ?: now
        val saved = dao.save(recipe.toEntity(createdAt, now), recipe.steps.toEntities(recipe.id, now), now)
        return if (saved) SaveRecipeResult.Saved else SaveRecipeResult.LimitReached
    }

    override suspend fun replaceSteps(recipeId: String, steps: List<RecipeStep>) {
        val now = time.nowMs()
        dao.replaceSteps(recipeId, steps.toEntities(recipeId, now), now)
    }

    override suspend fun deleteRecipe(id: String): DeleteRecipeResult = when (dao.deleteKeepingOne(id, time.nowMs())) {
        RecipeDeletion.DELETED -> DeleteRecipeResult.Deleted
        RecipeDeletion.LAST_RECIPE -> DeleteRecipeResult.LastRecipe
        RecipeDeletion.NOT_FOUND -> DeleteRecipeResult.NotFound
    }
}

/** [VesselRepository] on Room (05 §3.4). */
class RoomVesselRepository @Inject constructor(private val dao: VesselDao, private val time: WallClock) :
    VesselRepository {
    override fun observeVessels(): Flow<List<Vessel>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getVessel(id: String): Vessel? = dao.get(id)?.toModel()

    override suspend fun saveVessel(vessel: Vessel) {
        val now = time.nowMs()
        dao.upsert(vessel.toEntity(createdAt = dao.get(vessel.id)?.createdAt ?: now, updatedAt = now))
    }

    override suspend fun deleteVessel(id: String) = dao.delete(id)
}

/** [StockRepository] on Room (05 §3.10). */
class RoomStockRepository @Inject constructor(private val dao: InventoryDao, private val time: WallClock) :
    StockRepository {
    override fun observeEvents(teaId: String): Flow<List<InventoryEvent>> =
        dao.observeForTea(teaId).map { rows -> rows.map { it.toModel() } }

    override fun observeStock(teaId: String): Flow<StockSummary> = observeEvents(teaId).map(StockSummary::of)

    override suspend fun addEvent(event: InventoryEvent) {
        val now = time.nowMs()
        dao.upsert(event.toEntity(createdAt = dao.get(event.id)?.createdAt ?: now, updatedAt = now))
    }

    override suspend fun deleteEvent(id: String) = dao.delete(id)

    override suspend fun correctStock(teaId: String, newTotalGrams: Double): InventoryEvent? {
        require(newTotalGrams.isFinite() && newTotalGrams >= 0.0) { "A stock total is at least 0 g" }
        return dao.correctStock(teaId, newTotalGrams, UUID.randomUUID().toString(), time.nowMs())?.toModel()
    }
}
