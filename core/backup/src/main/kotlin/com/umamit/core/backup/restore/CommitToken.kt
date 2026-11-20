package com.umamit.core.backup.restore

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * How a committed replace is recognised after a crash (the D-130 build note of #336): the replace's one transaction
 * also sets `PRAGMA application_id`, a 32-bit field in the SQLite header (offset 68), to the marker's token. A header
 * write is part of the transaction, so the field has the token exactly when the rows committed. Room uses only
 * `user_version` and `room_master_table`, so umamit.db keeps schema v9, its frozen schema and its identity hash.
 */
internal object CommitToken {
    private const val READ = "PRAGMA application_id"

    /** The token of the next replace: never the current value, never 0 (a database no import ever touched). */
    fun next(current: Int): Int = if (current == Int.MAX_VALUE || current < 0) 1 else current + 1

    fun read(db: SupportSQLiteDatabase): Int = db.query(READ).use { cursor ->
        check(cursor.moveToFirst()) { "no application_id" }
        cursor.getInt(0)
    }

    /** Sets the token; inside the replace's transaction, so it commits or rolls back with the rows. */
    fun set(db: SupportSQLiteDatabase, token: Int) {
        db.execSQL("PRAGMA application_id = $token")
    }

    /**
     * The token in the database [file] on a plain framework connection, before Room opens it (the start gate), so
     * Room's open callbacks (the restore detection) never run on a half-finished replace; null without a database.
     */
    fun readFile(file: File): Int? {
        if (!file.exists()) return null
        // Write-ahead logging as Room keeps it, so this connection does not switch the file's journal mode.
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        return SQLiteDatabase.openDatabase(file.path, null, flags).use { db ->
            db.rawQuery(READ, null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
        }
    }
}
