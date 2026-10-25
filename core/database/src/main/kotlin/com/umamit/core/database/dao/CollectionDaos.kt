package com.umamit.core.database.dao

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
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
interface TeaDao {
    @Transaction
    @Query("SELECT * FROM tea ORDER BY name")
    fun observeAll(): Flow<List<TeaWithDetails>>

    @Transaction
    @Query("SELECT * FROM tea WHERE id = :id")
    fun observe(id: String): Flow<TeaWithDetails?>

    @Query("SELECT * FROM tea WHERE id = :id")
    suspend fun get(id: String): TeaEntity?

    /** The catalog keys the collection's teas link to („im Besitz", CAT-10; 05 §1.6 soft link). */
    @Query("SELECT DISTINCT catalog_key FROM tea WHERE catalog_key IS NOT NULL")
    fun observeCatalogKeys(): Flow<List<String>>

    @Upsert
    suspend fun upsert(tea: TeaEntity)

    /** Whether the database holds any tea, vessel or session, e.g. after a device restore (M1-07). */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM tea) OR EXISTS(SELECT 1 FROM vessel) OR EXISTS(SELECT 1 FROM brew_session)",
    )
    suspend fun hasPersonalData(): Boolean

    @Query("DELETE FROM tea WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tea SET cover_photo_id = :photoId, updated_at = :now WHERE id = :teaId")
    suspend fun setCoverPhoto(teaId: String, photoId: String?, now: Long)

    /** *Geöffnet am* (INV-06). */
    @Query("UPDATE tea SET opened_date = :openedDate, updated_at = :now WHERE id = :teaId")
    suspend fun setOpenedDate(teaId: String, openedDate: LocalDate, now: Long)

    @Upsert
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?
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

    @Query("UPDATE brew_profile SET pre_alert_seconds = :preAlertSeconds, updated_at = :now WHERE id = :id")
    protected abstract suspend fun setPreAlert(id: String, preAlertSeconds: Int?, now: Long)

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

    /** Replaces exactly the recipe's steps and pre-alert (*Im Rezept speichern*, BRW-02). */
    @Transaction
    open suspend fun replaceStepsAndPreAlert(
        profileId: String,
        steps: List<BrewStepEntity>,
        preAlertSeconds: Int?,
        now: Long,
    ) {
        setPreAlert(profileId, preAlertSeconds, now)
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

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_event WHERE tea_id = :teaId ORDER BY occurred_at, created_at")
    fun observeForTea(teaId: String): Flow<List<InventoryEventEntity>>

    @Query("SELECT * FROM inventory_event WHERE tea_id = :teaId ORDER BY occurred_at, created_at")
    suspend fun eventsForTea(teaId: String): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_event WHERE id = :id")
    suspend fun get(id: String): InventoryEventEntity?

    @Upsert
    suspend fun upsert(event: InventoryEventEntity)

    @Query("DELETE FROM inventory_event WHERE id = :id")
    suspend fun delete(id: String)
}
