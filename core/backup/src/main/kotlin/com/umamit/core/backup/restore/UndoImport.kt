package com.umamit.core.backup.restore

import android.util.Log
import com.umamit.core.backup.archive.BackupContent
import com.umamit.core.backup.archive.BackupReadResult
import com.umamit.core.backup.archive.ReadBackup
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * *Import rückgängig machen* (BAK-02, 05 §8 „Undo", D-130 Q6, item 21): while the safety copy exists and now <
 * `safetyCopyAt` + 7 × 24 h, the same replace as the import (refused while a timer is active or the catalog
 * refreshes; steps 3–4 with the same crash safety) from the copy, which is then deleted and `safetyCopyAt` cleared. No
 * new safety copy is made.
 */
class UndoImport @Inject constructor(
    private val replacer: ImportReplacer,
    private val readBackup: ReadBackup,
    private val expiry: SafetyCopyExpiry,
    private val db: UmamiDatabase,
) {
    private val paths get() = replacer.paths

    /**
     * The dialog's counts, or null while there is nothing to undo (SCR-26 hides *Import rückgängig machen* then). An
     * unfinished replace is finished first, so the copy and its time are the last import's. If an import's step after
     * its commit still cannot finish, the undo is offered all the same, counted from the import (D-133 Q3), and
     * [undo] refuses it with the replace-failed text while the step keeps failing.
     */
    suspend fun preview(): UndoPreview? = withContext(Dispatchers.IO) {
        replacer.lock.withLock {
            val since = if (replacer.finishPending()) {
                expiry.expireLocked()
                validSince()
            } else {
                pendingImportAt()
            }
            since?.let { at ->
                val counts = db.restoreDao()
                UndoPreview(
                    sessions = counts.sessionsCreatedAfter(at),
                    teas = counts.teasChangedAfter(at),
                    availableUntil = at + SAFETY_COPY_VALID_MS,
                )
            }
        }
    }

    /**
     * The time of an import whose transaction committed (the database holds its token) but whose later steps still
     * wait (its marker), while it can be undone (now < it + 7 × 24 h); else null (D-133 Q3). The caller holds the lock.
     */
    private fun pendingImportAt(): Long? {
        val marker = MarkerFile.read(paths)?.takeIf { it.kind == ImportKind.IMPORT } ?: return null
        val at = marker.importedAt?.takeIf { replacer.now < it + SAFETY_COPY_VALID_MS }
        val committed = try {
            at != null && CommitToken.read(db.openHelper.writableDatabase) == marker.token
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // SQLite: unknown, so no undo is offered
        ) {
            Log.w(TAG, "commit token unreadable", e)
            false
        }
        return at.takeIf { committed }
    }

    suspend fun undo(timer: TimerActivity): RestoreResult = withContext(Dispatchers.IO + NonCancellable) {
        replacer.lock.withLock { undoLocked(timer) }
    }

    private suspend fun undoLocked(timer: TimerActivity): RestoreResult {
        val refusal = replacer.refusal(timer) ?: RestoreRefusal.REPLACE_FAILED.takeUnless { replacer.finishPending() }
        return if (refusal != null) RestoreResult.Refused(refusal) else undoFromCopy(timer)
    }

    private suspend fun undoFromCopy(timer: TimerActivity): RestoreResult {
        expiry.expireLocked()
        if (validSince() == null) return RestoreResult.NoSafetyCopy
        return stagedCopy()?.use { replacer.replace(ImportKind.UNDO, it, timer) }
            ?: RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED)
    }

    /** The safety copy read back into the undo's staging, or null when it cannot be (then nothing changes). */
    private suspend fun stagedCopy(): BackupContent? {
        val read = try {
            readBackup.read(paths.safetyCopy, paths.undoStaging)
        } catch (e: IOException) {
            Log.w(TAG, "the safety copy cannot be staged", e)
            null
        }
        if (read is BackupReadResult.Refused) Log.w(TAG, "the safety copy does not read back: ${read.refusal}")
        return (read as? BackupReadResult.Valid)?.content
    }

    /** `safetyCopyAt` while the copy can be undone (05 §8: it exists and now < it + 7 × 24 h), else null. */
    private suspend fun validSince(): Long? {
        val at = replacer.safetyCopyAt() ?: return null
        return at.takeIf { paths.safetyCopy.exists() && replacer.now < at + SAFETY_COPY_VALID_MS }
    }

    private companion object {
        const val TAG = "UmamiTImport"
    }
}
