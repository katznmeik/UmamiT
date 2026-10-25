package com.umamit.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogFtsEntity
import com.umamit.core.database.entity.CatalogGuideEntity
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity

/**
 * The catalog database `catalog.db` (05 §1.6, §4), schema version 1: separate from the personal
 * `umamit.db`, never backed up (05 §8) and rebuilt by each refresh. Its schema is exported and
 * frozen from the first release candidate that ships it (`v0.2.0-rc.1`) like `umamit.db`'s
 * (05 §9): every version change ships a migration in [CATALOG_MIGRATIONS] and a test.
 */
@Database(
    entities = [
        CatalogTeaEntity::class,
        StagedCatalogTeaEntity::class,
        CatalogFtsEntity::class,
        CatalogPackageEntity::class,
        StagedCatalogPackageEntity::class,
        CatalogSkipEntity::class,
        CatalogGuideEntity::class,
        CatalogRefreshLogEntity::class,
    ],
    version = CatalogDatabase.VERSION,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "catalog.db"
        const val VERSION = 1
    }
}

/**
 * Every migration of `catalog.db` (05 §9). [catalogDatabaseBuilder] registers exactly these, and
 * `CatalogSchemaVersionsTest` opens a database of every committed schema version with it. Empty
 * while the schema is at version 1.
 */
internal val CATALOG_MIGRATIONS: List<Migration> = emptyList()

/** The builder of `catalog.db` as the app opens it, with every migration in [CATALOG_MIGRATIONS]. */
fun catalogDatabaseBuilder(
    context: Context,
    name: String = CatalogDatabase.NAME,
): RoomDatabase.Builder<CatalogDatabase> = Room.databaseBuilder(context, CatalogDatabase::class.java, name)
    .addMigrations(*CATALOG_MIGRATIONS.toTypedArray())
