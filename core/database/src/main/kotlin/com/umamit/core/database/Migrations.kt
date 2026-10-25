package com.umamit.core.database

import androidx.room.migration.Migration

/**
 * Every migration of `umamit.db` (05 §9). [umamiDatabaseBuilder] registers exactly these, and
 * `SchemaVersionsTest` opens a database of every committed schema version with that builder,
 * so a version bump whose migration is missing here fails the unit tests. Empty while the
 * schema is at version 1.
 */
internal val UMAMI_MIGRATIONS: List<Migration> = emptyList()
