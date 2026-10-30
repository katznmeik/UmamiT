package com.umamit.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.TimerPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MIGRATION_2_3` (05 §9, D-097, #110; spec #108 seam 3): a populated database of the frozen schema v2 — a tea with
 * its cover photo, a vessel, recipes with steps and their own pre-alerts, a finished session in the journal with its
 * stock events, a session left in progress by a device restore, and the active timer, once a running brew session
 * and once a running quick timer — migrates to v3 with every row and value kept. The Merkliste's `catalog_bookmark`
 * (05 §3.13) is new and empty; `active_brew` gains `quick_pre_alert_seconds` as its last column, null, so a quick
 * timer running during the update reads as *Automatisch*, as it ran in v0.2.1 (rule 8). Room's helper validates the
 * result against the exported v3 schema, foreign keys included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    /** The active timer of a case, as v2 stored it. */
    private data class Case(val name: String, val activeBrew: Map<String, Any?>)

    private val cases = listOf(
        Case("a running brew session", runningSession()),
        Case("a running quick timer", runningQuickTimer()),
    )

    @Test
    fun `every row and value survives, the Merkliste is empty and the quick timer's choice is Automatisch`() {
        cases.forEachIndexed { index, case ->
            val name = "migrate-$index.db"
            val before = helper.createDatabase(name, 2).use { db ->
                populateV2(db, case.activeBrew)
                TABLES.associateWith { db.dump(it) }
            }

            helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { db ->
                val label = case.name
                val after = TABLES.associateWith { db.dump(it) }
                (TABLES - "active_brew").forEach { table ->
                    assertWithMessage("$label: $table").that(after[table]).isEqualTo(before[table])
                }
                assertWithMessage("$label: active_brew").that(after["active_brew"])
                    .containsExactly(before.getValue("active_brew").single() + (QUICK_PRE_ALERT to null))
                assertWithMessage("$label: the new column is the last one")
                    .that(db.columns("active_brew").last()).isEqualTo(QUICK_PRE_ALERT)
                assertWithMessage("$label: catalog_bookmark")
                    .that(db.count("catalog_bookmark")).isEqualTo(0)
                assertWithMessage("$label: catalog_bookmark columns")
                    .that(db.columns("catalog_bookmark")).containsExactly("catalog_key", "created_at").inOrder()
            }
        }
    }

    @Test
    fun `the migrated database opens with the production builder and keeps the running timer`() = runTest {
        cases.forEachIndexed { index, case ->
            val name = "production-$index.db"
            helper.createDatabase(name, 2).use { db -> populateV2(db, case.activeBrew) }
            // An update keeps the install marker (05 §8): restore detection leaves the active timer alone.
            val marker = temp.newFile("marker-$index")

            val db = umamiDatabaseBuilder(context(), name, marker, temp.newFolder("photos-$index"))
                .allowMainThreadQueries()
                .build()
            try {
                val label = case.name
                assertWithMessage(label).that(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
                val active = db.activeBrewDao().get()!!
                assertWithMessage("$label: session").that(active.sessionId).isEqualTo(case.activeBrew["session_id"])
                assertWithMessage("$label: phase").that(active.phase).isEqualTo(TimerPhase.RUNNING)
                assertWithMessage("$label: deadline").that(active.deadlineElapsedMs).isEqualTo(DEADLINE)
                assertWithMessage("$label: pre-alert due").that(active.preAlertElapsedMs)
                    .isEqualTo(case.activeBrew["pre_alert_elapsed_ms"])
                assertWithMessage("$label: B").that(active.preAlertInheritedSeconds).isEqualTo(10)
                assertWithMessage("$label: Automatisch").that(active.quickPreAlertSeconds).isNull()
                assertWithMessage("$label: the steps' own pre-alerts")
                    .that(db.sessionDao().steps("live-1").map { it.preAlertSeconds })
                    .containsExactly(null, 20, 0).inOrder()
                assertWithMessage("$label: Merkliste").that(db.catalogBookmarkDao().observeKeys().first()).isEmpty()
            } finally {
                db.close()
            }
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v2 fixture: synthetic values only (D-016).

    private fun populateV2(db: SupportSQLiteDatabase, activeBrew: Map<String, Any?>) {
        db.insert(
            "tea",
            "id" to "tea-1", "name" to "Sencha Probe", "secondary_name" to "Probe", "catalog_key" to "TG0001",
            "style_key" to "sencha", "tea_type" to "Sencha", "category" to "GREEN", "form" to "LOOSE_LEAF",
            "umami_level" to "HIGH", "is_flavored" to 0, "is_organic" to 1, "opened_date" to "2026-10-20",
            "low_stock_grams" to 10.5, "is_favorite" to 1, "is_archived" to 0, "created_at" to 1L, "updated_at" to 2L,
        )
        db.insert(
            "tea",
            "id" to "tea-2", "name" to "Kräuter Probe", "catalog_key" to "TG0002", "style_key" to "herbal",
            "category" to "HERBAL", "form" to "LOOSE_LEAF", "is_flavored" to 0, "is_organic" to 0,
            "is_favorite" to 0, "is_archived" to 1, "created_at" to 1L, "updated_at" to 2L,
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
        db.recipe("profile-1", preAlert = 15, default = true, steps = listOf(60 to null, 20 to 0, 30 to 25))
        db.recipe("profile-2", preAlert = null, default = false, steps = listOf(120 to null))
        db.session("done-1", "COMPLETED", endedAt = 1_000_300L)
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
        db.insert(
            "inventory_event",
            "id" to "adjust-1", "tea_id" to "tea-1", "type" to "ADJUSTMENT", "delta_grams" to -2.0,
            "note" to "Korrektur", "occurred_at" to 1_100_000L, "created_at" to 7L, "updated_at" to 7L,
        )
        // Left in progress without its active_brew row (a device restore, E11).
        db.session("stale-1", "IN_PROGRESS", endedAt = null)
        db.sessionStep("stale-1", 0)
        // The brew session of the first case; it stays in progress (and unreferenced) in the quick timer's case.
        db.session("live-1", "IN_PROGRESS", endedAt = null)
        db.sessionStep("live-1", 0, actual = 60, startedAt = 2_000_000L, endedAt = 2_000_060L)
        db.sessionStep("live-1", 1, startedAt = WALL, preAlert = 20)
        db.sessionStep("live-1", 2, preAlert = 0)
        db.insert("active_brew", *activeBrew.toList().toTypedArray())
    }

    private fun SupportSQLiteDatabase.recipe(
        id: String,
        preAlert: Int?,
        default: Boolean,
        steps: List<Pair<Int, Int?>>,
    ) {
        insert(
            "brew_profile",
            "id" to id, "tea_id" to "tea-1", "name" to "Rezept $id", "method" to "KYUSU", "leaf_grams" to 4.0,
            "water_ml" to 250, "base_temp_c" to 60, "vessel_id" to "vessel-1", "pre_alert_seconds" to preAlert,
            "is_default" to if (default) 1 else 0, "position" to if (default) 0 else 1, "source" to "CATALOG_TABLE",
            "notes" to "Notiz", "created_at" to 7L, "updated_at" to 8L,
        )
        steps.forEachIndexed { position, (seconds, stepPreAlert) ->
            insert(
                "brew_step",
                "id" to "$id-step-$position", "profile_id" to id, "position" to position, "kind" to "INFUSION",
                "duration_seconds" to seconds, "temp_c" to if (position == 0) null else 65,
                "note" to "Schritt $position", "created_at" to 9L, "updated_at" to 9L,
                "pre_alert_seconds" to stepPreAlert,
            )
        }
    }

    private fun SupportSQLiteDatabase.session(id: String, status: String, endedAt: Long?) {
        val finished = endedAt != null
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to "tea-1", "tea_name" to "Sencha Probe",
            "tea_secondary_name" to "Probe", "tea_type" to "Sencha", "style_key" to "sencha", "category" to "GREEN",
            "profile_id" to "profile-1", "profile_name" to "Rezept profile-1", "method" to "KYUSU",
            "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5, "water_ml" to 250,
            "base_temp_c" to 60, "pre_alert_seconds" to 10, "planned_step_count" to 3,
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
        preAlert: Int? = null,
    ) {
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to "INFUSION", "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to endedAt, "skipped" to if (skipped) 1 else 0,
            "overtime_auto_ack" to 0, "note" to "Aufguss $position", "created_at" to 12L, "updated_at" to 13L,
            "pre_alert_seconds" to preAlert,
        )
    }

    private companion object {
        const val QUICK_PRE_ALERT = "quick_pre_alert_seconds"
        const val WALL = 1_761_120_000_000L
        const val DEADLINE = 5_060_000L

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

        /** The v2 row of an active timer with every column: running, its pre-alert 10 s ahead, not yet played. */
        fun activeBrew(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>(
            "id" to 1, "session_id" to "live-1", "is_quick_timer" to 0, "quick_duration_seconds" to null,
            "step_index" to 1, "phase" to "RUNNING", "deadline_elapsed_ms" to DEADLINE,
            "deadline_wall_ms" to WALL + 60_000, "pre_alert_elapsed_ms" to DEADLINE - 10_000, "pre_alert_played" to 0,
            "remaining_ms" to null, "paused_total_ms" to 3_000L, "paused_since_elapsed_ms" to null,
            "paused_since_wall_ms" to null, "ready_since_wall_ms" to null, "step_started_wall_ms" to WALL,
            "alert_played" to 0, "boot_count" to 42, "last_interaction_wall_ms" to WALL, "demoted" to 0,
            "pre_alert_inherited_seconds" to 10, "zone_id" to "Europe/Berlin", "created_at" to 14L,
            "updated_at" to 15L,
        ).apply { putAll(fields) }

        /** Step 1 of `live-1` running with its own 20 s (B = 10): the tick is due 20 s before the deadline. */
        fun runningSession() = activeBrew("pre_alert_elapsed_ms" to DEADLINE - 20_000)

        /** A quick timer of 2:00 running with B = 10: no session, no step rows, the pre-alert due at 1:50 (04 §10). */
        fun runningQuickTimer() = activeBrew(
            "session_id" to null,
            "is_quick_timer" to 1,
            "quick_duration_seconds" to 120,
            "step_index" to 0,
            "deadline_wall_ms" to WALL + 120_000,
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

        /** The columns of [table] in their order. */
        fun SupportSQLiteDatabase.columns(table: String): List<String> =
            query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }

        fun SupportSQLiteDatabase.count(table: String): Long = query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

        fun Cursor.valueOf(index: Int): Any? = when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            else -> getString(index)
        }
    }
}
