package com.umamit.core.data

import android.util.Log
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** How [TeaArchive.archive] or [VesselArchive.archive] ended. */
sealed interface ArchiveResult {
    /** Archived: gone from Start and the pickers, kept by recipes and the journal. */
    data object Archived : ArchiveResult

    /** It belongs to the active brew session; nothing changed („Erst die Sitzung beenden", E15). */
    data object InActiveSession : ArchiveResult

    /** No row with the id (any more); nothing changed. */
    data object NotFound : ArchiveResult
}

/** How [TeaArchive.delete] ended. */
sealed interface DeleteResult {
    /** The tea is deleted; the journal keeps its sessions by name. */
    data object Deleted : DeleteResult

    /** The tea belongs to the active brew session; nothing changed („Erst die Sitzung beenden", E15). */
    data object InActiveSession : DeleteResult

    /** No tea with the id (any more); nothing changed. */
    data object NotFound : DeleteResult
}

/**
 * *Archivieren*, *Wiederherstellen* and *Löschen* of a tea (TEA-04, SCR-03, SCR-09; D-093 point 8, D-100).
 *
 * An archived tea keeps its recipes, stock events, photos and journal sessions. It leaves Start and the
 * tea picker ([TeaRepository.observeOwnedTeas]) and the catalog's „im Besitz"; adding it again from the
 * catalog brings back the same tea (D-085). A deleted tea is gone with its recipes, stock events and
 * photos; its sessions stay in the journal with their name snapshot. The tea of the active brew session
 * can be neither archived nor deleted (E15).
 */
interface TeaArchive {
    /**
     * The tea of the active brew session, or null (no session, the quick timer, or a session
     * without a tea): that tea can't be archived or deleted (E15).
     */
    fun observeActiveTeaId(): Flow<String?>

    /** Archives the tea, unless it belongs to the active brew session; one transaction. */
    suspend fun archive(teaId: String): ArchiveResult

    /**
     * *Wiederherstellen*, and *Rückgängig* after [archive]: the tea is back in Start and the pickers as it
     * was. A tea that is gone is ignored.
     */
    suspend fun restore(teaId: String)

    /**
     * *Löschen* (D-100), unless the tea belongs to the active brew session. In one transaction the tea goes
     * and with it, by cascade, its recipes, steps, stock events and photo rows; its sessions keep their
     * snapshot without the tea (05 §3.1). The tea's photo files are deleted after the commit; a file that
     * can't be deleted is logged and never undoes the delete.
     */
    suspend fun delete(teaId: String): DeleteResult
}

/** [TeaArchive] on Room: the check against `active_brew` and the write share one transaction. */
class RoomTeaArchive @Inject constructor(
    private val db: UmamiDatabase,
    private val time: WallClock,
    private val photoFiles: PhotoFiles,
) : TeaArchive {
    override fun observeActiveTeaId(): Flow<String?> = db.activeBrewDao().observeActiveTeaId().distinctUntilChanged()

    override suspend fun archive(teaId: String): ArchiveResult = db.withTransaction {
        when {
            db.activeBrewDao().activeTeaId() == teaId -> ArchiveResult.InActiveSession
            db.teaDao().setArchived(teaId, archived = true, now = time.nowMs()) == 0 -> ArchiveResult.NotFound
            else -> ArchiveResult.Archived
        }
    }

    override suspend fun restore(teaId: String) {
        db.teaDao().setArchived(teaId, archived = false, now = time.nowMs())
    }

    override suspend fun delete(teaId: String): DeleteResult {
        var photoIds = emptyList<String>()
        val result = db.withTransaction {
            when {
                db.teaDao().get(teaId) == null -> DeleteResult.NotFound

                db.activeBrewDao().activeTeaId() == teaId -> DeleteResult.InActiveSession

                else -> {
                    photoIds = db.teaDao().photoIds(teaId)
                    db.teaDao().delete(teaId)
                    DeleteResult.Deleted
                }
            }
        }
        // Only after the commit: the files of a delete that was rolled back must stay.
        photoIds.forEach { id ->
            val file = photoFiles.file(id)
            if (!file.delete() && file.exists()) Log.w(TAG, "Photo file of a deleted tea not removed: ${file.name}")
        }
        return result
    }

    private companion object {
        const val TAG = "TeaArchive"
    }
}

/** Binds [TeaArchive] (TEA-04) and [VesselArchive] (WARE-01). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TeaArchiveModule {
    @Binds
    abstract fun teaArchive(impl: RoomTeaArchive): TeaArchive

    @Binds
    abstract fun vesselArchive(impl: RoomVesselArchive): VesselArchive
}
