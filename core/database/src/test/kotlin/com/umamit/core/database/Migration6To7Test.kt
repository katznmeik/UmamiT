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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MIGRATION_6_7` (05 §9 „umamit.db v7", JRN-03, D-114, #242): a populated database of the frozen schema v6 migrates
 * to v7. `session_step` gains the six taste columns and `tasting_note`; each session's taste values move to one of its
 * steps (the first ended one that is neither skipped nor *Spülen*; else the first that is not *Spülen*; else position
 * 0) and the session's own columns become null. The fixture: a finished session with all six values and a rinse
 * first, one with two values, one without values, one whose steps were never brewed, one whose only step is a rinse,
 * one whose 1st infusion was skipped, one whose steps were all skipped, one with values and no step row (left as it
 * is), and a session in progress with its `active_brew` row (and a quick timer). The tea's radar reads the same before
 * and after (JRN-03 AC3). Every row of every table
 * is checked against what it was; Room's helper validates the result against the exported v7 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every other table is unchanged`() {
        val before = helper.createDatabase(NAME, 6).use { db ->
            populateV6(db)
            UNCHANGED_TABLES.associateWith { db.dump(it) }
        }

        helper.runMigrationsAndValidate(NAME, 7, true, MIGRATION_6_7).use { db ->
            UNCHANGED_TABLES.forEach { table ->
                assertWithMessage(table).that(db.dump(table)).isEqualTo(before[table])
            }
        }
    }

    @Test
    fun `each session's taste moves to its first infusion and the session's columns become null`() {
        val before = helper.createDatabase(NAME, 6).use { db ->
            populateV6(db)
            db.dump("brew_session") to db.dump("session_step")
        }
        val (sessionsBefore, stepsBefore) = before

        helper.runMigrationsAndValidate(NAME, 7, true, MIGRATION_6_7).use { db ->
            // brew_session: every column as it was, but the moved sessions' six taste columns.
            val moved = setOf("all-six", "two", "unbrewed", "rinse-only", "skipped-first", "all-skipped")
            val expectedSessions = sessionsBefore.map { row ->
                if (row["id"] in moved) row + TASTE_COLUMNS.associateWith { null } else row
            }
            assertThat(db.dump("brew_session")).isEqualTo(expectedSessions)

            // session_step: every row as it was plus the seven new columns, set on the target row only.
            val targets = mapOf(
                ("all-six" to 1L) to ALL_SIX,
                ("two" to 0L) to TWO,
                ("unbrewed" to 1L) to UNBREWED,
                ("rinse-only" to 0L) to RINSE_ONLY,
                ("skipped-first" to 1L) to SKIPPED_FIRST,
                ("all-skipped" to 0L) to ALL_SKIPPED,
            )
            val expectedSteps = stepsBefore.map { row ->
                val values = targets[row["session_id"] to row["position"]] ?: NO_TASTE
                row + TASTE_COLUMNS.zip(values).toMap() + ("tasting_note" to null)
            }
            assertThat(db.dump("session_step")).isEqualTo(expectedSteps)
        }
    }

    @Test
    fun `the targets are the ones 05 names`() {
        helper.createDatabase(NAME, 6).use { db -> populateV6(db) }

        helper.runMigrationsAndValidate(NAME, 7, true, MIGRATION_6_7).use { db ->
            fun tasted(sessionId: String) = db.dump("session_step")
                .filter { it["session_id"] == sessionId && TASTE_COLUMNS.any { c -> it[c] != null } }
                .map { it["position"] }

            // The rinse at 0 is ended, but the first ended infusion takes the values.
            assertThat(tasted("all-six")).containsExactly(1L)
            assertThat(tasted("two")).containsExactly(0L)
            assertThat(tasted("none")).isEmpty()
            // No ended step: the first that is not a rinse (its rinse at 0 never brewed either).
            assertThat(tasted("unbrewed")).containsExactly(1L)
            assertThat(tasted("rinse-only")).containsExactly(0L)
            // A skipped step records 0 s but was never poured (D-114 build note, T3 m1): the 2nd, poured, takes them.
            assertThat(tasted("skipped-first")).containsExactly(1L)
            // Every step skipped: the first that is not a rinse still keeps the values (nothing is lost).
            assertThat(tasted("all-skipped")).containsExactly(0L)
            assertThat(tasted("live")).isEmpty()
            // A session without step rows keeps its values on its own row (the tea's radar still reads them).
            val stepless = db.dump("brew_session").single { it["id"] == "stepless" }
            assertThat(TASTE_COLUMNS.map { stepless[it] }).isEqualTo(STEPLESS)
        }
    }

    @Test
    fun `the migrated database opens with the production builder, the timers keep their slots`() = runTest {
        // The tea's radar as v0.6.0 read it: per axis over its finished sessions that set it.
        val radarBefore = helper.createDatabase(NAME, 6).use { db ->
            populateV6(db)
            db.query(
                "SELECT ${TASTE_COLUMNS.joinToString { "AVG($it)" }} FROM brew_session " +
                    "WHERE tea_id = 'tea-1' AND status != 'IN_PROGRESS'",
            ).use { cursor ->
                cursor.moveToFirst()
                TASTE_COLUMNS.indices.map { cursor.valueOf(it) }
            }
        }
        // An update keeps the install marker (05 §8): restore detection leaves the active timers alone.
        val marker = temp.newFile("marker")

        val db = umamiDatabaseBuilder(context(), NAME, marker, temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            val allSix = db.sessionDao().get("all-six")!!
            assertThat(allSix.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(allSix.tasteUmami).isNull()
            assertThat(allSix.rating).isEqualTo(4.5)
            assertThat(allSix.notes).isEqualTo("Süß")
            val infusion = db.sessionDao().steps("all-six")[1]
            assertThat(
                listOf(
                    infusion.tasteUmami,
                    infusion.tasteSweetness,
                    infusion.tasteBitterness,
                    infusion.tasteAstringency,
                    infusion.tasteBody,
                    infusion.tasteAroma,
                ),
            ).isEqualTo(ALL_SIX.map { it?.toInt() })
            assertThat(infusion.tastingNote).isNull()

            // The tea's radar is the one it had before the update (JRN-03 AC3).
            val averages = db.tastingDao().observeTasteAverages("tea-1").first()
            assertThat(
                listOf(
                    averages.umami,
                    averages.sweetness,
                    averages.bitterness,
                    averages.astringency,
                    averages.body,
                    averages.aroma,
                ),
            ).isEqualTo(radarBefore)
            assertThat(radarBefore).doesNotContain(null)

            val dao = db.activeBrewDao()
            val brew = dao.get(TimerSlot.BREW)!!
            assertThat(brew.sessionId).isEqualTo("live")
            assertThat(brew.phase).isEqualTo(TimerPhase.RUNNING)
            assertThat(brew.deadlineElapsedMs).isEqualTo(DEADLINE)
            assertThat(brew.preAlertElapsedMs).isEqualTo(DEADLINE - 20_000)
            val quick = dao.get(TimerSlot.QUICK)!!
            assertThat(quick.isQuickTimer).isTrue()
            assertThat(quick.quickPreAlertSeconds).isEqualTo(20)
            assertThat(db.sessionDao().steps("live").map { it.preAlertSeconds }).containsExactly(null, 20, 0).inOrder()
        } finally {
            db.close()
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v6 fixture: synthetic values only (D-016).

    private fun populateV6(db: SupportSQLiteDatabase) {
        populateCollection(db)
        populateJournal(db)
        populateTimers(db)
    }

    /** Two teas, a vessel, a recipe with a step, a stock purchase. */
    private fun populateCollection(db: SupportSQLiteDatabase) {
        listOf("tea-1", "tea-2").forEach { id ->
            db.insert(
                "tea",
                "id" to id, "name" to "Sencha $id", "category" to "GREEN", "form" to "LOOSE_LEAF",
                "is_flavored" to 0, "is_organic" to 1, "is_favorite" to 0, "is_archived" to 0, "created_at" to 1L,
                "updated_at" to 2L, "low_stock_warning" to 1,
            )
        }
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
            "is_default" to 1, "position" to 0, "source" to "CATALOG_TABLE", "created_at" to 7L, "updated_at" to 8L,
        )
        db.insert(
            "brew_step",
            "id" to "profile-1-step-0",
            "profile_id" to "profile-1",
            "position" to 0,
            "kind" to "INFUSION",
            "duration_seconds" to 60,
            "created_at" to 9L,
            "updated_at" to 9L,
        )
        db.insert(
            "inventory_event",
            "id" to "purchase-1", "tea_id" to "tea-1", "type" to "INITIAL", "delta_grams" to 100.0,
            "price_cents" to 1290L, "currency" to "EUR", "occurred_at" to 900_000L, "created_at" to 5L,
            "updated_at" to 5L,
        )
    }

    /** The finished sessions, each a case of 05 §9 „umamit.db v7". */
    private fun populateJournal(db: SupportSQLiteDatabase) {
        // All six values, a rinse first (ended), then two ended infusions and a never-started one.
        db.session("all-six", "COMPLETED", ALL_SIX, rating = 4.5, notes = "Süß")
        db.sessionStep("all-six", 0, kind = "RINSE", actual = 10)
        db.sessionStep("all-six", 1, actual = 61)
        db.sessionStep("all-six", 2, actual = 20)
        db.sessionStep("all-six", 3)
        db.insert(
            "session_descriptor",
            "session_id" to "all-six",
            "descriptor_key" to "marine.nori",
            "created_at" to 20L,
        )
        db.insert(
            "inventory_event",
            "id" to "brew-1", "tea_id" to "tea-1", "session_id" to "all-six", "type" to "BREW", "delta_grams" to -4.5,
            "occurred_at" to 1_000_061L, "created_at" to 6L, "updated_at" to 6L,
        )
        // Two values, two ended infusions.
        db.session("two", "COMPLETED", TWO)
        db.sessionStep("two", 0, actual = 60)
        db.sessionStep("two", 1, actual = 30)
        // No values.
        db.session("none", "COMPLETED", NO_TASTE)
        db.sessionStep("none", 0, actual = 60)
        // Values, but no step was ever brewed: the first step that is not a rinse (position 1, after the rinse).
        db.session("unbrewed", "COMPLETED", UNBREWED)
        db.sessionStep("unbrewed", 0, kind = "RINSE")
        db.sessionStep("unbrewed", 1)
        db.sessionStep("unbrewed", 2)
        // Values, and its only step is an ended rinse: the step at position 0.
        db.session("rinse-only", "COMPLETED", RINSE_ONLY)
        db.sessionStep("rinse-only", 0, kind = "RINSE", actual = 10)
        // Values, the 1st infusion skipped (0 s, `skipped = 1`), the 2nd and 3rd poured: the 2nd takes them.
        db.session("skipped-first", "COMPLETED", SKIPPED_FIRST)
        db.sessionStep("skipped-first", 0, actual = 0, startedAt = null, skipped = true)
        db.sessionStep("skipped-first", 1, actual = 60)
        db.sessionStep("skipped-first", 2, actual = 30)
        // Values and every step skipped: no step was poured, so the first that is not a rinse (position 0).
        db.session("all-skipped", "COMPLETED", ALL_SKIPPED)
        db.sessionStep("all-skipped", 0, actual = 0, startedAt = null, skipped = true)
        db.sessionStep("all-skipped", 1, actual = 0, startedAt = null, skipped = true)
        // Values and no step row (a v0.3.0 session completed without an ended step): left as it is.
        db.session("stepless", "COMPLETED", STEPLESS)
    }

    /** The brew session running in slot BREW (step 1 with its own 20 s) and a quick timer in QUICK. */
    private fun populateTimers(db: SupportSQLiteDatabase) {
        db.session("live", "IN_PROGRESS", NO_TASTE, ended = false)
        db.sessionStep("live", 0, actual = 60)
        db.sessionStep("live", 1, startedAt = WALL, preAlert = 20)
        db.sessionStep("live", 2, preAlert = 0)
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

    @Suppress("LongParameterList") // a session row's varying values
    private fun SupportSQLiteDatabase.session(
        id: String,
        status: String,
        taste: List<Long?>,
        rating: Double? = null,
        notes: String? = null,
        ended: Boolean = true,
    ) {
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to "tea-1", "tea_name" to "Sencha", "method" to "KYUSU",
            "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5, "water_ml" to 250,
            "base_temp_c" to 60, "pre_alert_seconds" to 10, "planned_step_count" to 3,
            "cost_cents" to if (ended) 58L else null, "started_at" to 1_000_000L,
            "ended_at" to if (ended) 1_000_300L else null, "zone_id" to "Europe/Berlin", "rating" to rating,
            *TASTE_COLUMNS.zip(taste).toTypedArray(),
            "notes" to notes, "auto_finished" to 0, "times_offered" to 0, "times_applied" to 0,
            "created_at" to 10L, "updated_at" to 11L,
        )
    }

    @Suppress("LongParameterList") // a step row's values
    private fun SupportSQLiteDatabase.sessionStep(
        sessionId: String,
        position: Int,
        kind: String = "INFUSION",
        actual: Int? = null,
        startedAt: Long? = actual?.let { 1_000_000L },
        preAlert: Int? = null,
        skipped: Boolean = false,
    ) {
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to kind, "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to actual?.let { 1_000_000L + it * 1000 },
            "skipped" to if (skipped) 1 else 0,
            "overtime_auto_ack" to 0, "note" to "Schritt $position", "created_at" to 12L, "updated_at" to 13L,
            "pre_alert_seconds" to preAlert,
        )
    }

    private companion object {
        const val NAME = "migrate-6-7.db"
        const val WALL = 1_761_120_000_000L
        const val DEADLINE = 5_060_000L

        val TASTE_COLUMNS = listOf(
            "taste_umami",
            "taste_sweetness",
            "taste_bitterness",
            "taste_astringency",
            "taste_body",
            "taste_aroma",
        )

        val ALL_SIX = listOf<Long?>(5, 4, 3, 2, 1, 0)
        val TWO = listOf<Long?>(2, 1, null, null, null, null)
        val UNBREWED = listOf<Long?>(1, null, null, null, 3, null)
        val RINSE_ONLY = listOf<Long?>(0, 1, null, null, null, null)
        val SKIPPED_FIRST = listOf<Long?>(3, null, 2, null, null, null)
        val ALL_SKIPPED = listOf<Long?>(null, null, null, 1, null, 4)
        val STEPLESS = listOf<Long?>(4, null, null, null, null, 2)
        val NO_TASTE = listOf<Long?>(null, null, null, null, null, null)

        val UNCHANGED_TABLES = listOf(
            "tea",
            "photo",
            "vessel",
            "brew_profile",
            "brew_step",
            "inventory_event",
            "catalog_bookmark",
            "active_brew",
            "session_descriptor",
        )

        /** A v6 `active_brew` row with every column: slot BREW, running, its pre-alert 20 s ahead, not yet played. */
        fun activeBrew(vararg fields: Pair<String, Any?>): Map<String, Any?> = linkedMapOf<String, Any?>(
            "slot" to "BREW", "session_id" to "live", "is_quick_timer" to 0, "quick_duration_seconds" to null,
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
