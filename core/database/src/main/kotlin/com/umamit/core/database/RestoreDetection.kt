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

/**
 * The builder of `umamit.db` as the app opens it (05 §1, §8): with restore detection, so a
 * database restored from a device backup or transfer never resumes the old phone's timer
 * (04 §11 E11). [installMarker] is replaceable for tests only.
 */
fun umamiDatabaseBuilder(
    context: Context,
    name: String = UmamiDatabase.NAME,
    installMarker: File = File(context.noBackupFilesDir, INSTALL_MARKER_FILE),
): RoomDatabase.Builder<UmamiDatabase> = Room.databaseBuilder(context, UmamiDatabase::class.java, name)
    .addCallback(RestoreDetection(installMarker))

/**
 * Restore detection (05 §8, D-052). The install marker lives in no-backup storage, which no
 * backup carries. A database created on this install writes it at once. A database that is
 * opened without it came from another install, i.e. from a backup: its `active_brew` row is
 * cleared, then the marker is written, so this happens exactly once. Room runs [onOpen]
 * before it serves any query, so nothing ever reads the restored row.
 */
internal class RestoreDetection(private val marker: File) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = writeMarker()

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (marker.exists()) return
        db.execSQL("DELETE FROM active_brew")
        writeMarker()
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
    }
}
