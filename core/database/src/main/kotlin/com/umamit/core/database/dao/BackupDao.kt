package com.umamit.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

/**
 * The personal rows a backup holds (05 §8, BAK-01), read at one moment by [BackupDao.snapshot]. Sessions are the
 * `COMPLETED` and `MANUAL` ones only (D-130 item 27); every row that points at another session (its steps,
 * descriptors, photos and stock event) is left out with it, so the snapshot is closed under its references.
 * `active_brew` is never part of it.
 */
data class BackupRows(
    val teas: List<TeaEntity>,
    val brewProfiles: List<BrewProfileEntity>,
    val brewSteps: List<BrewStepEntity>,
    val vessels: List<VesselEntity>,
    val sessions: List<BrewSessionEntity>,
    val sessionSteps: List<SessionStepEntity>,
    val sessionDescriptors: List<SessionDescriptorEntity>,
    val inventoryEvents: List<InventoryEventEntity>,
    val photos: List<PhotoEntity>,
    val catalogBookmarks: List<CatalogBookmarkEntity>,
)

/**
 * The backup's reads of `umamit.db` (05 §8, BAK-01). Every list is ordered by its primary key, so the same data
 * gives the same file. No schema of its own: it reads the tables of 05 §3.
 */
@Dao
abstract class BackupDao {
    /**
     * Every personal row of the backup in **one read transaction** (05 §8): a timer writing meanwhile waits for it or
     * comes before it, so the file never holds half of a write. The transaction runs the SELECTs only.
     */
    @Transaction
    open suspend fun snapshot(): BackupRows = BackupRows(
        teas = teas(),
        brewProfiles = brewProfiles(),
        brewSteps = brewSteps(),
        vessels = vessels(),
        sessions = sessions(),
        sessionSteps = sessionSteps(),
        sessionDescriptors = sessionDescriptors(),
        inventoryEvents = inventoryEvents(),
        photos = photos(),
        catalogBookmarks = catalogBookmarks(),
    )

    @Query("SELECT * FROM tea ORDER BY id")
    protected abstract suspend fun teas(): List<TeaEntity>

    @Query("SELECT * FROM brew_profile ORDER BY id")
    protected abstract suspend fun brewProfiles(): List<BrewProfileEntity>

    @Query("SELECT * FROM brew_step ORDER BY id")
    protected abstract suspend fun brewSteps(): List<BrewStepEntity>

    @Query("SELECT * FROM vessel ORDER BY id")
    protected abstract suspend fun vessels(): List<VesselEntity>

    @Query("SELECT * FROM brew_session WHERE $EXPORTED ORDER BY id")
    protected abstract suspend fun sessions(): List<BrewSessionEntity>

    @Query("SELECT * FROM session_step WHERE session_id IN ($EXPORTED_IDS) ORDER BY id")
    protected abstract suspend fun sessionSteps(): List<SessionStepEntity>

    @Query(
        "SELECT * FROM session_descriptor WHERE session_id IN ($EXPORTED_IDS) ORDER BY session_id, descriptor_key",
    )
    protected abstract suspend fun sessionDescriptors(): List<SessionDescriptorEntity>

    @Query("SELECT * FROM inventory_event WHERE $SESSION_EXPORTED ORDER BY id")
    protected abstract suspend fun inventoryEvents(): List<InventoryEventEntity>

    @Query("SELECT * FROM photo WHERE $SESSION_EXPORTED ORDER BY id")
    protected abstract suspend fun photos(): List<PhotoEntity>

    @Query("SELECT * FROM catalog_bookmark ORDER BY catalog_key")
    protected abstract suspend fun catalogBookmarks(): List<CatalogBookmarkEntity>
}

/** The sessions a backup holds (D-130 item 27): all but `IN_PROGRESS`. */
private const val EXPORTED = "status IN ('COMPLETED', 'MANUAL')"
private const val EXPORTED_IDS = "SELECT id FROM brew_session WHERE $EXPORTED"
private const val SESSION_EXPORTED = "(session_id IS NULL OR session_id IN ($EXPORTED_IDS))"
