package com.umamit.core.backup.restore

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * The ids of the `photo` rows in `umamit.db` now (05 §3.9). The folder swap keeps every file of `photos.old/` whose id
 * has a row in the database it ends with (D-133 item 13): a photo written into the old `photos/` while the replace ran
 * or while its marker waited, whose row went into the new database, is not lost.
 */
internal fun interface PhotoRows {
    /** The ids; throws when they cannot be read (then `photos.old/` stays for the next try). */
    fun ids(): Set<String>

    companion object {
        private const val QUERY = "SELECT id FROM photo"

        /** On the open Room database (the live import and its undo). */
        fun of(db: () -> SupportSQLiteDatabase) = PhotoRows {
            db().query(QUERY).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        }

        /**
         * On a plain framework connection to the database [file], as [CommitToken.readFile] reads the token: the start
         * gate before Room opens, and the start-up housekeeping, which builds no Room graph. None without a database.
         */
        fun ofFile(file: File) = PhotoRows {
            if (!file.exists()) return@PhotoRows emptySet()
            // Write-ahead logging as Room keeps it, so this connection does not switch the file's journal mode.
            val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            SQLiteDatabase.openDatabase(file.path, null, flags).use { db ->
                db.rawQuery(QUERY, null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            }
        }
    }
}
