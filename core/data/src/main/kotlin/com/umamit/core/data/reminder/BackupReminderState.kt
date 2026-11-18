package com.umamit.core.data.reminder

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What the backup reminder (BAK-06, 06 §5.4) reads and writes besides SET-20 itself: the two device-bound markers of
 * 05 §6 and the count of sessions since the last export. A seam for the tests; [DataStoreBackupReminderState] is the
 * app's.
 */
interface BackupReminderState {
    /** When the last export finished (`lastBackupAt`), epoch ms; null = never. */
    suspend fun lastBackupAt(): Long?

    /** The last check or turn-on (`backupReminderLastAt`), epoch ms; null = none yet. */
    suspend fun reminderLastAt(): Long?

    /** Notes a check or the switch's turn-on at [atMs]. */
    suspend fun markReminderLastAt(atMs: Long)

    /** Sessions (`COMPLETED`, `MANUAL`) started after [sinceMs]; all of them for null (never saved). */
    suspend fun sessionsSince(sinceMs: Long?): Int
}

/** [BackupReminderState] on the settings file and the database (05 §6). */
class DataStoreBackupReminderState @Inject constructor(
    private val source: SettingsDataSource,
    private val db: UmamiDatabase,
) : BackupReminderState {
    override suspend fun lastBackupAt(): Long? = source.lastBackupAt.first()

    override suspend fun reminderLastAt(): Long? = source.backupReminderLastAt.first()

    override suspend fun markReminderLastAt(atMs: Long) = source.markBackupReminderLastAt(atMs)

    override suspend fun sessionsSince(sinceMs: Long?): Int = db.restoreDao().sessionsStartedAfter(sinceMs ?: 0L)
}

/** Binds [BackupReminderState]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface BackupReminderStateModule {
    @Binds
    fun backupReminderState(impl: DataStoreBackupReminderState): BackupReminderState
}
