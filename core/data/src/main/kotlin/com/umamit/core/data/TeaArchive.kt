package com.umamit.core.data

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

/** How [TeaArchive.archive] ended. */
sealed interface ArchiveResult {
    /** The tea is archived: gone from Start and the tea picker, kept in the journal. */
    data object Archived : ArchiveResult

    /** The tea belongs to the active brew session; nothing changed („Erst die Sitzung beenden", E15). */
    data object InActiveSession : ArchiveResult

    /** No tea with the id (any more); nothing changed. */
    data object NotFound : ArchiveResult
}

/**
 * *Archivieren* from the tea picker and its *Rückgängig* (TEA-04, SCR-03, D-093 point 8).
 *
 * A removed tea is archived, never deleted: its recipes, stock events, photos and journal sessions
 * stay. It leaves Start and the tea picker ([TeaRepository.observeOwnedTeas]) and the catalog's
 * „im Besitz"; adding it again from the catalog brings back the same tea (D-085).
 */
interface TeaArchive {
    /**
     * The tea of the active brew session, or null (no session, the quick timer, or a session
     * without a tea): that tea can't be removed (E15).
     */
    fun observeActiveTeaId(): Flow<String?>

    /** Archives the tea, unless it belongs to the active brew session; one transaction. */
    suspend fun archive(teaId: String): ArchiveResult

    /** *Rückgängig*: the tea is back as it was before [archive]. A tea that is gone is ignored. */
    suspend fun restore(teaId: String)
}

/** [TeaArchive] on Room: the check against `active_brew` and the write share one transaction. */
class RoomTeaArchive @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) : TeaArchive {
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
}

/** Binds [TeaArchive] (TEA-04). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TeaArchiveModule {
    @Binds
    abstract fun teaArchive(impl: RoomTeaArchive): TeaArchive
}
