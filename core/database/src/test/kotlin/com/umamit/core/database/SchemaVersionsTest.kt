package com.umamit.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
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
 * Every committed schema version opens with the production builder (05 §9, #54). A database
 * created from each `schemas/…/N.json` is opened with [umamiDatabaseBuilder], exactly as the
 * app opens `umamit.db` after an update: Room then needs a migration path from N to
 * [UmamiDatabase.VERSION] in [UMAMI_MIGRATIONS] and validates the result against the entities.
 * The migrations are also validated against the exported JSON of the current version.
 *
 * At version 1 this only reopens version 1. From the first bump on it fails while the new
 * version's migration is missing from the production builder, or only registered in a test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchemaVersionsTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UmamiDatabase::class.java,
    )

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun committedSchemasAreEveryVersionUpToTheCurrentOne() {
        assertThat(committedVersions()).containsExactlyElementsIn(1..UmamiDatabase.VERSION).inOrder()
    }

    @Test
    fun everyCommittedVersionOpensWithTheProductionBuilder() {
        for (version in committedVersions()) {
            val name = "production-v$version.db"
            helper.createDatabase(name, version).close()

            val db = umamiDatabaseBuilder(
                context(),
                name,
                installMarker = temp.root.resolve("marker-v$version"),
                photosDir = temp.newFolder("photos-v$version"),
            ).allowMainThreadQueries().build()
            try {
                // Opening runs the migrations and validates every table against the entities.
                assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun productionMigrationsMatchTheCurrentExportedSchema() {
        for (version in committedVersions().filter { it < UmamiDatabase.VERSION }) {
            val name = "migrated-v$version.db"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(
                name,
                UmamiDatabase.VERSION,
                true,
                *UMAMI_MIGRATIONS.toTypedArray(),
            ).close()
        }
    }

    /** The versions of the committed schema files, as the test assets carry them (`N.json`). */
    private fun committedVersions(): List<Int> {
        val files = InstrumentationRegistry.getInstrumentation().context.assets
            .list(SCHEMA_ASSET_DIR)
            .orEmpty()
        return files.mapNotNull { SCHEMA_FILE.matchEntire(it)?.groupValues?.get(1)?.toInt() }.sorted()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private companion object {
        val SCHEMA_ASSET_DIR: String = UmamiDatabase::class.java.name
        val SCHEMA_FILE = Regex("""(\d+)\.json""")
    }
}
