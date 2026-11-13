package com.umamit.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MIGRATION_5_6` (05 §9 „umamit.db v6", D-112, #223): a populated database of the frozen schema v5 — a tea with its
 * cover photo, a vessel, a recipe with steps, a finished session with steps, its tasting values and its stock event,
 * a finished session without steps, a brew session in progress with its `active_brew` row in slot `BREW` and a quick
 * timer in `QUICK` — migrates to v6 with every row and value kept, and `session_descriptor` created empty. A
 * descriptor row then goes with its session (CASCADE). So the timers running during the update keep their state and
 * alert on time (rule 8). Room's helper validates the result against the exported v6 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every row and value survives and session_descriptor starts empty`() {
        val before = helper.createDatabase(NAME, 5).use { db ->
            populateV5(db)
            TABLES.associateWith { db.dump(it) }
        }

        helper.runMigrationsAndValidate(NAME, 6, true, MIGRATION_5_6).use { db ->
            TABLES.forEach { table ->
                assertWithMessage(table).that(db.dump(table)).isEqualTo(before[table])
            }
            assertThat(db.dump("session_descriptor")).isEmpty()
        }
    }

    @Test
    fun `a descriptor row goes with its session`() {
        helper.createDatabase(NAME, 5).use { db -> populateV5(db) }

        helper.runMigrationsAndValidate(NAME, 6, true, MIGRATION_5_6).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.insert(
                "session_descriptor",
                "session_id" to "done-1",
                "descriptor_key" to "marine.nori",
                "created_at" to 20L,
            )
            db.insert(
                "session_descriptor",
                "session_id" to "done-2",
                "descriptor_key" to "marine.nori",
                "created_at" to 21L,
            )
            assertThat(db.dump("session_descriptor")).hasSize(2)

            db.execSQL("DELETE FROM brew_session WHERE id = 'done-1'")

            assertThat(db.dump("session_descriptor").map { it["session_id"] }).containsExactly("done-2")
        }
    }

    @Test
    fun `the migrated database opens with the production builder and both timers keep their slots`() = runTest {
        helper.createDatabase(NAME, 5).use { db -> populateV5(db) }
        // An update keeps the install marker (05 §8): restore detection leaves the active timers alone.
        val marker = temp.newFile("marker")

        val db = umamiDatabaseBuilder(context(), NAME, marker, temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            val done = db.sessionDao().get("done-1")!!
            assertThat(done.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(done.rating).isEqualTo(4.5)
            // The production builder runs on to the current version: from v7 the taste is its first infusion's.
            assertThat(done.tasteUmami).isNull()
            assertThat(db.sessionDao().steps("done-1")[0].tasteUmami).isEqualTo(5)
            assertThat(done.notes).isEqualTo("Süß")
            assertThat(db.sessionDao().steps("done-2")).isEmpty()
            assertThat(db.tastingDao().descriptors("done-1")).isEmpty()

            val dao = db.activeBrewDao()
            val brew = dao.get(TimerSlot.BREW)!!
            assertThat(brew.sessionId).isEqualTo("live-1")
            assertThat(brew.phase).isEqualTo(TimerPhase.RUNNING)
            assertThat(brew.deadlineElapsedMs).isEqualTo(DEADLINE)
            assertThat(brew.preAlertElapsedMs).isEqualTo(DEADLINE - 20_000)
            val quick = dao.get(TimerSlot.QUICK)!!
            assertThat(quick.isQuickTimer).isTrue()
            assertThat(quick.deadlineElapsedMs).isEqualTo(DEADLINE + 30_000)
            assertThat(quick.quickPreAlertSeconds).isEqualTo(20)

            assertThat(db.sessionDao().steps("live-1").map { it.preAlertSeconds })
                .containsExactly(null, 20, 0).inOrder()
            assertThat(db.teaDao().get("tea-1")!!.coverPhotoId).isEqualTo("photo-1")
            assertThat(db.inventoryDao().remainingGrams("tea-1")).isEqualTo(95.5)
        } finally {
            db.close()
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v5 fixture: synthetic values only (D-016).

    private fun populateV5(db: SupportSQLiteDatabase) {
        populateCollection(db)
        populateJournal(db)
        populateTimers(db)
    }

    /** A tea with its cover photo and a stock purchase, a vessel and a recipe with steps. */
    private fun populateCollection(db: SupportSQLiteDatabase) {
        db.insert(
            "tea",
            "id" to "tea-1", "name" to "Sencha Probe", "secondary_name" to "Probe", "catalog_key" to "TG0001",
            "style_key" to "sencha", "tea_type" to "Sencha", "category" to "GREEN", "form" to "LOOSE_LEAF",
            "umami_level" to "HIGH", "is_flavored" to 0, "is_organic" to 1, "opened_date" to "2026-10-20",
            "low_stock_grams" to 10.0, "is_favorite" to 1, "is_archived" to 0, "created_at" to 1L, "updated_at" to 2L,
            "low_stock_warning" to 0,
        )
        db.insert(
            "photo",
            "id" to "photo-1", "tea_id" to "tea-1", "position" to 0, "width_px" to 1200, "height_px" to 800,
            "byte_size" to 123_456L, "origin" to "OWN", "created_at" to 3L, "updated_at" to 3L,
        )
        db.execSQL("UPDATE tea SET cover_photo_id = 'photo-1' WHERE id = 'tea-1'")
        db.insert(
            "vessel",
            "id" to "vessel-1", "name" to "Kyusu", "type" to "KYUSU", "capacity_ml" to 300, "usable_ml" to 250,
            "material" to "UNGLAZED_CLAY", "is_favorite" to 1, "is_archived" to 0, "created_at" to 4L,
            "updated_at" to 4L,
        )
        db.insert(
            "brew_profile",
            "id" to "profile-1", "tea_id" to "tea-1", "name" to "Rezept", "method" to "KYUSU", "leaf_grams" to 4.0,
            "water_ml" to 250, "base_temp_c" to 60, "vessel_id" to "vessel-1", "pre_alert_seconds" to 15,
            "is_default" to 1, "position" to 0, "source" to "CATALOG_TABLE", "notes" to "Notiz", "created_at" to 7L,
            "updated_at" to 8L,
        )
        listOf(60 to null, 20 to 0).forEachIndexed { position, (seconds, preAlert) ->
            db.insert(
                "brew_step",
                "id" to "profile-1-step-$position", "profile_id" to "profile-1", "position" to position,
                "kind" to "INFUSION", "duration_seconds" to seconds, "temp_c" to if (position == 0) null else 65,
                "note" to "Schritt $position", "created_at" to 9L, "updated_at" to 9L, "pre_alert_seconds" to preAlert,
            )
        }
        db.insert(
            "inventory_event",
            "id" to "purchase-1", "tea_id" to "tea-1", "type" to "INITIAL", "delta_grams" to 100.0,
            "price_cents" to 1290L, "currency" to "EUR", "package_label" to "100 g", "occurred_at" to 900_000L,
            "created_at" to 5L, "updated_at" to 5L,
        )
    }

    /** A finished session with steps, tasting values and its BREW event, and one without steps or tasting. */
    private fun populateJournal(db: SupportSQLiteDatabase) {
        db.session("done-1", "COMPLETED", endedAt = 1_000_300L, tasted = true)
        db.sessionStep("done-1", 0, actual = 61, startedAt = 1_000_000L, endedAt = 1_000_061L)
        db.sessionStep("done-1", 1)
        db.insert(
            "inventory_event",
            "id" to "brew-1", "tea_id" to "tea-1", "session_id" to "done-1", "type" to "BREW", "delta_grams" to -4.5,
            "occurred_at" to 1_000_061L, "created_at" to 6L, "updated_at" to 6L,
        )
        db.session("done-2", "COMPLETED", endedAt = 1_500_000L, tasted = false)
    }

    /** The brew session running in slot BREW (step 1 with its own 20 s) and a quick timer running in QUICK. */
    private fun populateTimers(db: SupportSQLiteDatabase) {
        db.session("live-1", "IN_PROGRESS", endedAt = null, tasted = false)
        db.sessionStep("live-1", 0, actual = 60, startedAt = 2_000_000L, endedAt = 2_000_060L)
        db.sessionStep("live-1", 1, startedAt = WALL, preAlert = 20)
        db.sessionStep("live-1", 2, preAlert = 0)
        db.insert("active_brew", *activeBrew().toList().toTypedArray())
        db.insert(
            "active_brew",
            *activeBrew(
                "slot" to "QUICK", "session_id" to null, "is_quick_timer" to 1, "quick_duration_seconds" to 120,
                "step_index" to 0, "deadline_elapsed_ms" to DEADLINE + 30_000, "deadline_wall_ms" to WALL + 120_000,
                "pre_alert_elapsed_ms" to DEADLINE + 10_000, "paused_total_ms" to 0L, "quick_pre_alert_seconds" to 20,
            ).toList().toTypedArray(),
        )
    }

    private fun SupportSQLiteDatabase.session(id: String, status: String, endedAt: Long?, tasted: Boolean) {
        val finished = endedAt != null
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to "tea-1", "tea_name" to "Sencha Probe",
            "tea_secondary_name" to "Probe", "tea_type" to "Sencha", "style_key" to "sencha", "category" to "GREEN",
            "profile_id" to "profile-1", "profile_name" to "Rezept", "method" to "KYUSU",
            "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5, "water_ml" to 250,
            "base_temp_c" to 60, "pre_alert_seconds" to 10, "planned_step_count" to if (finished) 2 else 3,
            "cost_cents" to if (finished) 58L else null, "started_at" to 1_000_000L, "ended_at" to endedAt,
            "zone_id" to "Europe/Berlin", "rating" to if (tasted) 4.5 else null,
            "taste_umami" to if (tasted) 5 else null, "taste_aroma" to if (tasted) 3 else null,
            "notes" to if (tasted) "Süß" else null, "auto_finished" to if (id == "done-2") 1 else 0,
            "times_offered" to 1, "times_applied" to 0, "created_at" to 10L, "updated_at" to 11L,
        )
    }

    @Suppress("LongParameterList") // a step row's values
    private fun SupportSQLiteDatabase.sessionStep(
        sessionId: String,
        position: Int,
        actual: Int? = null,
        startedAt: Long? = null,
        endedAt: Long? = null,
        preAlert: Int? = null,
    ) {
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to "INFUSION", "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to endedAt, "skipped" to 0, "overtime_auto_ack" to 0,
            "note" to "Aufguss $position", "created_at" to 12L, "updated_at" to 13L, "pre_alert_seconds" to preAlert,
        )
    }

    private companion object {
        const val NAME = "migrate-5-6.db"
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

        /** A v5 `active_brew` row with every column: slot BREW, running, its pre-alert 20 s ahead, not yet played. */
        fun activeBrew(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>(
            "slot" to "BREW", "session_id" to "live-1", "is_quick_timer" to 0, "quick_duration_seconds" to null,
            "step_index" to 1, "phase" to "RUNNING", "deadline_elapsed_ms" to DEADLINE,
            "deadline_wall_ms" to WALL + 60_000, "pre_alert_elapsed_ms" to DEADLINE - 20_000, "pre_alert_played" to 0,
            "remaining_ms" to null, "paused_total_ms" to 3_000L, "paused_since_elapsed_ms" to null,
            "paused_since_wall_ms" to null, "ready_since_wall_ms" to null, "step_started_wall_ms" to WALL,
            "alert_played" to 0, "boot_count" to 42, "last_interaction_wall_ms" to WALL, "demoted" to 0,
            "pre_alert_inherited_seconds" to 10, "zone_id" to "Europe/Berlin", "created_at" to 14L,
            "updated_at" to 15L, "quick_pre_alert_seconds" to null, "alert_ringing_since_elapsed_ms" to null,
            "alert_ringing_since_wall_ms" to null,
        ).apply { putAll(fields) }

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
