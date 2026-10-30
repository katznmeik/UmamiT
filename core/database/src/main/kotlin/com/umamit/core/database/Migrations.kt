package com.umamit.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 1 → 2 (v0.2.1, TMR-23, D-093, D-096): the pre-alert per step. `brew_step` and `session_step` gain
 * `pre_alert_seconds` (null = *Automatisch*), so every existing step reads as *Automatisch*. The active
 * session's steps that have not ended (the current step and the pending ones) get the lead v1 gave them, as an
 * explicit value: its own choice, `active_brew.pre_alert_override_seconds`, which v1 held for the whole session,
 * or else the inherited B, `active_brew.pre_alert_inherited_seconds`, which v1 applied to every step (0 = *Aus*).
 * So a session running during the update keeps the same leads, a 15–29 s step too, and its running step's ring
 * and Live Update point match its stored tick (rule 8). Then the override column is dropped (SQLite ≥ 3.35;
 * API 35 ships 3.44). Room runs it in one transaction: it applies whole or not at all, and the database is never
 * opened destructively (05 §9, D-093 Q16).
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `brew_step` ADD COLUMN `pre_alert_seconds` INTEGER")
        db.execSQL("ALTER TABLE `session_step` ADD COLUMN `pre_alert_seconds` INTEGER")
        db.execSQL(
            "UPDATE `session_step` SET `pre_alert_seconds` = (" +
                "SELECT COALESCE(`pre_alert_override_seconds`, `pre_alert_inherited_seconds`) FROM `active_brew` " +
                "WHERE `active_brew`.`session_id` = `session_step`.`session_id`" +
                ") WHERE `actual_seconds` IS NULL AND `session_id` IN (" +
                "SELECT `session_id` FROM `active_brew` WHERE `session_id` IS NOT NULL" +
                ")",
        )
        db.execSQL("ALTER TABLE `active_brew` DROP COLUMN `pre_alert_override_seconds`")
    }
}

/**
 * Schema 2 → 3 (v0.2.2, CAT-18, TMR-22, D-097): the one schema step of v0.2.2. The Merkliste `catalog_bookmark`
 * (05 §3.13) is created empty, and `active_brew` gains `quick_pre_alert_seconds` as its last column (null =
 * *Automatisch*), so a quick timer running during the update keeps the lead it ran with in v0.2.1, the automatic
 * rule on its frozen B (rule 8). No row changes. Room runs it in one transaction: it applies whole or not at all,
 * and the database is never opened destructively (05 §9).
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_bookmark` (`catalog_key` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`catalog_key`))",
        )
        db.execSQL("ALTER TABLE `active_brew` ADD COLUMN `quick_pre_alert_seconds` INTEGER")
    }
}

/**
 * Schema 3 → 4 (v0.3.0, TMR-21, TMR-22, D-098): `active_brew` holds one row per timer slot, keyed by `slot`
 * (`BREW` or `QUICK`) in place of `id` = 1, and gains `alert_ringing_since_elapsed_ms` and
 * `alert_ringing_since_wall_ms` (05 §3.12). SQLite cannot change a primary key in place, so the table is recreated
 * as Room does: create the new table, copy, drop the old one, rename it, recreate its index. The one v3 row, if
 * any, goes to the slot of its kind (`is_quick_timer` 1 → `QUICK`, else `BREW`) with every value as it was; an
 * ALERTING row rang from its deadline in v0.2.2, so its ringing start is its deadline (both clocks), every other
 * row has none. So a timer running, waiting or alerting during the update keeps its state and alerts on time
 * (rule 8, 04 §7.5). No other table changes; the foreign key to `brew_session` stays (CASCADE). Room runs it in one
 * transaction: it applies whole or not at all, and the database is never opened destructively (05 §9).
 */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_active_brew` (`slot` TEXT NOT NULL, " +
                "$ACTIVE_BREW_V3_COLUMN_DEFINITIONS, " +
                "`alert_ringing_since_elapsed_ms` INTEGER, `alert_ringing_since_wall_ms` INTEGER, " +
                "PRIMARY KEY(`slot`), FOREIGN KEY(`session_id`) REFERENCES `brew_session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `_new_active_brew` (`slot`, $ACTIVE_BREW_V3_COLUMNS, " +
                "`alert_ringing_since_elapsed_ms`, `alert_ringing_since_wall_ms`) " +
                "SELECT CASE `is_quick_timer` WHEN 1 THEN 'QUICK' ELSE 'BREW' END, $ACTIVE_BREW_V3_COLUMNS, " +
                "CASE `phase` WHEN 'ALERTING' THEN `deadline_elapsed_ms` END, " +
                "CASE `phase` WHEN 'ALERTING' THEN `deadline_wall_ms` END " +
                "FROM `active_brew`",
        )
        db.execSQL("DROP TABLE `active_brew`")
        db.execSQL("ALTER TABLE `_new_active_brew` RENAME TO `active_brew`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_active_brew_session_id` ON `active_brew` (`session_id`)")
    }
}

/** The v3 columns of `active_brew` that v4 keeps as they are (all but `id`), with their types, in their order. */
private val ACTIVE_BREW_V3_KEPT = listOf(
    "session_id" to "TEXT",
    "is_quick_timer" to "INTEGER NOT NULL",
    "quick_duration_seconds" to "INTEGER",
    "step_index" to "INTEGER NOT NULL",
    "phase" to "TEXT NOT NULL",
    "deadline_elapsed_ms" to "INTEGER",
    "deadline_wall_ms" to "INTEGER",
    "pre_alert_elapsed_ms" to "INTEGER",
    "pre_alert_played" to "INTEGER NOT NULL",
    "remaining_ms" to "INTEGER",
    "paused_total_ms" to "INTEGER NOT NULL",
    "paused_since_elapsed_ms" to "INTEGER",
    "paused_since_wall_ms" to "INTEGER",
    "ready_since_wall_ms" to "INTEGER",
    "step_started_wall_ms" to "INTEGER",
    "alert_played" to "INTEGER NOT NULL",
    "boot_count" to "INTEGER NOT NULL",
    "last_interaction_wall_ms" to "INTEGER NOT NULL",
    "demoted" to "INTEGER NOT NULL",
    "pre_alert_inherited_seconds" to "INTEGER NOT NULL",
    "zone_id" to "TEXT NOT NULL",
    "created_at" to "INTEGER NOT NULL",
    "updated_at" to "INTEGER NOT NULL",
    "quick_pre_alert_seconds" to "INTEGER",
)

private val ACTIVE_BREW_V3_COLUMNS = ACTIVE_BREW_V3_KEPT.joinToString { (name, _) -> "`$name`" }

private val ACTIVE_BREW_V3_COLUMN_DEFINITIONS = ACTIVE_BREW_V3_KEPT.joinToString { (name, type) -> "`$name` $type" }

/**
 * Every migration of `umamit.db` (05 §9). [umamiDatabaseBuilder] registers exactly these, and
 * `SchemaVersionsTest` opens a database of every committed schema version with that builder,
 * so a version bump whose migration is missing here fails the unit tests.
 */
internal val UMAMI_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
