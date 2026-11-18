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
import com.umamit.core.model.TeaCategory
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
 * `MIGRATION_8_9` (05 §9 „umamit.db v9", JRN-03, TEA-02, D-128, #320): a populated database of the frozen schema v8
 * migrates to v9. `tea` gains `taste_set` as its last column, `session_step` gains `taste_roast`, `taste_acidity`,
 * `taste_freshness` and `taste_spice` as its last columns, all null; no row is rewritten. The fixture: a green and a
 * herbal tea, each with a finished session that has all six taste values and an infusion note, a session of a deleted
 * tea (no `tea_id`), and a session in progress with its `active_brew` row and a quick timer. Every row of every table
 * is checked against what it was; Room's helper validates the result against the exported v9 schema, and the
 * production builder opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every other table is unchanged`() {
        val before = helper.createDatabase(NAME, 8).use { db ->
            populateV8(db)
            UNCHANGED_TABLES.associateWith { db.dump(it) }
        }

        helper.runMigrationsAndValidate(NAME, 9, true, MIGRATION_8_9).use { db ->
            UNCHANGED_TABLES.forEach { table ->
                assertWithMessage(table).that(db.dump(table)).isEqualTo(before[table])
            }
        }
    }

    @Test
    fun `each tea keeps every column and gains taste_set, null, as its last column`() {
        val teasBefore = helper.createDatabase(NAME, 8).use { db ->
            populateV8(db)
            db.dump("tea")
        }
        assertThat(teasBefore.map { it["id"] }).containsExactly("tea-green", "tea-herbal").inOrder()

        helper.runMigrationsAndValidate(NAME, 9, true, MIGRATION_8_9).use { db ->
            assertThat(db.dump("tea")).isEqualTo(teasBefore.map { it + ("taste_set" to null) })
            db.query("SELECT * FROM `tea` LIMIT 1").use { cursor ->
                assertThat(cursor.columnNames.last()).isEqualTo("taste_set")
            }
        }
    }

    @Test
    fun `each step keeps every value and gains the four taste columns, null, as its last columns`() {
        val stepsBefore = helper.createDatabase(NAME, 8).use { db ->
            populateV8(db)
            db.dump("session_step")
        }
        // The six v7 taste columns are filled on both teas' sessions, and the orphan's.
        val tasted = stepsBefore.filter { it["taste_umami"] != null }.map { it["id"] }
        assertThat(tasted).containsExactly("green-step-1", "herbal-step-0", "live-step-0", "orphan-step-0")

        helper.runMigrationsAndValidate(NAME, 9, true, MIGRATION_8_9).use { db ->
            assertThat(db.dump("session_step")).isEqualTo(
                stepsBefore.map {
                    it + NEW_STEP_COLUMNS.associateWith { null }
                },
            )
            db.query("SELECT * FROM `session_step` LIMIT 1").use { cursor ->
                assertThat(cursor.columnNames.takeLast(NEW_STEP_COLUMNS.size)).isEqualTo(NEW_STEP_COLUMNS)
            }
        }
    }

    @Test
    fun `the migrated database opens with the production builder, the timers keep their slots`() = runTest {
        helper.createDatabase(NAME, 8).use { db -> populateV8(db) }
        // An update keeps the install marker (05 §8): restore detection leaves the active timers alone.
        val marker = temp.newFile("marker")

        val db = umamiDatabaseBuilder(context(), NAME, marker, temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            val green = db.teaDao().get("tea-green")!!
            assertThat(green.category).isEqualTo(TeaCategory.GREEN)
            assertThat(green.tasteSet).isNull()
            val herbal = db.teaDao().get("tea-herbal")!!
            assertThat(herbal.category).isEqualTo(TeaCategory.HERBAL)
            assertThat(herbal.tasteSet).isNull()

            // Every value of the six v7 columns and the note is kept, also on the herbal tea's session (D-128 Q16).
            listOf("green" to 1, "herbal" to 0, "orphan" to 0).forEach { (id, position) ->
                val step = db.sessionDao().steps(id)[position]
                assertWithMessage(id).that(
                    listOf(
                        step.tasteUmami,
                        step.tasteSweetness,
                        step.tasteBitterness,
                        step.tasteAstringency,
                        step.tasteBody,
                        step.tasteAroma,
                    ),
                ).containsExactly(1, 2, 3, 4, 5, 0).inOrder()
                assertWithMessage(id).that(step.tastingNote).isEqualTo("Aufguss $id")
                assertWithMessage(id).that(
                    listOf(step.tasteRoast, step.tasteAcidity, step.tasteFreshness, step.tasteSpice),
                ).containsExactly(null, null, null, null)
            }
            val herbalSession = db.sessionDao().get("herbal")!!
            assertThat(herbalSession.rating).isEqualTo(3.5)
            assertThat(herbalSession.brewingRating).isEqualTo(4.0)
            assertThat(db.sessionDao().get("orphan")!!.teaId).isNull()

            val live = db.sessionDao().get("live")!!
            assertThat(live.status).isEqualTo(SessionStatus.IN_PROGRESS)
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

    @Test
    fun `the migrated reads see every value, each tea's category and no taste_set`() = runTest {
        helper.createDatabase(NAME, 8).use { db -> populateV8(db) }
        val db = umamiDatabaseBuilder(context(), NAME, temp.newFile("marker"), temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            // The averages read every column; the four new ones have no value yet.
            val averages = db.tastingDao().observeTasteAverages("tea-herbal").first()
            assertThat(averages.umami).isEqualTo(1.0)
            assertThat(averages.aroma).isEqualTo(0.0)
            assertThat(listOf(averages.roast, averages.acidity, averages.freshness, averages.spice))
                .containsExactly(null, null, null, null)
            val set = db.tastingDao().observeTeaTasteSet("tea-herbal").first()!!
            assertThat(set.category).isEqualTo(TeaCategory.HERBAL)
            assertThat(set.tasteSet).isNull()

            val journal = db.journalDao().observeSession("herbal").first()!!
            assertThat(journal.teaCategory).isEqualTo(TeaCategory.HERBAL)
            assertThat(journal.teaTasteSet).isNull()
            assertThat(db.journalDao().observeSession("orphan").first()!!.teaCategory).isNull()
        } finally {
            db.close()
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- The v8 fixture: synthetic values only (D-016).

    private fun populateV8(db: SupportSQLiteDatabase) {
        populateCollection(db)
        populateJournal(db)
        populateTimers(db)
    }

    /** A green and a herbal tea, a vessel, a recipe with a step, a stock purchase. */
    private fun populateCollection(db: SupportSQLiteDatabase) {
        listOf("tea-green" to "GREEN", "tea-herbal" to "HERBAL").forEach { (id, category) ->
            db.insert(
                "tea",
                "id" to id, "name" to "Tee $id", "category" to category, "form" to "LOOSE_LEAF",
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
            "id" to "profile-1", "tea_id" to "tea-green", "name" to "Rezept", "method" to "KYUSU",
            "leaf_grams" to 4.0, "water_ml" to 250, "base_temp_c" to 60, "vessel_id" to "vessel-1",
            "pre_alert_seconds" to 15, "is_default" to 1, "position" to 0, "source" to "CATALOG_TABLE",
            "created_at" to 7L, "updated_at" to 8L,
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
            "id" to "purchase-1", "tea_id" to "tea-green", "type" to "INITIAL", "delta_grams" to 100.0,
            "price_cents" to 1290L, "currency" to "EUR", "occurred_at" to 900_000L, "created_at" to 5L,
            "updated_at" to 5L,
        )
    }

    /** The finished sessions, each a case of 05 §9 „umamit.db v9". */
    private fun populateJournal(db: SupportSQLiteDatabase) {
        // The green tea's: a rinse, an infusion with all six values and a note, a descriptor and its stock event.
        db.session("green", "COMPLETED", teaId = "tea-green", category = "GREEN", rating = 4.0)
        db.sessionStep("green", 0, kind = "RINSE", actual = 10)
        db.sessionStep("green", 1, actual = 61, tasted = true)
        db.sessionStep("green", 2)
        db.insert(
            "session_descriptor",
            "session_id" to "green",
            "descriptor_key" to "marine.nori",
            "created_at" to 20L,
        )
        db.insert(
            "inventory_event",
            "id" to "brew-1", "tea_id" to "tea-green", "session_id" to "green", "type" to "BREW",
            "delta_grams" to -4.5, "occurred_at" to 1_000_061L, "created_at" to 6L, "updated_at" to 6L,
        )
        // The herbal tea's: the six values rated before the update, Umami among them.
        db.session("herbal", "COMPLETED", teaId = "tea-herbal", category = "HERBAL", rating = 3.5, brewing = 4.0)
        db.sessionStep("herbal", 0, actual = 60, tasted = true)
        // A session of a deleted tea: its soft link cleared, its snapshot kept.
        db.session("orphan", "COMPLETED", teaId = null, category = "HERBAL", rating = 0.5)
        db.sessionStep("orphan", 0, actual = 60, tasted = true)
    }

    /** The brew session running in slot BREW (step 1 with its own 20 s) and a quick timer in QUICK. */
    private fun populateTimers(db: SupportSQLiteDatabase) {
        db.session("live", "IN_PROGRESS", teaId = "tea-green", category = "GREEN", ended = false)
        db.sessionStep("live", 0, actual = 60, tasted = true)
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
        teaId: String?,
        category: String,
        rating: Double? = null,
        brewing: Double? = rating,
        ended: Boolean = true,
    ) {
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to teaId, "tea_name" to "Tee $id", "category" to category,
            "method" to "KYUSU", "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5,
            "water_ml" to 250, "base_temp_c" to 60, "pre_alert_seconds" to 10, "planned_step_count" to 3,
            "cost_cents" to if (ended) 58L else null, "started_at" to 1_000_000L,
            "ended_at" to if (ended) 1_000_300L else null, "zone_id" to "Europe/Berlin", "rating" to rating,
            "taste_umami" to null, "taste_sweetness" to null, "taste_bitterness" to null,
            "taste_astringency" to null, "taste_body" to null, "taste_aroma" to null,
            "notes" to "Notizen $id", "auto_finished" to 0, "times_offered" to 0, "times_applied" to 0,
            "created_at" to 10L, "updated_at" to 11L, "brewing_rating" to brewing,
        )
    }

    /** A step row; [tasted] sets all six v7 taste values (1, 2, 3, 4, 5, 0) and the note „Aufguss <session>". */
    @Suppress("LongParameterList") // a step row's values
    private fun SupportSQLiteDatabase.sessionStep(
        sessionId: String,
        position: Int,
        kind: String = "INFUSION",
        actual: Int? = null,
        startedAt: Long? = actual?.let { 1_000_000L },
        preAlert: Int? = null,
        tasted: Boolean = false,
    ) {
        fun taste(value: Int) = if (tasted) value else null
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to kind, "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to actual?.let { 1_000_000L + it * 1000 }, "skipped" to 0,
            "overtime_auto_ack" to 0, "note" to "Schritt $position", "created_at" to 12L, "updated_at" to 13L,
            "pre_alert_seconds" to preAlert, "taste_umami" to taste(1), "taste_sweetness" to taste(2),
            "taste_bitterness" to taste(3), "taste_astringency" to taste(4), "taste_body" to taste(5),
            "taste_aroma" to taste(0), "tasting_note" to if (tasted) "Aufguss $sessionId" else null,
        )
    }

    private companion object {
        const val NAME = "migrate-8-9.db"
        const val WALL = 1_761_120_000_000L
        const val DEADLINE = 5_060_000L

        /** The four columns v9 appends to `session_step`, in their order (05 §9). */
        val NEW_STEP_COLUMNS = listOf("taste_roast", "taste_acidity", "taste_freshness", "taste_spice")

        val UNCHANGED_TABLES = listOf(
            "photo",
            "vessel",
            "brew_profile",
            "brew_step",
            "brew_session",
            "inventory_event",
            "catalog_bookmark",
            "active_brew",
            "session_descriptor",
        )

        /** A v8 `active_brew` row with every column: slot BREW, running, its pre-alert 20 s ahead, not yet played. */
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
