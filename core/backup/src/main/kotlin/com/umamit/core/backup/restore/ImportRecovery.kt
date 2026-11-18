package com.umamit.core.backup.restore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

/**
 * The start gate of the replace's crash safety (05 §8, the D-130 build note of #336). `UmamitApplication.onCreate`
 * calls [recoverAtStart] right after `super.onCreate()`, before the timer recovery, the seed retry and the reminder
 * start, so nothing reads the database, the photos or the settings before an interrupted import is complete one way or
 * the other: forward when the database holds the marker's commit token, else back.
 *
 * The token is read on a plain connection **before Room opens** `umamit.db`, so Room's restore detection can never
 * sweep imported photo rows whose files are still in `photos.new/`. The gate only renames and writes the settings once;
 * deleting `photos.old/` and the stagings is left to [SafetyCopyExpiry.atAppStart] in the background. It blocks the
 * main thread only while a marker exists, which only a crash or a failure after an import's commit leaves.
 */
class ImportRecovery @Inject constructor(private val replacer: ImportReplacer) {
    /** Finishes an interrupted replace, blocking; false when it could not (the next try or start does it). */
    fun recoverAtStart(): Boolean = runBlocking(Dispatchers.IO) {
        replacer.mutex.withLock {
            replacer.engine.finishPending(
                token = { CommitToken.readFile(replacer.paths.database) },
                cleanUp = false,
            )
        }
    }

    companion object {
        /** The gate's fast path, one file check on the main thread: no graph, no database. */
        fun isNeeded(context: Context): Boolean =
            File(File(context.noBackupFilesDir, ImportPaths.IMPORT_DIR), ImportPaths.MARKER_FILE).exists()
    }
}
