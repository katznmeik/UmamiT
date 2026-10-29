package com.umamit.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The committed schema JSON (05 §9) matches the entities: a database created from
 * `schemas/…/N.json` of the current version N opens with the current entities, which Room only
 * allows when the schema's identity hash equals the one generated from the entities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportedSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UmamiDatabase::class.java,
    )

    @Test
    fun committedSchemaOfTheCurrentVersionMatchesTheEntities() = runTest {
        helper.createDatabase(DB_NAME, UmamiDatabase.VERSION).close()

        val db = Room.databaseBuilder(context(), UmamiDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening validates the identity hash and every table against the entities.
            assertThat(db.activeBrewDao().get()).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun schemaHasAllM1Tables() {
        val db = helper.createDatabase(DB_NAME, UmamiDatabase.VERSION)
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        db.close()

        assertThat(tables).containsAtLeast(
            "tea",
            "brew_profile",
            "brew_step",
            "vessel",
            "brew_session",
            "session_step",
            "inventory_event",
            "photo",
            "active_brew",
        )
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val DB_NAME = "schema-test.db"
    }
}
