package com.umamit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.CatalogBookmarkEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.database.entity.VesselEntity
import kotlinx.coroutines.flow.Flow

/**
 * The replacing import's writes and the undo's counts (05 §8 „Replace", „Undo"; BAK-02). No schema of its own: it
 * writes the tables of 05 §3. [replaceAll] runs only inside the caller's one transaction, with the foreign keys
 * deferred to its commit (`tea` and `photo` point at each other).
 */
@Dao
@Suppress("TooManyFunctions") // one delete and one insert per table
abstract class RestoreDao {
    /**
     * Empties every personal table of 05 §3 and fills it with [rows]. `active_brew` is emptied too; the caller runs
     * this only while it has no row (05 §8 step 1), so no timer loses its row. Call inside a transaction.
     */
    open suspend fun replaceAll(rows: BackupRows) {
        deleteActiveBrew()
        deleteSessionDescriptors()
        deleteSessionSteps()
        deleteInventoryEvents()
        deletePhotos()
        deleteSessions()
        deleteBrewSteps()
        deleteBrewProfiles()
        deleteTeas()
        deleteVessels()
        deleteCatalogBookmarks()
        insertVessels(rows.vessels)
        insertTeas(rows.teas)
        insertBrewProfiles(rows.brewProfiles)
        insertBrewSteps(rows.brewSteps)
        insertSessions(rows.sessions)
        insertSessionSteps(rows.sessionSteps)
        insertSessionDescriptors(rows.sessionDescriptors)
        insertInventoryEvents(rows.inventoryEvents)
        insertPhotos(rows.photos)
        insertCatalogBookmarks(rows.catalogBookmarks)
    }

    /**
     * „N neue Sessions" of SCR-26, the undo and the backup reminder's ≥ 20 (D-130 item 21, D-133 Q9): completed and
     * manual sessions saved (`created_at`) after [sinceMs], whatever date they carry, so a backdated manual entry and a
     * session that was running during the export count too.
     */
    @Query(SESSIONS_CREATED_AFTER)
    abstract suspend fun sessionsCreatedAfter(sinceMs: Long): Int

    /** [sessionsCreatedAfter], again whenever a session is saved or changed (SCR-26 stays current while it is open). */
    @Query(SESSIONS_CREATED_AFTER)
    abstract fun observeSessionsCreatedAfter(sinceMs: Long): Flow<Int>

    /** The undo's „N geänderte Tees" (D-130 item 21): teas created or changed after [sinceMs]. */
    @Query("SELECT COUNT(*) FROM tea WHERE created_at > :sinceMs OR updated_at > :sinceMs")
    abstract suspend fun teasChangedAfter(sinceMs: Long): Int

    @Query("DELETE FROM active_brew")
    protected abstract suspend fun deleteActiveBrew()

    @Query("DELETE FROM session_descriptor")
    protected abstract suspend fun deleteSessionDescriptors()

    @Query("DELETE FROM session_step")
    protected abstract suspend fun deleteSessionSteps()

    @Query("DELETE FROM inventory_event")
    protected abstract suspend fun deleteInventoryEvents()

    @Query("DELETE FROM photo")
    protected abstract suspend fun deletePhotos()

    @Query("DELETE FROM brew_session")
    protected abstract suspend fun deleteSessions()

    @Query("DELETE FROM brew_step")
    protected abstract suspend fun deleteBrewSteps()

    @Query("DELETE FROM brew_profile")
    protected abstract suspend fun deleteBrewProfiles()

    @Query("DELETE FROM tea")
    protected abstract suspend fun deleteTeas()

    @Query("DELETE FROM vessel")
    protected abstract suspend fun deleteVessels()

    @Query("DELETE FROM catalog_bookmark")
    protected abstract suspend fun deleteCatalogBookmarks()

    @Insert
    protected abstract suspend fun insertTeas(rows: List<TeaEntity>)

    @Insert
    protected abstract suspend fun insertBrewProfiles(rows: List<BrewProfileEntity>)

    @Insert
    protected abstract suspend fun insertBrewSteps(rows: List<BrewStepEntity>)

    @Insert
    protected abstract suspend fun insertVessels(rows: List<VesselEntity>)

    @Insert
    protected abstract suspend fun insertSessions(rows: List<BrewSessionEntity>)

    @Insert
    protected abstract suspend fun insertSessionSteps(rows: List<SessionStepEntity>)

    @Insert
    protected abstract suspend fun insertSessionDescriptors(rows: List<SessionDescriptorEntity>)

    @Insert
    protected abstract suspend fun insertInventoryEvents(rows: List<InventoryEventEntity>)

    @Insert
    protected abstract suspend fun insertPhotos(rows: List<PhotoEntity>)

    @Insert
    protected abstract suspend fun insertCatalogBookmarks(rows: List<CatalogBookmarkEntity>)
}

private const val SESSIONS_CREATED_AFTER =
    "SELECT COUNT(*) FROM brew_session WHERE status IN ('COMPLETED', 'MANUAL') AND created_at > :sinceMs"
