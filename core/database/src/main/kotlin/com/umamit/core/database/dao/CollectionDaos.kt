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
import com.umamit.core.model.Recipe
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

/**
 * What *Meine Tees* sorts and filters a tea by besides its own columns (SCR-08, TEA-08–TEA-11, D-100), as
 * [TeaDao.observeListFacts] reads it: the start of its latest session (null: never brewed) and the Σ of its stock
 * events (null: none).
 */
data class TeaListFacts(
    @ColumnInfo(name = "tea_id") val teaId: String,
    @ColumnInfo(name = "last_brewed_at") val lastBrewedAt: Long?,
    @ColumnInfo(name = "remaining_grams") val remainingGrams: Double?,
)

@Dao
@Suppress("TooManyFunctions") // one query per need of the tea, its photos and its catalog link (CAT-12)
interface TeaDao {
    @Transaction
    @Query("SELECT * FROM tea ORDER BY name")
    fun observeAll(): Flow<List<TeaWithDetails>>

    /**
     * Every tea, archived ones included, with [TeaListFacts] (SCR-08, D-100): the list's sort by *Zuletzt
     * gebrüht* and *Vorrat*, and its stock chips. Emits again on every write to the teas,
     * their sessions and their stock events.
     */
    @Query(
        """
        SELECT t.id AS tea_id,
            (SELECT MAX(s.started_at) FROM brew_session s WHERE s.tea_id = t.id) AS last_brewed_at,
            (SELECT SUM(e.delta_grams) FROM inventory_event e WHERE e.tea_id = t.id) AS remaining_grams
        FROM tea t
        """,
    )
    fun observeListFacts(): Flow<List<TeaListFacts>>

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

    /** The ids of the tea's own photos, whose files go when the tea is deleted (TEA-04, D-100). */
    @Query("SELECT id FROM photo WHERE tea_id = :teaId")
    suspend fun photoIds(teaId: String): List<String>

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
@Suppress("TooManyFunctions") // the recipe rules of PRF-01 and TEA-17 each need their query
abstract class RecipeDao {
    @Transaction
    @Query("SELECT * FROM brew_profile WHERE tea_id = :teaId ORDER BY position, name")
    abstract fun observeForTea(teaId: String): Flow<List<ProfileWithSteps>>

    @Transaction
    @Query("SELECT * FROM brew_profile WHERE id = :id")
    abstract suspend fun get(id: String): ProfileWithSteps?

    @Query("SELECT COUNT(*) FROM brew_step WHERE profile_id = :profileId")
    abstract suspend fun stepCount(profileId: String): Int

    @Query("SELECT tea_id FROM brew_profile WHERE id = :id")
    protected abstract suspend fun teaIdOf(id: String): String?

    @Query("SELECT COUNT(*) FROM brew_profile WHERE tea_id = :teaId")
    protected abstract suspend fun countForTea(teaId: String): Int

    @Upsert
    protected abstract suspend fun upsertProfile(profile: BrewProfileEntity)

    @Insert
    protected abstract suspend fun insertSteps(steps: List<BrewStepEntity>)

    @Query("DELETE FROM brew_step WHERE profile_id = :profileId")
    protected abstract suspend fun deleteSteps(profileId: String)

    @Query("UPDATE brew_profile SET is_default = 0 WHERE tea_id = :teaId AND id != :keepId AND is_default = 1")
    protected abstract suspend fun clearOtherDefaults(teaId: String, keepId: String)

    /** A tea without a default makes its first recipe (by position) the default (PRF-01). */
    @Query(
        """
        UPDATE brew_profile SET is_default = 1, updated_at = :now
        WHERE id = (SELECT id FROM brew_profile WHERE tea_id = :teaId ORDER BY position, name, id LIMIT 1)
            AND NOT EXISTS (SELECT 1 FROM brew_profile WHERE tea_id = :teaId AND is_default = 1)
        """,
    )
    protected abstract suspend fun ensureDefault(teaId: String, now: Long)

    /** TEA-17 (D-100): a saved recipe clears its tea's „Rezept prüfen" reason. */
    @Query(
        "UPDATE tea SET recipe_check_reason = NULL, updated_at = :now " +
            "WHERE id = :teaId AND recipe_check_reason IS NOT NULL",
    )
    protected abstract suspend fun clearRecipeCheck(teaId: String, now: Long)

    @Query("UPDATE brew_profile SET updated_at = :now WHERE id = :id")
    protected abstract suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM brew_profile WHERE id = :id")
    protected abstract suspend fun deleteRow(id: String)

    /**
     * Inserts or updates the recipe, replaces its steps, keeps exactly one default per tea (PRF-01) and
     * clears the tea's „Rezept prüfen" (TEA-17). A new recipe of a tea that already has
     * [Recipe.MAX_PER_TEA] is refused: returns false and writes nothing.
     */
    @Transaction
    open suspend fun save(profile: BrewProfileEntity, steps: List<BrewStepEntity>, now: Long): Boolean {
        if (teaIdOf(profile.id) == null && countForTea(profile.teaId) >= Recipe.MAX_PER_TEA) return false
        upsertProfile(profile)
        if (profile.isDefault) clearOtherDefaults(profile.teaId, profile.id)
        ensureDefault(profile.teaId, now)
        deleteSteps(profile.id)
        insertSteps(steps)
        clearRecipeCheck(profile.teaId, now)
        return true
    }

    /**
     * Replaces exactly the recipe's steps, each with its pre-alert (*Im Rezept speichern*, BRW-02, SCR-31,
     * D-093); the recipe's own values, its pre-alert base included, stay. Clears the tea's „Rezept prüfen"
     * (TEA-17).
     */
    @Transaction
    open suspend fun replaceSteps(profileId: String, steps: List<BrewStepEntity>, now: Long) {
        touch(profileId, now)
        deleteSteps(profileId)
        insertSteps(steps)
        teaIdOf(profileId)?.let { clearRecipeCheck(it, now) }
    }

    /**
     * Deletes the recipe unless it is its tea's last one (PRF-01); a deleted default passes to the first
     * remaining recipe by position.
     */
    @Transaction
    open suspend fun deleteKeepingOne(id: String, now: Long): RecipeDeletion {
        val teaId = teaIdOf(id)
        return when {
            teaId == null -> RecipeDeletion.NOT_FOUND

            countForTea(teaId) <= 1 -> RecipeDeletion.LAST_RECIPE

            else -> {
                deleteRow(id)
                ensureDefault(teaId, now)
                RecipeDeletion.DELETED
            }
        }
    }
}

/** How [RecipeDao.deleteKeepingOne] ended (PRF-01). */
enum class RecipeDeletion {
    /** The recipe is gone; a deleted default passed to the first remaining recipe. */
    DELETED,

    /** It is its tea's last recipe; nothing changed. */
    LAST_RECIPE,

    /** No recipe with the id; nothing changed. */
    NOT_FOUND,
}

@Dao
interface VesselDao {
    @Query("SELECT * FROM vessel ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VesselEntity>>

    @Query("SELECT * FROM vessel WHERE id = :id")
    suspend fun get(id: String): VesselEntity?

    @Upsert
    suspend fun upsert(vessel: VesselEntity)

    /** *Archivieren* and *Wiederherstellen* (WARE-01, D-100): the number of rows changed (0: no such vessel). */
    @Query("UPDATE vessel SET is_archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long): Int

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
