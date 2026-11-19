package com.umamit.core.backup.restore

import android.util.Log
import com.umamit.core.backup.archive.BackupContent
import com.umamit.core.backup.archive.BackupFormat
import com.umamit.core.common.WallClock
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.StoredSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException

/** The points of a replace where a test stops it, as a failure or a crash would (the D-130 build note of #336). */
internal enum class ImportPoint {
    SAFETY_COPY_WRITTEN,
    PHOTOS_STAGED,
    PREPARED,
    IN_TRANSACTION,
    COMMITTED,
    PHOTOS_MOVED_OUT,
    PHOTOS_SWAPPED,
    SETTINGS_APPLIED,
    SETTINGS_WRITTEN,
    SAFETY_COPY_PROMOTED,
}

/** Called at each [ImportPoint]; a no-op in the app, a failure or a simulated crash in the tests. */
internal fun interface ImportCheckpoints {
    fun reached(point: ImportPoint)
}

/**
 * The file steps of a replace (05 §8 „Replace" steps 3–5, the D-130 build note of #336): everything but the Room
 * transaction, so the start gate runs them before Room opens. Every step is idempotent and none writes a database row.
 *
 * - [prepare]: refused unless the staged folder holds exactly one file per photo row of the file; the staged photos
 *   to `photos.new/` and the file's settings to `import/settings.json`, both fsynced, then the marker `PREPARED` with
 *   the next commit token and the import's time (`importedAt`, D-133 Q3).
 * - [rollForward] (the transaction committed): the folder swap by its state; once the commit is durable (the marker's
 *   `durable`) `photos.old/` is freed before the settings write (D-133 item 13); one settings write (device-bound keys
 *   kept), `SETTINGS_WRITTEN`, then step 5 (import: `.new` over `.umamit`, `safetyCopyAt` = the import's time; undo:
 *   the copy deleted, `safetyCopyAt` cleared), then the marker deleted.
 * - [rollBack] (it did not): a started swap reversed; `photos.new/`, the files in `import/` and (import only) `.new`
 *   deleted, then the marker.
 *
 * Freeing `photos.old/` first moves back every file whose id has a row in the database ([photoRows]) and is missing
 * from `photos/`. The picked file's staging (`import/staging/`) is never deleted here: a check may be staging the next
 * import while a marker is finished (finding 1 of the T4 review); only [cleanLeftovers] at app start deletes it.
 */
internal class ImportEngine(
    private val paths: ImportPaths,
    private val settings: SettingsDataSource,
    private val clock: WallClock,
    private val photoRows: PhotoRows,
    private val checkpoints: () -> ImportCheckpoints,
) {
    private fun reached(point: ImportPoint) = checkpoints().reached(point)

    /** Before the commit; on a failure the caller runs [rollBack]. [currentToken] is the database's token now. */
    fun prepare(kind: ImportKind, content: BackupContent, currentToken: Int): ImportMarker {
        val staged = content.photosDir
        val expected = content.data.photos.mapTo(HashSet()) { "${it.id}${BackupFormat.PHOTO_SUFFIX}" }
        val found = staged.listFiles().orEmpty().filter { it.isFile }.mapTo(HashSet()) { it.name }
        if (found != expected) throw IOException("The staged photos do not match the file's photo rows")
        // With no marker, a photos.old/ is a leftover of an earlier swap; a marker's photos.old/ is always this one's.
        freeOldPhotos(strict = true)
        Durable.deleteTree(paths.photosNew)
        if (!staged.isDirectory && !staged.mkdirs()) throw IOException("Cannot create the staged photos")
        staged.listFiles().orEmpty().forEach(Durable::sync)
        Durable.syncDir(staged)
        Durable.moveDir(staged, paths.photosNew)
        reached(ImportPoint.PHOTOS_STAGED)
        paths.importDir.mkdirs()
        val settingsBytes = BackupFormat.json.encodeToString(JsonObject.serializer(), content.settings)
        Durable.writeAtomically(paths.settings, settingsBytes.encodeToByteArray())
        val marker = ImportMarker(
            kind = kind,
            token = CommitToken.next(currentToken),
            phase = ImportPhase.PREPARED,
            importedAt = clock.nowMs(),
        )
        MarkerFile.write(paths, marker)
        reached(ImportPoint.PREPARED)
        return marker
    }

    /**
     * Finishes the marker found, if any: forward when [token] (the database's) is the marker's, else back. False when
     * that failed: the marker stays for the next try. [cleanUp] deletes `photos.old/` and the stagings at the end (the
     * start gate leaves that to the background).
     */
    suspend fun finishPending(token: () -> Int?, cleanUp: Boolean = true): Boolean {
        if (!paths.marker.exists()) return true
        return try {
            val marker = MarkerFile.read(paths)
            if (marker != null && token() == marker.token) rollForward(marker, cleanUp) else rollBack(marker?.kind)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // IO, SQLite, DataStore: the marker stays either way
        ) {
            Log.w(TAG, "import not finished; the next try finishes it", e)
            false
        }
    }

    /** Steps 3's swap, 4 and 5 after the commit; throws if a step fails (the marker stays, every step can repeat). */
    suspend fun rollForward(marker: ImportMarker, cleanUp: Boolean = true) {
        if (marker.phase == ImportPhase.PREPARED) {
            swapPhotos()
            // A durable commit cannot be lost any more, so the old photos are not needed for a roll back: they go
            // before the settings write, which a full disk would otherwise keep failing (D-133 item 13).
            if (marker.durable) freeOldPhotos(strict = false)
            applySettings()
            reached(ImportPoint.SETTINGS_APPLIED)
            MarkerFile.write(paths, marker.copy(phase = ImportPhase.SETTINGS_WRITTEN))
            reached(ImportPoint.SETTINGS_WRITTEN)
        }
        when (marker.kind) {
            ImportKind.IMPORT -> {
                // `.new` is never deleted after PREPARED but by this rename, so without it the rename is done.
                if (paths.safetyNew.exists()) {
                    Durable.rename(paths.safetyNew, paths.safetyCopy)
                    Durable.syncDir(paths.safetyDir)
                }
                reached(ImportPoint.SAFETY_COPY_PROMOTED)
                // The 7 × 24 h and the undo's count start at the import, also when this step ran later (D-133 Q3).
                settings.setSafetyCopyAt(marker.importedAt ?: clock.nowMs())
            }

            ImportKind.UNDO -> {
                if (paths.safetyCopy.exists() && !paths.safetyCopy.delete()) throw IOException("Cannot delete the copy")
                settings.setSafetyCopyAt(null)
            }
        }
        MarkerFile.delete(paths)
        if (cleanUp) cleanLeftovers()
    }

    /**
     * Back to the data before (05 §8: staging and `.new` deleted). The database never committed, so its old rows are
     * there; a swap already started (only after a commit that a power loss then undid) is reversed, so the photos
     * match those rows. [kind] null: an unreadable marker; `.new` goes then too (an undo never has one).
     */
    fun rollBack(kind: ImportKind?) {
        // Without a marker no swap has started (it starts after the marker): a photos.old/ is an earlier leftover.
        if (paths.marker.exists() && paths.photosOld.exists()) {
            if (paths.photos.exists()) {
                // photos/ holds the imported set here: put it back where it came from, then delete it below.
                Durable.deleteTree(paths.photosNew)
                Durable.rename(paths.photos, paths.photosNew)
            }
            Durable.rename(paths.photosOld, paths.photos)
            Durable.syncDir(paths.photos.parentFile!!)
        }
        Durable.deleteTree(paths.photosNew)
        if (kind != ImportKind.UNDO) Durable.deleteTree(paths.safetyNew)
        deleteStagings(picked = false)
        MarkerFile.delete(paths)
    }

    /**
     * With no marker: what a crash left of a replace that did not get to its marker or finished it (`photos.new/`,
     * `photos.old/` but the files that have a row, `.new`, the stagings; the picked file's staging only with
     * [pickedStaging], at app start). With a marker nothing is touched.
     */
    fun cleanLeftovers(pickedStaging: Boolean = false) {
        if (paths.marker.exists()) return
        if (paths.photosOld.exists() && !paths.photos.exists()) {
            Durable.rename(paths.photosOld, paths.photos) // cannot happen by the steps; never lose the photos
        }
        freeOldPhotos(strict = false)
        Durable.deleteTree(paths.photosNew)
        Durable.deleteTree(paths.safetyNew)
        deleteStagings(picked = pickedStaging)
    }

    private fun deleteStagings(picked: Boolean) {
        Durable.deleteTree(paths.settings)
        if (picked) Durable.deleteTree(paths.staging)
        Durable.deleteTree(paths.undoStaging)
        Durable.deleteTree(paths.check)
    }

    /**
     * `photos.old/` deleted, after every file in it whose id has a `photo` row and is missing from `photos/` was moved
     * back. When the rows cannot be read it stays ([strict]: the step fails instead), so no such file is lost.
     */
    private fun freeOldPhotos(strict: Boolean) {
        if (!paths.photosOld.exists()) return
        val rows = try {
            photoRows.ids()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // SQLite: photos.old/ waits for the next try
        ) {
            if (strict) throw IOException("The photo rows cannot be read", e)
            Log.w(TAG, "photos.old kept; the photo rows cannot be read", e)
            return
        }
        val kept = paths.photosOld.listFiles().orEmpty().filter { file ->
            file.isFile &&
                file.name.endsWith(BackupFormat.PHOTO_SUFFIX) &&
                file.name.removeSuffix(BackupFormat.PHOTO_SUFFIX) in rows &&
                !File(paths.photos, file.name).exists()
        }
        if (kept.isNotEmpty()) {
            if (!paths.photos.isDirectory && !paths.photos.mkdirs()) throw IOException("Cannot create the photos")
            kept.forEach { Durable.rename(it, File(paths.photos, it.name)) }
            Durable.syncDir(paths.photos)
        }
        Durable.deleteTree(paths.photosOld)
    }

    /**
     * `photos/` → `photos.old/`, `photos.new/` → `photos/`, by the folders' state: a repeat goes on where it was. A
     * `photos/` that a photo writer made again after the move-out (a marker that waited) joins `photos.old/` first.
     */
    private fun swapPhotos() {
        if (paths.photosNew.exists()) {
            if (paths.photos.exists()) {
                if (paths.photosOld.exists()) {
                    paths.photos.listFiles().orEmpty().forEach { Durable.rename(it, File(paths.photosOld, it.name)) }
                    Durable.syncDir(paths.photosOld)
                    Durable.deleteTree(paths.photos)
                } else {
                    Durable.rename(paths.photos, paths.photosOld)
                }
                reached(ImportPoint.PHOTOS_MOVED_OUT)
            }
            Durable.rename(paths.photosNew, paths.photos)
            Durable.syncDir(paths.photos.parentFile!!)
        }
        reached(ImportPoint.PHOTOS_SWAPPED)
    }

    /** Step 4: the file's keys over the stored settings in one DataStore write; the same values again on a repeat. */
    private suspend fun applySettings() {
        val imported = BackupFormat.json.parseToJsonElement(paths.settings.readText()).jsonObject
        settings.updateStored { overlay(it, imported) }
    }

    private companion object {
        const val TAG = "UmamiTImport"
    }
}

/** [stored] with [imported]'s keys, except the device-bound ones of 05 §8, which stay as they are (D-130 item 22). */
internal fun overlay(stored: StoredSettings, imported: JsonObject): StoredSettings {
    val json = BackupFormat.json
    val current = json.encodeToJsonElement(StoredSettings.serializer(), stored).jsonObject
    val replaced = imported.filterKeys { it !in BackupFormat.DEVICE_BOUND_SETTINGS_KEYS }
    return json.decodeFromJsonElement(StoredSettings.serializer(), JsonObject(current + replaced))
}
