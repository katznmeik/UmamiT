package com.umamit.core.backup.restore

import android.content.Context
import com.umamit.core.common.WallClock
import com.umamit.core.database.ImportLock
import com.umamit.core.datastore.SettingsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

/**
 * The start gate of the replace's crash safety (05 §8, the D-130 build note of #336). `UmamitApplication.onCreate`
 * calls [recoverAtStart] right after `super.onCreate()`, before the timer recovery, the seed retry and the reminder
 * start, so nothing reads the database, the photos or the settings before an interrupted import is complete one way or
 * the other: forward when the database holds the marker's commit token, else back. `UmamitApplication` guards the call:
 * a throw is logged and the start goes on, as for false (D-133 item 13).
 *
 * The token is read on a plain connection **before Room opens** `umamit.db`, so Room's restore detection can never
 * sweep imported photo rows whose files are still in `photos.new/`. It depends on the lock, the files, the settings and
 * the clock only (no Room, no catalog graph). Nothing else runs yet in the app, but it still holds the one
 * [ImportLock], like every other path that renames or deletes the import's trees (the import, the undo,
 * [SafetyCopyExpiry.atAppStart]), so none of them walks a tree another one is deleting (#349). The gate only renames
 * and writes the settings once (and frees `photos.old/` of a commit marked durable); deleting the stagings is left to
 * [SafetyCopyExpiry.atAppStart] in the background. It blocks the main thread only while a marker exists, which only a
 * crash or a failure after an import's commit leaves.
 */
class ImportRecovery @Inject constructor(
    private val lock: ImportLock,
    private val paths: ImportPaths,
    settings: SettingsDataSource,
    clock: WallClock,
) {
    private val engine = ImportEngine(paths, settings, clock, PhotoRows.ofFile(paths.database)) { ImportCheckpoints {} }

    /** Finishes an interrupted replace, blocking; false when it could not (the next try or start does it). */
    fun recoverAtStart(): Boolean = runBlocking(Dispatchers.IO) {
        lock.withLock { engine.finishPending(token = { CommitToken.readFile(paths.database) }, cleanUp = false) }
    }

    companion object {
        /** The gate's fast path, one file check on the main thread: no graph, no database. */
        fun isNeeded(context: Context): Boolean =
            File(File(context.noBackupFilesDir, ImportPaths.IMPORT_DIR), ImportPaths.MARKER_FILE).exists()
    }
}
