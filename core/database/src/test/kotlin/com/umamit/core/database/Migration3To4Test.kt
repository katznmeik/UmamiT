package com.umamit.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MIGRATION_3_4` (05 §9, D-098, #126; spec #124 seam 4): a populated database of the frozen schema v3 — a tea with
 * its cover photo, a vessel, recipes with steps and their own pre-alerts, a finished session in the journal with its
 * stock events, a session left in progress by a device restore, a Merkliste entry, and the active timer in each case
 * below — migrates to v4 with every row and value kept. `active_brew` is keyed by `slot` instead of `id` = 1: the
 * one v3 row goes to the slot of its kind, a brew session to `BREW` and a quick timer to `QUICK`. An ALERTING row
 * rang from its deadline in v0.2.2, so its `alert_ringing_since_*` are the deadline's values; every other row has
 * none. So a timer running, waiting or alerting during the update keeps its state and alerts on time (rule 8).
 * Room's helper validates the result against the exported v4 schema, foreign keys and index included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    /** The active timer of a case as v3 stored it (null: none), and the slot it belongs to in v4. */
    private data class Case(val name: String, val activeBrew: Map<String, Any?>?, val slot: TimerSlot?)

    private val cases = listOf(
        Case("a running brew session", runningSession(), TimerSlot.BREW),
        Case("a running quick timer with its own pre-alert", runningQuickTimer(), TimerSlot.QUICK),
        Case("an alerting quick timer", alertingQuickTimer(), TimerSlot.QUICK),
        Case("an alerting brew session whose alert played", alertingSession(), TimerSlot.BREW),
        Case("no active timer", null, null),
    )

    @Test
    fun `every row and value survives and the active timer moves to the slot of its kind`() {
        cases.forEachIndexed { index, case ->
            val name = "migrate-$index.db"
            val before = helper.createDatabase(name, 3).use { db ->
                populateV3(db, case.activeBrew)
                TABLES.associateWith { db.dump(it) }
            }

            helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).use { db ->
                val label = case.name
                val after = TABLES.associateWith { db.dump(it) }
                (TABLES - "active_brew").forEach { table ->
                    assertWithMessage("$label: $table").that(after[table]).isEqualTo(before[table])
                }
                assertWithMessage("$label: active_brew").that(after["active_brew"])
                    .isEqualTo(listOfNotNull(case.activeBrew?.let { v4Row(it, checkNotNull(case.slot)) }))
            }
        }
    }

    @Test
    fun `the migrated database opens with the production builder and keeps the timer in its slot`() = runTest {
        cases.forEachIndexed { index, case ->
            val name = "production-$index.db"
            helper.createDatabase(name, 3).use { db -> populateV3(db, case.activeBrew) }
            // An update keeps the install marker (05 §8): restore detection leaves the active timer alone.
            val marker = temp.newFile("marker-$index")

            val db = umamiDatabaseBuilder(context(), name, marker, temp.newFolder("photos-$index"))
                .allowMainThreadQueries()
                .build()
            try {
                val label = case.name
                assertWithMessage(label).that(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
                val dao = db.activeBrewDao()
                TimerSlot.entries.filter { it != case.slot }.forEach { other ->
                    assertWithMessage("$label: slot $other").that(dao.get(other)).isNull()
                }
                assertWithMessage("$label: the steps' own pre-alerts")
                    .that(db.sessionDao().steps("live-1").map { it.preAlertSeconds })
                    .containsExactly(null, 20, 0).inOrder()
                assertWithMessage("$label: Merkliste")
                    .that(db.catalogBookmarkDao().observeKeys().first()).containsExactly("TG0002")
                val v3 = case.activeBrew
                if (v3 == null) {
                    assertWithMessage("$label: the one timer").that(dao.get()).isNull()
                    return@forEachIndexed
                }

                val active = dao.get(checkNotNull(case.slot))!!
                assertWithMessage("$label: the one timer").that(dao.get()).isEqualTo(active)
                assertWithMessage("$label: slot").that(active.slot).isEqualTo(case.slot)
                assertWithMessage("$label: quick timer").that(active.isQuickTimer).isEqualTo(v3["is_quick_timer"] == 1)
                assertWithMessage("$label: session").that(active.sessionId).isEqualTo(v3["session_id"])
                assertWithMessage("$label: phase").that(active.phase.name).isEqualTo(v3["phase"])
                assertWithMessage("$label: step").that(active.stepIndex).isEqualTo(v3["step_index"])
                assertWithMessage("$label: deadline").that(active.deadlineElapsedMs).isEqualTo(DEADLINE)
                assertWithMessage("$label: deadline, wall").that(active.deadlineWallMs)
                    .isEqualTo(v3["deadline_wall_ms"])
                assertWithMessage("$label: pre-alert due").that(active.preAlertElapsedMs)
                    .isEqualTo(v3["pre_alert_elapsed_ms"])
                assertWithMessage("$label: alert played").that(active.alertPlayed).isEqualTo(v3["alert_played"] == 1)
                assertWithMessage("$label: boot").that(active.bootCount).isEqualTo(42)
                assertWithMessage("$label: B").that(active.preAlertInheritedSeconds).isEqualTo(10)
                assertWithMessage("$label: quick choice").that(active.quickPreAlertSeconds)
                    .isEqualTo(v3["quick_pre_alert_seconds"])
                val alerting = active.phase == TimerPhase.ALERTING
                assertWithMessage("$label: ringing since")
                    .that(active.alertRingingSinceElapsedMs).isEqualTo(if (alerting) DEADLINE else null)
                assertWithMessage("$label: ringing since, wall")
                    .that(active.alertRingingSinceWallMs).isEqualTo(if (alerting) v3["deadline_wall_ms"] else null)
            } finally {
                db.close()
            }
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v3 fixture: synthetic values only (D-016).

    private fun populateV3(db: SupportSQLiteDatabase, activeBrew: Map<String, Any?>?) {
        db.insert(
            "tea",
            "id" to "tea-1", "name" to "Sencha Probe", "secondary_name" to "Probe", "catalog_key" to "TG0001",
            "style_key" to "sencha", "tea_type" to "Sencha", "category" to "GREEN", "form" to "LOOSE_LEAF",
            "umami_level" to "HIGH", "is_flavored" to 0, "is_organic" to 1, "opened_date" to "2026-10-20",
            "low_stock_grams" to 10.5, "is_favorite" to 1, "is_archived" to 0, "created_at" to 1L, "updated_at" to 2L,
        )
        db.insert(
            "tea",
            "id" to "tea-2", "name" to "Kräuter Probe", "catalog_key" to "TG0003", "style_key" to "herbal",
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
        db.insert("catalog_bookmark", "catalog_key" to "TG0002", "created_at" to 8L)
        // Left in progress without its active_brew row (a device restore, E11).
        db.session("stale-1", "IN_PROGRESS", endedAt = null)
        db.sessionStep("stale-1", 0)
        // The brew session of the brew cases; it stays in progress (and unreferenced) in the others.
        db.session("live-1", "IN_PROGRESS", endedAt = null)
        db.sessionStep("live-1", 0, actual = 60, startedAt = 2_000_000L, endedAt = 2_000_060L)
        db.sessionStep("live-1", 1, startedAt = WALL, preAlert = 20)
        db.sessionStep("live-1", 2, preAlert = 0)
        activeBrew?.let { db.insert("active_brew", *it.toList().toTypedArray()) }
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
            "catalog_bookmark",
            "active_brew",
        )

        /** The v3 row of an active timer with every column: running, its pre-alert 10 s ahead, not yet played. */
        fun activeBrew(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>(
            "id" to 1, "session_id" to "live-1", "is_quick_timer" to 0, "quick_duration_seconds" to null,
            "step_index" to 1, "phase" to "RUNNING", "deadline_elapsed_ms" to DEADLINE,
            "deadline_wall_ms" to WALL + 60_000, "pre_alert_elapsed_ms" to DEADLINE - 10_000, "pre_alert_played" to 0,
            "remaining_ms" to null, "paused_total_ms" to 3_000L, "paused_since_elapsed_ms" to null,
            "paused_since_wall_ms" to null, "ready_since_wall_ms" to null, "step_started_wall_ms" to WALL,
            "alert_played" to 0, "boot_count" to 42, "last_interaction_wall_ms" to WALL, "demoted" to 0,
            "pre_alert_inherited_seconds" to 10, "zone_id" to "Europe/Berlin", "created_at" to 14L,
            "updated_at" to 15L, "quick_pre_alert_seconds" to null,
        ).apply { putAll(fields) }

        /** Step 1 of `live-1` running with its own 20 s (B = 10): the tick is due 20 s before the deadline. */
        fun runningSession() = activeBrew("pre_alert_elapsed_ms" to DEADLINE - 20_000)

        /** A quick timer of 2:00 running with its own 20 s (B = 10): no session, no step rows, the tick due at 1:40. */
        fun runningQuickTimer() = quickTimer(
            "pre_alert_elapsed_ms" to DEADLINE - 20_000,
            "quick_pre_alert_seconds" to 20,
        )

        /** A quick timer of 2:00 with *Aus*, past its deadline and ringing, its alert not yet played in full. */
        fun alertingQuickTimer() = quickTimer(
            "phase" to "ALERTING",
            "pre_alert_elapsed_ms" to null,
            "quick_pre_alert_seconds" to 0,
        )

        /** Step 1 of `live-1` past its deadline, its alert played in full and still waiting for *Abgegossen*. */
        fun alertingSession() = activeBrew("phase" to "ALERTING", "pre_alert_elapsed_ms" to null, "alert_played" to 1)

        fun quickTimer(vararg fields: Pair<String, Any?>) = activeBrew(
            "session_id" to null,
            "is_quick_timer" to 1,
            "quick_duration_seconds" to 120,
            "step_index" to 0,
            "deadline_wall_ms" to WALL + 120_000,
            "paused_total_ms" to 0L,
            *fields,
        )

        /**
         * What v4 must hold for the v3 row [v3]: `slot` in place of `id`, every other value as it was, and the
         * deadline as the time the alert started ringing for an ALERTING row (null otherwise), as an independent
         * statement of 05 §9.
         */
        fun v4Row(v3: Map<String, Any?>, slot: TimerSlot): Map<String, Any?> {
            val alerting = v3["phase"] == "ALERTING"
            return (v3 - "id").mapValues { (_, value) -> value.asStored() } + mapOf(
                "slot" to slot.name,
                "alert_ringing_since_elapsed_ms" to if (alerting) v3["deadline_elapsed_ms"].asStored() else null,
                "alert_ringing_since_wall_ms" to if (alerting) v3["deadline_wall_ms"].asStored() else null,
            )
        }

        /** A fixture value as SQLite gives it back: every INTEGER as Long. */
        fun Any?.asStored(): Any? = if (this is Int) toLong() else this

        /** Inserts a row of [values] into [table], each value bound as it is (null stays NULL). */
        fun SupportSQLiteDatabase.insert(table: String, vararg values: Pair<String, Any?>) {
            val columns = values.joinToString { "`${it.first}`" }
            val marks = values.joinToString { "?" }
            execSQL("INSERT INTO `$table` ($columns) VALUES ($marks)", values.map { it.second }.toTypedArray())
        }

        /** Every row of [table] by its first column (its key), each as its columns and values (INTEGER as Long). */
        fun SupportSQLiteDatabase.dump(table: String): List<Map<String, Any?>> =
            query("SELECT * FROM `$table` ORDER BY 1").use { cursor ->
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
    }
}
