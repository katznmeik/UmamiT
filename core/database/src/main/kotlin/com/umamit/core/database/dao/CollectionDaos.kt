package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.database.entity.VesselEntity
import com.umamit.core.model.InventoryEventType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.abs

// Writes use @Upsert (insert or update), never REPLACE: a REPLACE deletes the row
// first and would cascade into its recipes, steps, photos and stock events.

/** A recipe row with its step rows (in any order; sort by `position`). */
data class ProfileWithSteps(
    @Embedded val profile: BrewProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val steps: List<BrewStepEntity>,
)

/** A tea row with all its recipes and its cover photo. */
data class TeaWithDetails(
    @Embedded val tea: TeaEntity,
    @Relation(parentColumn = "id", entityColumn = "tea_id", entity = BrewProfileEntity::class)
    val profiles: List<ProfileWithSteps>,
    @Relation(parentColumn = "cover_photo_id", entityColumn = "id")
    val coverPhoto: PhotoEntity?,
)

@Dao
@Suppress("TooManyFunctions") // one query per need of the tea, its photos and its catalog link (CAT-12)
interface TeaDao {
    @Transaction
    @Query("SELECT * FROM tea ORDER BY name")
    fun observeAll(): Flow<List<TeaWithDetails>>

    /** The teas that are not archived: Start and the tea picker (SCR-02, SCR-03, TEA-04). */
    @Transaction
    @Query("SELECT * FROM tea WHERE is_archived = 0 ORDER BY name")
    fun observeOwned(): Flow<List<TeaWithDetails>>

    @Transaction
    @Query("SELECT * FROM tea WHERE id = :id")
    fun observe(id: String): Flow<TeaWithDetails?>

    @Query("SELECT * FROM tea WHERE id = :id")
    suspend fun get(id: String): TeaEntity?

    /** The catalog keys the collection's teas link to, archived ones included (05 §1.6 soft link). */
    @Query("SELECT DISTINCT catalog_key FROM tea WHERE catalog_key IS NOT NULL")
    fun observeCatalogKeys(): Flow<List<String>>

    /** The catalog keys of the teas that are not archived („im Besitz", CAT-10, TEA-04). */
    @Query("SELECT DISTINCT catalog_key FROM tea WHERE catalog_key IS NOT NULL AND is_archived = 0")
    fun observeOwnedCatalogKeys(): Flow<List<String>>

    @Upsert
    suspend fun upsert(tea: TeaEntity)

    /** *Archivieren* and its undo (TEA-04): returns the number of rows changed (0: no such tea). */
    @Query("UPDATE tea SET is_archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long): Int

    /** Whether the database holds any tea, vessel or session, e.g. after a device restore (M1-07). */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM tea) OR EXISTS(SELECT 1 FROM vessel) OR EXISTS(SELECT 1 FROM brew_session)",
    )
    suspend fun hasPersonalData(): Boolean

    @Query("DELETE FROM tea WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tea SET cover_photo_id = :photoId, updated_at = :now WHERE id = :teaId")
    suspend fun setCoverPhoto(teaId: String, photoId: String?, now: Long)

    /** *Rezept passt* (CAT-05, SCR-06): the one tea's „Rezept prüfen" reason goes, nothing else. */
    @Query("UPDATE tea SET recipe_check_reason = NULL, updated_at = :now WHERE id = :teaId")
    suspend fun clearRecipeCheck(teaId: String, now: Long)

    /** *Geöffnet am* (INV-06). */
    @Query("UPDATE tea SET opened_date = :openedDate, updated_at = :now WHERE id = :teaId")
    suspend fun setOpenedDate(teaId: String, openedDate: LocalDate, now: Long)

    @Upsert
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    /**
     * The collection tea that owns catalog tea [catalogKey] (05 §1.6 soft link): the oldest one
     * not archived; null when there is none, so the detail offers *In Sammlung aufnehmen* (TEA-04).
     */
    @Query(
        "SELECT id FROM tea WHERE catalog_key = :catalogKey AND is_archived = 0 ORDER BY created_at, id LIMIT 1",
    )
    fun observeIdByCatalogKey(catalogKey: String): Flow<String?>

    /**
     * The collection tea linked to catalog tea [catalogKey] (the duplicate guard of CAT-12): a tea
     * not archived first, then the oldest; null when none is. An archived one is brought back (D-085).
     */
    @Query(
        "SELECT id FROM tea WHERE catalog_key = :catalogKey ORDER BY is_archived, created_at, id LIMIT 1",
    )
    suspend fun idByCatalogKey(catalogKey: String): String?
}

@Dao
abstract class RecipeDao {
    @Transaction
    @Query("SELECT * FROM brew_profile WHERE tea_id = :teaId ORDER BY position, name")
    abstract fun observeForTea(teaId: String): Flow<List<ProfileWithSteps>>

    @Transaction
    @Query("SELECT * FROM brew_profile WHERE id = :id")
    abstract suspend fun get(id: String): ProfileWithSteps?

    @Query("SELECT COUNT(*) FROM brew_step WHERE profile_id = :profileId")
    abstract suspend fun stepCount(profileId: String): Int

    @Upsert
    protected abstract suspend fun upsertProfile(profile: BrewProfileEntity)

    @Insert
    protected abstract suspend fun insertSteps(steps: List<BrewStepEntity>)

    @Query("DELETE FROM brew_step WHERE profile_id = :profileId")
    protected abstract suspend fun deleteSteps(profileId: String)

    @Query("UPDATE brew_profile SET is_default = 0 WHERE tea_id = :teaId AND id != :keepId AND is_default = 1")
    protected abstract suspend fun clearOtherDefaults(teaId: String, keepId: String)

    @Query("UPDATE brew_profile SET updated_at = :now WHERE id = :id")
    protected abstract suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM brew_profile WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** Inserts or updates the recipe, replaces its steps and keeps one default per tea. */
    @Transaction
    open suspend fun save(profile: BrewProfileEntity, steps: List<BrewStepEntity>) {
        upsertProfile(profile)
        if (profile.isDefault) clearOtherDefaults(profile.teaId, profile.id)
        deleteSteps(profile.id)
        insertSteps(steps)
    }

    /**
     * Replaces exactly the recipe's steps, each with its pre-alert (*Im Rezept speichern*, BRW-02, SCR-31,
     * D-093); the recipe's own values, its pre-alert base included, stay.
     */
    @Transaction
    open suspend fun replaceSteps(profileId: String, steps: List<BrewStepEntity>, now: Long) {
        touch(profileId, now)
        deleteSteps(profileId)
        insertSteps(steps)
    }
}

@Dao
interface VesselDao {
    @Query("SELECT * FROM vessel ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VesselEntity>>

    @Query("SELECT * FROM vessel WHERE id = :id")
    suspend fun get(id: String): VesselEntity?

    @Upsert
    suspend fun upsert(vessel: VesselEntity)

    @Query("DELETE FROM vessel WHERE id = :id")
    suspend fun delete(id: String)
}

/** A tea's stock as [InventoryDao.observeStockLevels] reads it (INV-05, INV-10). */
data class TeaStockLevel(
    @ColumnInfo(name = "tea_id") val teaId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "style_key") val styleKey: String?,
    @ColumnInfo(name = "catalog_key") val catalogKey: String?,
    @ColumnInfo(name = "product_url") val productUrl: String?,
    @ColumnInfo(name = "low_stock_grams") val lowStockGrams: Double?,
    @ColumnInfo(name = "remaining_grams") val remainingGrams: Double,
    @ColumnInfo(name = "last_purchase_id") val lastPurchaseId: String?,
)

@Dao
abstract class InventoryDao {
    /**
     * Every non-archived tea that has stock events, with its remaining stock and the id of its latest INITIAL
     * or PURCHASE event (INV-05, INV-10): what Start's low-stock card is chosen from. Least stock first,
     * then by name.
     */
    @Query(
        """
        SELECT t.id AS tea_id, t.name AS name, t.style_key AS style_key, t.catalog_key AS catalog_key,
            t.product_url AS product_url, t.low_stock_grams AS low_stock_grams,
            SUM(e.delta_grams) AS remaining_grams,
            (SELECT p.id FROM inventory_event p
                WHERE p.tea_id = t.id AND p.type IN ('INITIAL', 'PURCHASE')
                ORDER BY p.occurred_at DESC, p.created_at DESC, p.id DESC LIMIT 1) AS last_purchase_id
        FROM tea t JOIN inventory_event e ON e.tea_id = t.id
        WHERE t.is_archived = 0
        GROUP BY t.id
        ORDER BY remaining_grams, t.name
        """,
    )
    abstract fun observeStockLevels(): Flow<List<TeaStockLevel>>

    @Query("SELECT * FROM inventory_event WHERE tea_id = :teaId ORDER BY occurred_at, created_at")
    abstract fun observeForTea(teaId: String): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_event WHERE tea_id = :teaId ORDER BY occurred_at, created_at")
    abstract suspend fun eventsForTea(teaId: String): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_event WHERE id = :id")
    abstract suspend fun get(id: String): InventoryEventEntity?

    @Upsert
    abstract suspend fun upsert(event: InventoryEventEntity)

    @Query("DELETE FROM inventory_event WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** The tea's remaining stock in g: the sum of its events' deltas, 0 without any (05 §3.10). */
    @Query("SELECT COALESCE(SUM(delta_grams), 0.0) FROM inventory_event WHERE tea_id = :teaId")
    abstract suspend fun remainingGrams(teaId: String): Double

    /**
     * *Bestand korrigieren* (INV-03, 05 §3.10): writes one `ADJUSTMENT` row [id] whose delta is
     * [newTotalGrams] minus the tea's current stock, read and written in one transaction. Nothing is
     * written when they are equal; the written row is returned, else null.
     */
    @Transaction
    open suspend fun correctStock(teaId: String, newTotalGrams: Double, id: String, now: Long): InventoryEventEntity? {
        val delta = newTotalGrams - remainingGrams(teaId)
        if (abs(delta) < UNCHANGED_GRAMS) return null
        val row = InventoryEventEntity(
            id = id,
            teaId = teaId,
            sessionId = null,
            type = InventoryEventType.ADJUSTMENT,
            deltaGrams = delta,
            priceCents = null,
            currency = null,
            packageLabel = null,
            occurredAt = now,
            note = null,
            createdAt = now,
            updatedAt = now,
        )
        upsert(row)
        return row
    }

    private companion object {
        /** Below this the totals are equal: sums of 0,1 g steps in doubles differ only by rounding noise. */
        const val UNCHANGED_GRAMS = 1e-6
    }
}
