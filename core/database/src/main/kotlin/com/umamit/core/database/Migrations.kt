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
 * Every migration of `umamit.db` (05 §9). [umamiDatabaseBuilder] registers exactly these, and
 * `SchemaVersionsTest` opens a database of every committed schema version with that builder,
 * so a version bump whose migration is missing here fails the unit tests.
 */
internal val UMAMI_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2)
