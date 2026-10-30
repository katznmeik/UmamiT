package com.umamit.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MIGRATION_1_2` (05 §9, D-093, #90; spec #81 seam 2): a populated database of the frozen schema v1 — a tea with
 * its cover photo, a vessel, recipes with steps, a finished session in the journal with its stock events, a session
 * left in progress by a device restore, and the active timer — migrates to v2 with every row and value kept.
 * `brew_step` and `session_step` gain `pre_alert_seconds` (null = *Automatisch*); the active session's steps that
 * have not ended get the lead v1 gave them, its override (`active_brew.pre_alert_override_seconds`) or else the
 * inherited B (`active_brew.pre_alert_inherited_seconds`), so a session running during the update keeps its leads
 * (D-096), and the override column is dropped. Room's helper validates the result against the exported v2 schema,
 * foreign keys included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    /** The active timer of a case, as v1 stored it, and the pre-alert each step of `live-1` has after the update. */
    private data class Case(val name: String, val activeBrew: Map<String, Any?>, val liveSteps: List<Int?>)

    private val cases = listOf(
        // Step 0 poured, step 1 running with the session's own 20 s, step 2 pending.
        Case("running with an override", running(override = 20), liveSteps = listOf(null, 20, 20)),
        // Waiting for step 1 with the session's *Aus*: the running and the pending steps keep it.
        Case("waiting with the pre-alert off", waiting(override = 0), liveSteps = listOf(null, 0, 0)),
        // Without an override v1 gave every step the inherited B (10 s here), whatever its length: the steps that have
        // not ended keep it, so a 15–29 s step does not drop to the automatic 5 s.
        Case("running without an override", running(override = null), liveSteps = listOf(null, 10, 10)),
        Case("waiting without an override", waiting(override = null, inherited = 15), liveSteps = listOf(null, 15, 15)),
        Case("waiting with B off", waiting(override = null, inherited = 0), liveSteps = listOf(null, 0, 0)),
        Case("a quick timer", quickTimer(), liveSteps = listOf(null, null, null)),
    )

    @Test
    fun `every row survives and the active session's steps that have not ended keep their v1 lead`() {
        cases.forEach { case ->
            val name = "migrate-${cases.indexOf(case)}.db"
            val before = helper.createDatabase(name, 1).use { db ->
                populateV1(db, case.activeBrew)
                TABLES.associateWith { db.dump(it) }
            }

            val after = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).use { db ->
                TABLES.associateWith { db.dump(it) }
            }

            val label = case.name
            UNCHANGED_TABLES.forEach { table ->
                assertWithMessage("$label: $table").that(after[table]).isEqualTo(before[table])
            }
            assertWithMessage("$label: brew_step").that(after.getValue("brew_step").without(PRE_ALERT))
                .isEqualTo(before["brew_step"])
            assertWithMessage("$label: recipe steps are Automatisch")
                .that(after.getValue("brew_step").map { it[PRE_ALERT] }).containsExactly(null, null, null, null)
            assertWithMessage("$label: session_step").that(after.getValue("session_step").without(PRE_ALERT))
                .isEqualTo(before["session_step"])
            assertWithMessage("$label: the active session's steps")
                .that(after.getValue("session_step").filter { it["session_id"] == "live-1" }.map { it[PRE_ALERT] })
                .containsExactlyElementsIn(case.liveSteps.map { it?.toLong() }).inOrder()
            assertWithMessage("$label: other sessions' steps are Automatisch")
                .that(after.getValue("session_step").filter { it["session_id"] != "live-1" }.map { it[PRE_ALERT] })
                .containsExactly(null, null, null)
            assertWithMessage("$label: active_brew").that(after["active_brew"])
                .isEqualTo(before.getValue("active_brew").without(OVERRIDE))
        }
    }

    @Test
    fun `the migrated database opens with the production builder and reads each step's pre-alert`() = runTest {
        val name = "production.db"
        helper.createDatabase(name, 1).use { db -> populateV1(db, running(override = 20)) }
        // An update keeps the install marker (05 §8): restore detection leaves the active timer alone.
        val marker = temp.newFile("marker")

        val db = umamiDatabaseBuilder(context(), name, marker, temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            // The production builder migrates on to the current version (v3: MIGRATION_2_3 keeps every v2 value).
            assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            assertThat(db.activeBrewDao().get()?.sessionId).isEqualTo("live-1")
            assertThat(db.sessionDao().steps("live-1").map { it.preAlertSeconds }).containsExactly(null, 20, 20)
                .inOrder()
            assertThat(db.recipeDao().get("profile-1")?.steps?.map { it.preAlertSeconds })
                .containsExactly(null, null, null)
        } finally {
            db.close()
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v1 fixture: synthetic values only (D-016).

    private fun populateV1(db: SupportSQLiteDatabase, activeBrew: Map<String, Any?>) {
        db.insert(
            "tea",
            "id" to "tea-1", "name" to "Sencha Probe", "secondary_name" to "Probe", "catalog_key" to "TG0001",
            "style_key" to "sencha", "tea_type" to "Sencha", "category" to "GREEN", "form" to "LOOSE_LEAF",
            "umami_level" to "HIGH", "is_flavored" to 0, "is_organic" to 1, "opened_date" to "2026-10-20",
            "low_stock_grams" to 10.5, "is_favorite" to 1, "is_archived" to 0, "created_at" to 1L, "updated_at" to 2L,
        )
        db.insert(
            "photo",
            "id" to "photo-1", "tea_id" to "tea-1", "position" to 0, "width_px" to 1200, "height_px" to 800,
            "byte_size" to 123_456L, "origin" to "CATALOG_COPY", "created_at" to 3L, "updated_at" to 3L,
        )
        db.execSQL("UPDATE tea SET cover_photo_id = 'photo-1' WHERE id = 'tea-1'")
        db.insert(
            "vessel",
            "id" to "vessel-1", "name" to "Kyusu", "type" to "KYUSU", "capacity_ml" to 300, "usable_ml" to 250,
            "material" to "UNGLAZED_CLAY", "is_favorite" to 1, "is_archived" to 0, "created_at" to 4L,
            "updated_at" to 4L,
        )
        db.recipe("profile-1", preAlert = 15, default = true, steps = listOf(60, 20, 30))
        db.recipe("profile-2", preAlert = null, default = false, steps = listOf(120))
        db.session("done-1", "COMPLETED", preAlert = 20, endedAt = 1_000_300L)
        db.sessionStep("done-1", 0, actual = 61, startedAt = 1_000_000L, endedAt = 1_000_061L)
        db.sessionStep("done-1", 1, actual = 0, skipped = true)
        db.insert(
            "inventory_event",
            "id" to "purchase-1", "tea_id" to "tea-1", "type" to "INITIAL", "delta_grams" to 100.0,
            "price_cents" to 1290L, "currency" to "EUR", "package_label" to "100 g", "occurred_at" to 900_000L,
            "created_at" to 5L, "updated_at" to 5L,
        )
        db.insert(
            "inventory_event",
            "id" to "brew-1", "tea_id" to "tea-1", "session_id" to "done-1", "type" to "BREW", "delta_grams" to -4.5,
            "occurred_at" to 1_000_061L, "created_at" to 6L, "updated_at" to 6L,
        )
        // Left in progress without its active_brew row (a device restore, E11).
        db.session("stale-1", "IN_PROGRESS", preAlert = 10, endedAt = null)
        db.sessionStep("stale-1", 0)
        db.session("live-1", "IN_PROGRESS", preAlert = 20, endedAt = null)
        db.sessionStep("live-1", 0, actual = 60, startedAt = 2_000_000L, endedAt = 2_000_060L)
        db.sessionStep("live-1", 1)
        db.sessionStep("live-1", 2)
        db.insert("active_brew", *activeBrew.toList().toTypedArray())
    }

    private fun SupportSQLiteDatabase.recipe(id: String, preAlert: Int?, default: Boolean, steps: List<Int>) {
        insert(
            "brew_profile",
            "id" to id, "tea_id" to "tea-1", "name" to "Rezept $id", "method" to "KYUSU", "leaf_grams" to 4.0,
            "water_ml" to 250, "base_temp_c" to 60, "vessel_id" to "vessel-1", "pre_alert_seconds" to preAlert,
            "is_default" to if (default) 1 else 0, "position" to if (default) 0 else 1, "source" to "CATALOG_TABLE",
            "notes" to "Notiz", "created_at" to 7L, "updated_at" to 8L,
        )
        steps.forEachIndexed { position, seconds ->
            insert(
                "brew_step",
                "id" to "$id-step-$position", "profile_id" to id, "position" to position, "kind" to "INFUSION",
                "duration_seconds" to seconds, "temp_c" to if (position == 0) null else 65,
                "note" to "Schritt $position", "created_at" to 9L, "updated_at" to 9L,
            )
        }
    }

    private fun SupportSQLiteDatabase.session(id: String, status: String, preAlert: Int, endedAt: Long?) {
        val finished = endedAt != null
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to "tea-1", "tea_name" to "Sencha Probe",
            "tea_secondary_name" to "Probe", "tea_type" to "Sencha", "style_key" to "sencha", "category" to "GREEN",
            "profile_id" to "profile-1", "profile_name" to "Rezept profile-1", "method" to "KYUSU",
            "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5, "water_ml" to 250,
            "base_temp_c" to 60, "pre_alert_seconds" to preAlert, "planned_step_count" to 3,
            "cost_cents" to if (finished) 58L else null, "started_at" to 1_000_000L, "ended_at" to endedAt,
            "zone_id" to "Europe/Berlin", "rating" to if (finished) 4.5 else null,
            "taste_umami" to if (finished) 5 else null, "notes" to if (finished) "Süß" else null,
            "auto_finished" to 0, "times_offered" to 1, "times_applied" to 0, "created_at" to 10L, "updated_at" to 11L,
        )
    }

    private fun SupportSQLiteDatabase.sessionStep(
        sessionId: String,
        position: Int,
        actual: Int? = null,
        startedAt: Long? = null,
        endedAt: Long? = null,
        skipped: Boolean = false,
    ) {
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to "INFUSION", "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to endedAt, "skipped" to if (skipped) 1 else 0,
            "overtime_auto_ack" to 0, "note" to "Aufguss $position", "created_at" to 12L, "updated_at" to 13L,
        )
    }

    private companion object {
        const val PRE_ALERT = "pre_alert_seconds"
        const val OVERRIDE = "pre_alert_override_seconds"
        const val WALL = 1_761_120_000_000L

        val TABLES = listOf(
            "tea",
            "photo",
            "vessel",
            "brew_profile",
            "brew_step",
            "brew_session",
            "session_step",
            "inventory_event",
            "active_brew",
        )
        val UNCHANGED_TABLES = TABLES - setOf("brew_step", "session_step", "active_brew")

        /** The v1 row of an active timer with every column, phase fields after [fields]. */
        fun activeBrew(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>(
            "id" to 1, "session_id" to "live-1", "is_quick_timer" to 0, "quick_duration_seconds" to null,
            "step_index" to 1, "phase" to "READY", "deadline_elapsed_ms" to null, "deadline_wall_ms" to null,
            "pre_alert_elapsed_ms" to null, "pre_alert_played" to 0, "remaining_ms" to null, "paused_total_ms" to 0L,
            "paused_since_elapsed_ms" to null, "paused_since_wall_ms" to null, "ready_since_wall_ms" to null,
            "step_started_wall_ms" to null, "alert_played" to 0, "boot_count" to 42, "last_interaction_wall_ms" to WALL,
            "demoted" to 0, OVERRIDE to null, "pre_alert_inherited_seconds" to 10, "zone_id" to "Europe/Berlin",
            "created_at" to 14L, "updated_at" to 15L,
        ).apply { putAll(fields) }

        /** Step 1 of `live-1` running, its pre-alert [override] s ahead (v1: override ?: B). */
        fun running(override: Int?) = activeBrew(
            "phase" to "RUNNING",
            "deadline_elapsed_ms" to 5_060_000L,
            "deadline_wall_ms" to WALL + 60_000,
            "pre_alert_elapsed_ms" to 5_060_000L - (override ?: 10) * 1_000L,
            "step_started_wall_ms" to WALL,
            OVERRIDE to override,
        )

        /** `live-1` waiting in READY for step 1, with the inherited B of [inherited] s. */
        fun waiting(override: Int?, inherited: Int = 10) = activeBrew(
            "ready_since_wall_ms" to WALL,
            OVERRIDE to override,
            "pre_alert_inherited_seconds" to inherited,
        )

        /** A quick timer of 2 min waiting in READY: no session, no override (04 §10). */
        fun quickTimer() = activeBrew(
            "session_id" to null,
            "is_quick_timer" to 1,
            "quick_duration_seconds" to 120,
            "step_index" to 0,
            "ready_since_wall_ms" to WALL,
        )

        /** Inserts a row of [values] into [table], each value bound as it is (null stays NULL). */
        fun SupportSQLiteDatabase.insert(table: String, vararg values: Pair<String, Any?>) {
            val columns = values.joinToString { "`${it.first}`" }
            val marks = values.joinToString { "?" }
            execSQL("INSERT INTO `$table` ($columns) VALUES ($marks)", values.map { it.second }.toTypedArray())
        }

        /** Every row of [table] by `id`, each as its columns and values (INTEGER as Long, REAL as Double). */
        fun SupportSQLiteDatabase.dump(table: String): List<Map<String, Any?>> =
            query("SELECT * FROM `$table` ORDER BY id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.columnNames.associateWith { cursor.valueOf(cursor.getColumnIndexOrThrow(it)) })
                    }
                }
            }

        fun Cursor.valueOf(index: Int): Any? = when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            else -> getString(index)
        }

        /** These rows without [column]. */
        fun List<Map<String, Any?>>.without(column: String): List<Map<String, Any?>> = map { it - column }
    }
}
