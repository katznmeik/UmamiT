package com.umamit.feature.settings.backup

import androidx.annotation.StringRes
import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.backup.archive.BackupPreview
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.backup.restore.UndoPreview
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/** „Letzte Sicherung: 3. Nov. 2026 · 12 neue Sessions" (BAK-01 AC4, D-130 Q12). */
data class LastBackup(val atMs: Long, val newSessions: Int)

/**
 * What *Daten & Sicherung* (SCR-26) does, over `core:backup`'s use cases (BAK-01, BAK-02, BAK-04, SET-20). A
 * [document] is the picker's `content://` URI as text; the implementation opens and closes its streams, so the screen
 * and its view model hold no file logic.
 */
interface DataBackup {
    /** The last finished export, null before the first. */
    val lastBackup: Flow<LastBackup?>

    /** SET-20 *An Sicherung erinnern*. */
    val reminderEnabled: Flow<Boolean>

    /** A brew or the quick timer is active: the import and its undo are greyed out (BAK-02 AC4). */
    val timerActive: Flow<Boolean>

    /** Stores SET-20; false when the write failed. */
    suspend fun setReminderEnabled(on: Boolean): Boolean

    /** The size note before the picker (BAK-01 AC1); null when the data cannot be read. */
    suspend fun estimate(): BackupEstimate?

    /** The export to [document] (BAK-01 AC2); cancelling the collection stops it. */
    fun export(document: String): Flow<BackupExport>

    /** Deletes a document a cancelled or failed export left incomplete, where the provider allows it. */
    suspend fun delete(document: String)

    /** Copies [document] to the staging and checks all of it (BAK-02 AC2). */
    suspend fun check(document: String): BackupCheck

    /** The undo dialog's counts, or null while nothing can be undone (BAK-02 AC7). */
    suspend fun undoPreview(): UndoPreview?

    /** *Import rückgängig machen* (BAK-02 AC7), with the timer's own state. */
    suspend fun undo(): RestoreResult

    /** *Journal als CSV* to [document] (BAK-04); false when the read or the write failed. */
    suspend fun exportJournal(document: String): Boolean
}

/** How [DataBackup.check] ends. */
sealed interface BackupCheck {
    /** Every check passed: [staged] waits for *Ersetzen*, and is closed whatever comes next. */
    class Valid(val preview: BackupPreview, val staged: StagedBackup) : BackupCheck

    /** Refused; [messageRes] is its text (03 §5), and nothing changed. */
    data class Refused(@StringRes val messageRes: Int) : BackupCheck
}

/** A checked backup in the staging (05 §8): [close] deletes the staging. */
interface StagedBackup : Closeable {
    /** *Ersetzen* (BAK-02 AC5, AC6): the safety copy, then the one replace; refused while a timer is active. */
    suspend fun restore(): RestoreResult
}
