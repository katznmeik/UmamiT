package com.umamit.core.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.IOException

/** The install marker's file name in no-backup storage (05 §8, D-052). */
const val INSTALL_MARKER_FILE = "umamit-install-marker"

/** The folder of the photo files in app-private `filesDir`, never backed up (05 §3.9, §8). */
const val PHOTOS_DIR = "photos"

/**
 * The builder of `umamit.db` as the app opens it (05 §1, §8): with restore detection, so a
 * database restored from a device backup or transfer never resumes the old phone's timer
 * (04 §11 E11), and with every migration in [UMAMI_MIGRATIONS] (05 §9). [installMarker] and
 * [photosDir] are replaceable for tests only.
 */
fun umamiDatabaseBuilder(
    context: Context,
    name: String = UmamiDatabase.NAME,
    installMarker: File = File(context.noBackupFilesDir, INSTALL_MARKER_FILE),
    photosDir: File = File(context.filesDir, PHOTOS_DIR),
): RoomDatabase.Builder<UmamiDatabase> = Room.databaseBuilder(context, UmamiDatabase::class.java, name)
    .addMigrations(*UMAMI_MIGRATIONS.toTypedArray())
    .addCallback(RestoreDetection(installMarker, photosDir))

/**
 * Restore detection (05 §8, D-052, D-054). The install marker lives in no-backup storage, which
 * no backup carries. A database created on this install writes it at once. A database that is
 * opened without it came from another install, i.e. from a backup: its `active_brew` row is
 * cleared and the `photo` rows whose file in [photosDir] did not come along (`photos/` is never
 * backed up) are deleted and their covers cleared; then the marker is written, so this happens
 * exactly once. Room runs [onOpen] before it serves any query, so nothing ever reads the
 * restored row or a dangling cover. A row whose file exists stays, so a false positive (a
 * marker that could not be written) loses no photo.
 */
internal class RestoreDetection(private val marker: File, private val photosDir: File) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = writeMarker()

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (marker.exists()) return
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM active_brew")
            deleteMissingPhotos(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        writeMarker()
    }

    private fun deleteMissingPhotos(db: SupportSQLiteDatabase) {
        val missing = db.query("SELECT id FROM photo").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    if (!File(photosDir, "$id.jpg").exists()) add(id)
                }
            }
        }
        missing.forEach { db.execSQL("DELETE FROM photo WHERE id = ?", arrayOf(it)) }
        // The foreign keys clear the covers (SET NULL); these statements hold even without them.
        db.execSQL(CLEAR_TEA_COVERS)
        db.execSQL(CLEAR_VESSEL_COVERS)
    }

    /**
     * A marker that cannot be written (disk full) is tried again on the next start; until then
     * that start clears `active_brew` once more. Failing the database open instead would stop
     * the whole app.
     */
    private fun writeMarker() {
        try {
            marker.parentFile?.mkdirs()
            marker.createNewFile()
        } catch (e: IOException) {
            Log.w(TAG, "install marker not written; the next start treats the database as restored", e)
        }
    }

    private companion object {
        const val TAG = "UmamiDatabase"
        const val CLEAR_TEA_COVERS =
            "UPDATE tea SET cover_photo_id = NULL WHERE cover_photo_id NOT IN (SELECT id FROM photo)"
        const val CLEAR_VESSEL_COVERS =
            "UPDATE vessel SET cover_photo_id = NULL WHERE cover_photo_id NOT IN (SELECT id FROM photo)"
    }
}
