package com.umamit.core.timer.controller

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.umamiDatabaseBuilder
import org.json.JSONObject
import java.io.File

/**
 * `umamit.db` as v0.4.1 left it at an update (05 §9, 04 §7.5; #194): a file of the frozen schema 4, built from its
 * exported `4.json`, holding every row the controller wrote to [source] (the timers' `active_brew` rows, their
 * sessions, teas and stock) in the columns schema 4 has. [migrated] opens it as the app does, with the production
 * builder, so `MIGRATION_4_5` runs before the new process reads its first row. The `active_brew` table is the same in
 * both schemas, so its rows are the ones v0.4.1 wrote for the same timers.
 */
class V041Database(private val context: Context, private val name: String = "umamit-v041.db") {

    /** Writes the schema-4 file from [source], replacing an earlier one. */
    fun writeFrom(source: UmamiDatabase) {
        context.deleteDatabase(name)
        val schema = JSONObject(SCHEMA_4.readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            (0 until entities.length()).map(entities::getJSONObject).forEach { entity ->
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
                val indices = entity.optJSONArray("indices")
                (0 until (indices?.length() ?: 0)).forEach { i ->
                    db.execSQL(indices!!.getJSONObject(i).getString("createSql").replace(TABLE_NAME, table))
                }
                copyRows(source, db, table)
            }
            val setup = schema.getJSONArray("setupQueries")
            (0 until setup.length()).forEach { db.execSQL(setup.getString(it)) }
            db.version = SCHEMA_VERSION
        }
    }

    /**
     * The file opened by the app's builder after the update: the install marker is there (an update keeps it,
     * 05 §8), so restore detection leaves the timers alone, and the migrations run to the current schema.
     */
    fun migrated(): UmamiDatabase {
        val marker = File(context.noBackupFilesDir, "v041-marker").apply {
            parentFile?.mkdirs()
            createNewFile()
        }
        return umamiDatabaseBuilder(context, name, marker, File(context.filesDir, "v041-photos"))
            .allowMainThreadQueries()
            .build()
            .also { check(it.openHelper.writableDatabase.version == UmamiDatabase.VERSION) { "not migrated" } }
    }

    /** Every row of [table] in [source], in the columns [target]'s schema-4 table has. */
    private fun copyRows(source: UmamiDatabase, target: SQLiteDatabase, table: String) {
        val columns = target.rawQuery("PRAGMA table_info(`$table`)", null).use { info ->
            generateSequence { if (info.moveToNext()) info.getString(info.getColumnIndexOrThrow("name")) else null }
                .toList()
        }
        source.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { rows ->
            while (rows.moveToNext()) {
                val values = ContentValues()
                columns.forEach { column -> rows.putInto(values, column) }
                target.insertOrThrow(table, null, values)
            }
        }
    }

    private fun Cursor.putInto(values: ContentValues, column: String) {
        val index = getColumnIndexOrThrow(column)
        when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> values.putNull(column)
            Cursor.FIELD_TYPE_INTEGER -> values.put(column, getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> values.put(column, getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> values.put(column, getBlob(index))
            else -> values.put(column, getString(index))
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 4
        const val TABLE_NAME = "\${TABLE_NAME}"

        /** The exported schema 4 of `core:database`; unit tests run in the module's directory. */
        val SCHEMA_4 = File("../database/schemas/com.umamit.core.database.UmamiDatabase/4.json")
    }
}
