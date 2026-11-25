package com.umamit.core.data

import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.core.data.catalog.CatalogColumns
import com.umamit.core.database.dao.InventoryDao
import com.umamit.core.database.dao.RecipeDao
import com.umamit.core.database.dao.RecipeDeletion
import com.umamit.core.database.dao.SessionCost
import com.umamit.core.database.dao.TeaDao
import com.umamit.core.database.dao.VesselDao
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

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
        RecipeDeletion.IN_ACTIVE_SESSION -> DeleteRecipeResult.InActiveSession
        RecipeDeletion.NOT_FOUND -> DeleteRecipeResult.NotFound
    }

    override fun observeActiveRecipeId(): Flow<String?> = dao.observeActiveRecipeId().distinctUntilChanged()
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

    override suspend fun deleteEvent(id: String): Boolean = dao.deleteAndRecost(id, time.nowMs(), SESSION_COST)

    override suspend fun correctStock(teaId: String, newTotalGrams: Double): InventoryEvent? {
        require(newTotalGrams.isFinite() && newTotalGrams >= 0.0) { "A stock total is at least 0 g" }
        return dao.correctStock(teaId, newTotalGrams, UUID.randomUUID().toString(), time.nowMs())?.toModel()
    }

    override suspend fun recordPurchase(teaId: String, purchase: TeaPurchase): InventoryEvent {
        val today = time.today()
        require(!purchase.purchasedOn.isAfter(today)) { "A purchase is never in the future" }
        val event = purchaseEvent(
            teaId,
            InventoryEventType.PURCHASE,
            purchase.grams,
            purchase.priceCents,
            purchase.purchasedOn,
            time,
        )
        val now = time.nowMs()
        dao.recordPurchase(event.toEntity(createdAt = now, updatedAt = now), now)
        return event
    }

    override suspend fun updatePurchase(id: String, grams: Double, priceCents: Long?, purchasedOn: LocalDate): Boolean {
        require(grams.isFinite() && grams > 0.0) { "A package weighs more than 0 g" }
        require(priceCents == null || priceCents >= 0) { "A price is at least 0" }
        require(!purchasedOn.isAfter(time.today())) { "A purchase is never in the future" }
        val now = time.nowMs()
        return dao.editEvent(id, now, SESSION_COST) { stored ->
            stored.takeIf { it.type.isPackage }?.copy(
                deltaGrams = grams,
                priceCents = priceCents,
                currency = stored.currency ?: InventoryEvent.DEFAULT_CURRENCY.takeIf { priceCents != null },
                packageLabel = if (grams == stored.deltaGrams) {
                    stored.packageLabel
                } else {
                    CatalogColumns.packageLabel(grams, null)
                },
                occurredAt = occurredAt(stored.occurredAt, purchasedOn, now),
                updatedAt = now,
            )
        }
    }

    override suspend fun updateCorrection(id: String, signedDeltaGrams: Double, day: LocalDate): Boolean {
        require(signedDeltaGrams.isFinite() && abs(signedDeltaGrams) >= UNCHANGED_GRAMS) { "A correction is not 0 g" }
        require(!day.isAfter(time.today())) { "A correction is never in the future" }
        val now = time.nowMs()
        return dao.editEvent(id, now, recost = null) { stored ->
            stored.takeIf { it.type == InventoryEventType.ADJUSTMENT }?.copy(
                deltaGrams = signedDeltaGrams,
                occurredAt = occurredAt(stored.occurredAt, day, now),
                updatedAt = now,
            )
        }
    }

    /** The edited event's time: unchanged on its own day, else now for today or noon of [day] in the phone's zone. */
    private fun occurredAt(stored: Long, day: LocalDate, now: Long): Long {
        val zone = time.zone()
        return when (day) {
            Instant.ofEpochMilli(stored).atZone(zone).toLocalDate() -> stored
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate() -> now
            else -> day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private companion object {
        /** Below this a correction is 0 g, as `correctStock` reads equal totals. */
        const val UNCHANGED_GRAMS = 1e-6

        /** INV-08's finish rule ([costCents]) for the recompute after a purchase is edited or deleted (D-136). */
        val SESSION_COST = SessionCost { leaf, events -> costCents(leaf, events.map { it.toModel() }) }
    }
}
