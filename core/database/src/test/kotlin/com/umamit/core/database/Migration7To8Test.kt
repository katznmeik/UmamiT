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
 * `MIGRATION_7_8` (05 §9 „umamit.db v8", JRN-02, D-116 R3, D-117 Q10–Q12, #280): a populated database of the frozen
 * schema v7 migrates to v8. `brew_session` gains `brewing_rating` as its last column, a plain copy of `rating` (the
 * *Tee* rating keeps its values); nothing else changes. The fixture: a rated finished session, an unrated one, a half
 * star, a session of a deleted tea (no `tea_id`), a manual session, and a session in progress with its `active_brew`
 * row and a quick timer. Every row of every table is checked against what it was; Room's helper validates the result
 * against the exported v8 schema, and the production builder opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UmamiDatabase::class.java)

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every other table is unchanged`() {
        val before = helper.createDatabase(NAME, 7).use { db ->
            populateV7(db)
            UNCHANGED_TABLES.associateWith { db.dump(it) }
        }

        helper.runMigrationsAndValidate(NAME, 8, true, MIGRATION_7_8).use { db ->
            UNCHANGED_TABLES.forEach { table ->
                assertWithMessage(table).that(db.dump(table)).isEqualTo(before[table])
            }
        }
    }

    @Test
    fun `each session keeps every column and gains brewing_rating, a copy of its rating`() {
        val sessionsBefore = helper.createDatabase(NAME, 7).use { db ->
            populateV7(db)
            db.dump("brew_session")
        }
        assertThat(sessionsBefore.map { it["id"] }).containsExactlyElementsIn(RATINGS.keys)

        helper.runMigrationsAndValidate(NAME, 8, true, MIGRATION_7_8).use { db ->
            val after = db.dump("brew_session")
            assertThat(after).isEqualTo(sessionsBefore.map { it + ("brewing_rating" to it["rating"]) })
            assertThat(after.associate { it["id"] to it["brewing_rating"] }).isEqualTo(RATINGS)
            // The new column is the table's last one (05 §3.6).
            db.query("SELECT * FROM `brew_session` LIMIT 1").use { cursor ->
                assertThat(cursor.columnNames.last()).isEqualTo("brewing_rating")
            }
        }
    }

    @Test
    fun `the migrated database opens with the production builder, the timers keep their slots`() = runTest {
        helper.createDatabase(NAME, 7).use { db -> populateV7(db) }
        // An update keeps the install marker (05 §8): restore detection leaves the active timers alone.
        val marker = temp.newFile("marker")

        val db = umamiDatabaseBuilder(context(), NAME, marker, temp.newFolder("photos"))
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.openHelper.writableDatabase.version).isEqualTo(UmamiDatabase.VERSION)
            val rated = db.sessionDao().get("rated")!!
            assertThat(rated.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(rated.rating).isEqualTo(4.0)
            assertThat(rated.brewingRating).isEqualTo(4.0)
            assertThat(rated.notes).isEqualTo("Süß")
            assertThat(db.sessionDao().get("half")!!.brewingRating).isEqualTo(3.5)
            assertThat(db.sessionDao().get("unrated")!!.brewingRating).isNull()
            val orphan = db.sessionDao().get("orphan")!!
            assertThat(orphan.teaId).isNull()
            assertThat(orphan.brewingRating).isEqualTo(0.5)
            assertThat(db.sessionDao().steps("rated")[1].tasteUmami).isEqualTo(4)

            // The tea's Ø reads `rating` only and is what it was: (4,0 + 3,5 + 5,0) / 3 over tea-1's rated sessions.
            val teaRating = db.tastingDao().observeTeaRating("tea-1").first()!!
            assertThat(teaRating.average).isEqualTo(12.5 / 3)
            assertThat(teaRating.count).isEqualTo(3)
            val brewing = db.tastingDao().observeTeaBrewingRating("tea-1").first()!!
            assertThat(brewing.average).isEqualTo(teaRating.average)
            assertThat(brewing.count).isEqualTo(teaRating.count)

            val live = db.sessionDao().get("live")!!
            assertThat(live.status).isEqualTo(SessionStatus.IN_PROGRESS)
            assertThat(live.rating).isNull()
            assertThat(live.brewingRating).isNull()
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

    // --- The v7 fixture: synthetic values only (D-016).

    private fun populateV7(db: SupportSQLiteDatabase) {
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

    /** The finished sessions, each a case of 05 §9 „umamit.db v8". */
    private fun populateJournal(db: SupportSQLiteDatabase) {
        // Rated, with a rinse, an infusion's taste and note, a descriptor and its stock event.
        db.session("rated", "COMPLETED", rating = 4.0, notes = "Süß")
        db.sessionStep("rated", 0, kind = "RINSE", actual = 10)
        db.sessionStep("rated", 1, actual = 61, umami = 4, note = "Kräftig")
        db.sessionStep("rated", 2)
        db.insert(
            "session_descriptor",
            "session_id" to "rated",
            "descriptor_key" to "marine.nori",
            "created_at" to 20L,
        )
        db.insert(
            "inventory_event",
            "id" to "brew-1", "tea_id" to "tea-1", "session_id" to "rated", "type" to "BREW", "delta_grams" to -4.5,
            "occurred_at" to 1_000_061L, "created_at" to 6L, "updated_at" to 6L,
        )
        // Unrated.
        db.session("unrated", "COMPLETED")
        db.sessionStep("unrated", 0, actual = 60)
        // A half star.
        db.session("half", "COMPLETED", rating = 3.5)
        db.sessionStep("half", 0, actual = 60)
        // A manual session, rated 5,0.
        db.session("manual", "MANUAL", rating = 5.0)
        // A session of a deleted tea: its soft link cleared, its snapshot kept.
        db.session("orphan", "COMPLETED", teaId = null, rating = 0.5)
        db.sessionStep("orphan", 0, actual = 60)
        // Another tea's rated session.
        db.session("other-tea", "COMPLETED", teaId = "tea-2", rating = 2.0)
    }

    /** The brew session running in slot BREW (step 1 with its own 20 s) and a quick timer in QUICK. */
    private fun populateTimers(db: SupportSQLiteDatabase) {
        db.session("live", "IN_PROGRESS", ended = false)
        db.sessionStep("live", 0, actual = 60, umami = 3)
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
        teaId: String? = "tea-1",
        rating: Double? = null,
        notes: String? = null,
        ended: Boolean = true,
    ) {
        insert(
            "brew_session",
            "id" to id, "status" to status, "tea_id" to teaId, "tea_name" to "Sencha", "method" to "KYUSU",
            "vessel_id" to "vessel-1", "vessel_name" to "Kyusu", "leaf_grams" to 4.5, "water_ml" to 250,
            "base_temp_c" to 60, "pre_alert_seconds" to 10, "planned_step_count" to 3,
            "cost_cents" to if (ended) 58L else null, "started_at" to 1_000_000L,
            "ended_at" to if (ended) 1_000_300L else null, "zone_id" to "Europe/Berlin", "rating" to rating,
            "taste_umami" to null, "taste_sweetness" to null, "taste_bitterness" to null,
            "taste_astringency" to null, "taste_body" to null, "taste_aroma" to null,
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
        umami: Int? = null,
        note: String? = null,
    ) {
        insert(
            "session_step",
            "id" to "$sessionId-step-$position", "session_id" to sessionId, "position" to position,
            "kind" to kind, "planned_seconds" to 60, "actual_seconds" to actual,
            "paused_seconds" to actual?.let { 0 }, "overtime_seconds" to actual?.let { 1 }, "temp_c" to 60,
            "started_at" to startedAt, "ended_at" to actual?.let { 1_000_000L + it * 1000 }, "skipped" to 0,
            "overtime_auto_ack" to 0, "note" to "Schritt $position", "created_at" to 12L, "updated_at" to 13L,
            "pre_alert_seconds" to preAlert, "taste_umami" to umami, "taste_sweetness" to null,
            "taste_bitterness" to null, "taste_astringency" to null, "taste_body" to null, "taste_aroma" to null,
            "tasting_note" to note,
        )
    }

    private companion object {
        const val NAME = "migrate-7-8.db"
        const val WALL = 1_761_120_000_000L
        const val DEADLINE = 5_060_000L

        /** Each session's `brewing_rating` after the migration: its `rating`. */
        val RATINGS: Map<Any?, Any?> = mapOf(
            "rated" to 4.0,
            "unrated" to null,
            "half" to 3.5,
            "manual" to 5.0,
            "orphan" to 0.5,
            "other-tea" to 2.0,
            "live" to null,
        )

        val UNCHANGED_TABLES = listOf(
            "tea",
            "photo",
            "vessel",
            "brew_profile",
            "brew_step",
            "session_step",
            "inventory_event",
            "catalog_bookmark",
            "active_brew",
            "session_descriptor",
        )

        /** A v7 `active_brew` row with every column: slot BREW, running, its pre-alert 20 s ahead, not yet played. */
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
