package com.umamit.feature.settings.backup

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.backup.archive.BackupReadResult
import com.umamit.core.backup.archive.CreateBackup
import com.umamit.core.backup.archive.ReadBackup
import com.umamit.core.backup.csv.ExportJournalCsv
import com.umamit.core.backup.csv.ResourceJournalCsvLabels
import com.umamit.core.backup.restore.ImportPaths
import com.umamit.core.backup.restore.RestoreBackup
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.backup.restore.TimerActivity
import com.umamit.core.backup.restore.UndoImport
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.reminder.BackupReminderScheduler
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.ui.labelRes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import com.umamit.core.backup.R as BackupR

/**
 * [DataBackup] over `core:backup`'s use cases (06 §2.2): it opens and closes the picked documents' streams and reads
 * the timer's slot states (read only) for the import's [TimerActivity] (D-054).
 */
class UseCaseDataBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val createBackup: CreateBackup,
    private val readBackup: ReadBackup,
    private val restoreBackup: RestoreBackup,
    private val undoImport: UndoImport,
    private val exportJournalCsv: ExportJournalCsv,
    private val paths: ImportPaths,
    private val settings: SettingsRepository,
    private val stored: SettingsDataSource,
    private val db: UmamiDatabase,
    private val timer: BrewSessionController,
    private val reminders: BackupReminderScheduler,
) : DataBackup {
    private val resolver: ContentResolver get() = context.contentResolver

    /** A brew or the quick timer is active in memory, in any slot. */
    private val timerActivity = TimerActivity { timer.slotStates.values.any { it.value !is TimerUiState.Idle } }

    /** „Letzte Sicherung · N neue Sessions" (D-133 Q9): by `created_at`, counted again whenever a session is saved. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val lastBackup: Flow<LastBackup?> = stored.lastBackupAt.distinctUntilChanged().flatMapLatest { at ->
        if (at == null) {
            flowOf(null)
        } else {
            db.restoreDao().observeSessionsCreatedAfter(at).distinctUntilChanged().map { LastBackup(at, it) }
        }
    }

    override val reminderEnabled: Flow<Boolean> = settings.settings.map { it.backupReminderEnabled }

    override val timerActive: Flow<Boolean> =
        combine(timer.slotStates.values) { states -> states.any { it !is TimerUiState.Idle } }.distinctUntilChanged()

    /** SET-20 (BAK-06): on counts the 30 days from now and replaces the job; off cancels it. */
    override suspend fun setReminderEnabled(on: Boolean): Boolean = try {
        val save: suspend () -> Unit = { settings.update { it.copy(backupReminderEnabled = on) } }
        if (on) {
            reminders.turnOn(save)
        } else {
            save()
            reminders.turnOff()
        }
        true
    } catch (e: IOException) {
        Log.w(TAG, "SET-20 not saved", e)
        false
    }

    override suspend fun estimate(): BackupEstimate? = try {
        createBackup.estimate()
    } catch (e: IOException) {
        Log.w(TAG, "backup size not estimated", e)
        null
    } catch (e: android.database.SQLException) {
        Log.w(TAG, "backup size not estimated", e)
        null
    }

    override fun export(document: String): Flow<BackupExport> = flow {
        val output = withContext(Dispatchers.IO) { open(document) }
        if (output == null) {
            emit(BackupExport.Failed(FileNotFoundException(document)))
            return@flow
        }
        try {
            createBackup.export(output).collect {
                // Before `Done` is emitted: a collector that stops there never runs what follows (BAK-06).
                if (it is BackupExport.Done) rescheduleReminder()
                emit(it)
            }
        } finally {
            // A failing close after the file is complete never throws into the app (BAK-01 AC3: a timer may run).
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    output.close()
                } catch (e: IOException) {
                    Log.w(TAG, "backup document not closed", e)
                }
            }
        }
    }

    /** A finished export replaces the reminder's job; a failure there never fails the export (BAK-06). */
    @Suppress("TooGenericExceptionCaught") // WorkManager's store or the settings file: the next app start recovers
    private suspend fun rescheduleReminder() {
        try {
            withContext(NonCancellable) { reminders.exported() }
        } catch (e: Exception) {
            Log.w(TAG, "backup reminder not rescheduled", e)
        }
    }

    override suspend fun delete(document: String) {
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(resolver, document.toUri())
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception, // a provider without delete: the file stays
            ) {
                Log.w(TAG, "incomplete document not deleted", e)
            }
        }
    }

    override suspend fun check(document: String): BackupCheck = withContext(Dispatchers.IO) {
        val read = try {
            resolver.openInputStream(document.toUri())?.use { readBackup.read(it, paths.staging) }
        } catch (e: IOException) {
            Log.w(TAG, "the picked file cannot be read or staged", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "the picked file cannot be opened", e)
            null
        }
        when (read) {
            null -> BackupCheck.Refused(BackupR.string.backup_refused_replace_failed)
            is BackupReadResult.Refused -> BackupCheck.Refused(read.refusal.messageRes)
            is BackupReadResult.Valid -> BackupCheck.Valid(read.content.preview, Staged(read))
        }
    }

    private inner class Staged(private val read: BackupReadResult.Valid) : StagedBackup {
        override suspend fun restore(): RestoreResult = restoreBackup.restore(read.content, timerActivity)

        override fun close() = read.content.close()
    }

    override suspend fun undoPreview(): UndoPreview? = try {
        undoImport.preview()
    } catch (e: IOException) {
        Log.w(TAG, "undo not readable", e)
        null
    } catch (e: android.database.SQLException) {
        Log.w(TAG, "undo not readable", e)
        null
    }

    override suspend fun undo(): RestoreResult = undoImport.undo(timerActivity)

    override suspend fun exportJournal(document: String): Boolean = withContext(Dispatchers.IO) {
        val resources = context.resources
        val descriptors = TasteDescriptor.entries.associate { it.key to resources.getString(it.labelRes()) }
        val labels = ResourceJournalCsvLabels(resources, descriptors::get)
        try {
            open(document)?.use { exportJournalCsv(it, labels) } != null
        } catch (e: IOException) {
            Log.w(TAG, "journal CSV not written", e)
            false
        } catch (e: android.database.SQLException) {
            Log.w(TAG, "journal CSV not read", e)
            false
        }
    }

    /** The picked document for writing, truncated; null when the provider gives none. */
    private fun open(document: String): OutputStream? = try {
        resolver.openOutputStream(document.toUri(), "wt")
    } catch (e: FileNotFoundException) {
        Log.w(TAG, "document not opened", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "document not opened", e)
        null
    }

    private companion object {
        const val TAG = "UmamiTBackup"
    }
}

/** The app's [DataBackup]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataBackupModule {
    @Binds
    fun dataBackup(impl: UseCaseDataBackup): DataBackup
}
