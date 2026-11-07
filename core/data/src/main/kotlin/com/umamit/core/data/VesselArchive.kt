package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * *Archivieren* and *Wiederherstellen* of a vessel (WARE-01, SCR-13, SCR-14; D-100). A vessel is
 * archived, never deleted: it leaves the pickers while recipes and journal sessions keep it. The vessel
 * of the active brew session can't be archived until the session ends.
 */
interface VesselArchive {
    /** The vessel of the active brew session, or null (no session, or a session without a vessel). */
    fun observeActiveVesselId(): Flow<String?>

    /** Archives the vessel, unless it belongs to the active brew session; one transaction. */
    suspend fun archive(vesselId: String): ArchiveResult

    /** *Wiederherstellen*: the vessel is back in the pickers as it was. A vessel that is gone is ignored. */
    suspend fun restore(vesselId: String)
}

/** [VesselArchive] on Room: the check against `active_brew` and the write share one transaction. */
class RoomVesselArchive @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    VesselArchive {
    override fun observeActiveVesselId(): Flow<String?> =
        db.activeBrewDao().observeActiveVesselId().distinctUntilChanged()

    override suspend fun archive(vesselId: String): ArchiveResult = db.withTransaction {
        when {
            db.activeBrewDao().activeVesselId() == vesselId -> ArchiveResult.InActiveSession
            db.vesselDao().setArchived(vesselId, archived = true, now = time.nowMs()) == 0 -> ArchiveResult.NotFound
            else -> ArchiveResult.Archived
        }
    }

    override suspend fun restore(vesselId: String) {
        db.vesselDao().setArchived(vesselId, archived = false, now = time.nowMs())
    }
}
