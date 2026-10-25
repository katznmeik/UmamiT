package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import kotlinx.coroutines.flow.Flow

/** A catalog list row (SCR-19): what the list shows of a tea. */
data class CatalogListRow(
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "secondary_name") val secondaryName: String?,
    @ColumnInfo(name = "tea_type") val teaType: String?,
    @ColumnInfo(name = "base_price_cents_per_100g") val basePriceCentsPer100g: Long?,
    @ColumnInfo(name = "available") val available: Boolean?,
)

/**
 * The catalog database's queries (05 §4). A refresh stages its teas, packages and skip entries
 * and then swaps them in with [swap], one transaction (05 §4.5): the old catalog is either
 * replaced as a whole or not touched.
 */
@Dao
@Suppress("TooManyFunctions") // one query per step of the refresh and its swap (05 §4.5)
abstract class CatalogDao {
    /** The teas in the shop's current range (not discontinued), by name. */
    @Query(
        "SELECT key, name, secondary_name, tea_type, base_price_cents_per_100g, available FROM catalog_tea " +
            "WHERE discontinued = 0 ORDER BY name, key",
    )
    abstract fun observeList(): Flow<List<CatalogListRow>>

    /** When the last successful refresh finished (CAT-07 AC6), or null before the first one. */
    @Query("SELECT MAX(finished_at) FROM catalog_refresh_log WHERE status = 'SUCCESS'")
    abstract fun observeLastSuccess(): Flow<Long?>

    /** The green teas in the current catalog (08 §2.9's previous count). */
    @Query("SELECT COUNT(*) FROM catalog_tea WHERE discontinued = 0")
    abstract suspend fun countCurrent(): Int

    @Query("SELECT key FROM catalog_tea")
    abstract suspend fun keys(): List<String>

    /** Base numbers classified out of scope at or after [since] (05 §4.2a: re-checked after 90 days). */
    @Query("SELECT base_key FROM catalog_skip WHERE checked_at >= :since")
    abstract suspend fun skippedSince(since: Long): List<String>

    @Query("SELECT * FROM catalog_skip ORDER BY base_key")
    abstract suspend fun skipList(): List<CatalogSkipEntity>

    @Query("SELECT * FROM catalog_refresh_log ORDER BY id")
    abstract suspend fun refreshLog(): List<CatalogRefreshLogEntity>

    /** Empties the staging tables, before a refresh and after a failed one. */
    @Transaction
    open suspend fun clearStaging() {
        clearStagedPackages()
        clearStagedTeas()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun stage(tea: StagedCatalogTeaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun stagePackages(packages: List<StagedCatalogPackageEntity>)

    @Query("SELECT COUNT(*) FROM catalog_tea_staging")
    abstract suspend fun countStaged(): Int

    @Query("SELECT key FROM catalog_tea_staging")
    abstract suspend fun stagedKeys(): List<String>

    /** Writes a refresh that ended without a swap (cancelled or failed, 05 §4.4). */
    @Insert
    abstract suspend fun log(entry: CatalogRefreshLogEntity): Long

    /**
     * The atomic swap (05 §4.5): the staged teas and packages replace the catalog, a tea seen
     * before keeps its `first_seen_at` („Neu"), [skips] are added to the skip list, [entry] is
     * logged and the staging tables are emptied, all in one transaction.
     */
    @Transaction
    open suspend fun swap(skips: List<CatalogSkipEntity>, entry: CatalogRefreshLogEntity) {
        keepFirstSeen()
        deletePackages()
        deleteTeas()
        insertStagedTeas()
        insertStagedPackages()
        putSkips(skips)
        log(entry)
        clearStagedPackages()
        clearStagedTeas()
    }

    @Query(
        "UPDATE catalog_tea_staging SET first_seen_at = " +
            "(SELECT t.first_seen_at FROM catalog_tea t WHERE t.key = catalog_tea_staging.key) " +
            "WHERE key IN (SELECT key FROM catalog_tea)",
    )
    protected abstract suspend fun keepFirstSeen()

    @Query("DELETE FROM catalog_package")
    protected abstract suspend fun deletePackages()

    @Query("DELETE FROM catalog_tea")
    protected abstract suspend fun deleteTeas()

    // Same columns in the same order: the staging entities embed the live ones (CatalogSchemaTest).
    @Query("INSERT INTO catalog_tea SELECT * FROM catalog_tea_staging")
    protected abstract suspend fun insertStagedTeas()

    @Query("INSERT INTO catalog_package SELECT * FROM catalog_package_staging")
    protected abstract suspend fun insertStagedPackages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putSkips(skips: List<CatalogSkipEntity>)

    @Query("DELETE FROM catalog_tea_staging")
    protected abstract suspend fun clearStagedTeas()

    @Query("DELETE FROM catalog_package_staging")
    protected abstract suspend fun clearStagedPackages()
}
