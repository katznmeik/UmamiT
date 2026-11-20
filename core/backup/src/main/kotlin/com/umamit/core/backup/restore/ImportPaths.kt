package com.umamit.core.backup.restore

import android.content.Context
import com.umamit.core.database.PHOTOS_DIR
import com.umamit.core.database.UmamiDatabase
import java.io.File

/**
 * The files of the replacing import (05 §8 „Replace", the D-130 build note of #336). In `noBackupFilesDir`, never in a
 * device backup and not visible to other apps: `import/` (the phase [marker], the file's [settings] to apply, the
 * readers' stagings) and `safety/` (the safety copy [safetyNew] while it is made, [safetyCopy] once the import is in).
 * In `filesDir`: [photos] (05 §3.9) with [photosNew] and [photosOld] beside it for the folder swap. [database] is
 * `umamit.db`, whose commit token the start gate reads before Room opens it.
 */
class ImportPaths(noBackupDir: File, filesDir: File, val database: File) {
    val importDir = File(noBackupDir, IMPORT_DIR)
    internal val marker = File(importDir, MARKER_FILE)
    internal val settings = File(importDir, "settings.json")

    /** The staging to pass to `ReadBackup.read` for a picked file (#338); the import moves its photos away. */
    val staging = File(importDir, "staging")
    internal val undoStaging = File(importDir, "undo")
    internal val check = File(importDir, "check")

    internal val safetyDir = File(noBackupDir, "safety")
    internal val safetyNew = File(safetyDir, "umamit-safety.new")
    internal val safetyCopy = File(safetyDir, "umamit-safety.umamit")

    internal val photos = File(filesDir, PHOTOS_DIR)
    internal val photosNew = File(filesDir, "$PHOTOS_DIR.new")
    internal val photosOld = File(filesDir, "$PHOTOS_DIR.old")

    companion object {
        internal const val IMPORT_DIR = "import"
        internal const val MARKER_FILE = "marker.json"

        fun of(context: Context) =
            ImportPaths(context.noBackupFilesDir, context.filesDir, context.getDatabasePath(UmamiDatabase.NAME))
    }
}
