package com.umamit.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The catalog database `catalog.db` (05 §4, §9; #64): the committed schema JSON matches the
 * entities, every committed version opens with the production builder (the same guard as
 * `umamit.db`'s, frozen from `v0.2.0-rc.1`), and each staging table has exactly its live table's
 * columns in the same order, which the swap's `INSERT … SELECT *` relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CatalogDatabase::class.java,
    )

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun committedSchemaVersion1MatchesTheEntities() {
        helper.createDatabase(DB_NAME, CatalogDatabase.VERSION).close()

        val db = Room.databaseBuilder(context(), CatalogDatabase::class.java, DB_NAME).allowMainThreadQueries().build()
        try {
            // Opening validates the identity hash and every table against the entities.
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(1)
        } finally {
            db.close()
        }
    }

    @Test
    fun schemaHasTheCatalogTablesOf05() {
        val db = helper.createDatabase(DB_NAME, CatalogDatabase.VERSION)
        val tables = db.names("table")
        db.close()

        assertThat(tables).containsAtLeast(
            "catalog_tea",
            "catalog_tea_staging",
            "catalog_fts",
            "catalog_package",
            "catalog_package_staging",
            "catalog_skip",
            "catalog_guide",
            "catalog_refresh_log",
        )
    }

    @Test
    fun eachStagingTableHasItsLiveTablesColumnsInOrder() {
        val db = helper.createDatabase(DB_NAME, CatalogDatabase.VERSION)
        try {
            assertThat(db.columns("catalog_tea_staging")).containsExactlyElementsIn(db.columns("catalog_tea")).inOrder()
            assertThat(db.columns("catalog_package_staging"))
                .containsExactlyElementsIn(db.columns("catalog_package")).inOrder()
            assertThat(db.columns("catalog_tea")).containsAtLeast("description_text", "photo_file", "first_seen_at")
        } finally {
            db.close()
        }
    }

    @Test
    fun committedSchemasAreEveryVersionUpToTheCurrentOne() {
        assertThat(committedVersions()).containsExactlyElementsIn(1..CatalogDatabase.VERSION).inOrder()
    }

    @Test
    fun everyCommittedVersionOpensWithTheProductionBuilder() {
        for (version in committedVersions()) {
            val name = "catalog-production-v$version.db"
            helper.createDatabase(name, version).close()

            val db = catalogDatabaseBuilder(context(), name).allowMainThreadQueries().build()
            try {
                // Opening runs the migrations and validates every table against the entities.
                assertThat(db.openHelper.writableDatabase.version).isEqualTo(CatalogDatabase.VERSION)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun productionMigrationsMatchTheCurrentExportedSchema() {
        for (version in committedVersions().filter { it < CatalogDatabase.VERSION }) {
            val name = "catalog-migrated-v$version.db"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, CatalogDatabase.VERSION, true, *CATALOG_MIGRATIONS.toTypedArray())
                .close()
        }
    }

    private fun SupportSQLiteDatabase.names(type: String): List<String> =
        query("SELECT name FROM sqlite_master WHERE type = '$type'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildList { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    /** The versions of the committed schema files, as the test assets carry them (`N.json`). */
    private fun committedVersions(): List<Int> =
        InstrumentationRegistry.getInstrumentation().context.assets.list(CatalogDatabase::class.java.name).orEmpty()
            .mapNotNull { SCHEMA_FILE.matchEntire(it)?.groupValues?.get(1)?.toInt() }
            .sorted()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val DB_NAME = "catalog-schema-test.db"
        val SCHEMA_FILE = Regex("""(\d+)\.json""")
    }
}
