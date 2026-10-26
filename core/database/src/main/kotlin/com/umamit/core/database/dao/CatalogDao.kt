package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.umamit.core.database.entity.CatalogGuideEntity
import com.umamit.core.database.entity.CatalogImageFailureEntity
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogGuideEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.RecipeCheckReason
import kotlinx.coroutines.flow.Flow

/** A catalog list row (SCR-19): what the list shows of a tea. */
data class CatalogListRow(
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "secondary_name") val secondaryName: String?,
    @ColumnInfo(name = "tea_type") val teaType: String?,
    @ColumnInfo(name = "base_price_cents_per_100g") val basePriceCentsPer100g: Long?,
    @ColumnInfo(name = "available") val available: Boolean?,
    @ColumnInfo(name = "photo_file") val photoFile: String?,
    @ColumnInfo(name = "origin_country") val originCountry: String? = null,
    @ColumnInfo(name = "is_organic") val isOrganic: Boolean = false,
    @ColumnInfo(name = "is_flavored") val isFlavored: Boolean = false,
    @ColumnInfo(name = "taste_scales_json") val tasteScalesJson: String? = null,
    @ColumnInfo(name = "recipe_check_reason") val recipeCheckReason: RecipeCheckReason? = null,
    /** First seen in the latest successful refresh, and that refresh was not the first (CAT-10 „Neu", D-083). */
    @ColumnInfo(name = "is_new") val isNew: Boolean = false,
)

/**
 * What a later refresh needs to know of a tea in the catalog before its swap (#70): its photo's
 * source and file (CAT-08), its shop content's hash („geändert"), its recipe flag („Rezepte
 * prüfen") and whether it already left the range (CAT-07 AC4).
 */
data class CatalogTeaState(
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    @ColumnInfo(name = "photo_file") val photoFile: String?,
    @ColumnInfo(name = "content_hash") val contentHash: String?,
    @ColumnInfo(name = "recipe_check_reason") val recipeCheckReason: RecipeCheckReason?,
    @ColumnInfo(name = "discontinued") val discontinued: Boolean,
)

/** A package's article number (05 §4.2), which names the base number its own page has (D-081). */
data class CatalogPackageNumber(
    @ColumnInfo(name = "catalog_key") val catalogKey: String,
    @ColumnInfo(name = "article_number") val articleNumber: String,
)

/**
 * The catalog database's queries (05 §4). A refresh stages its teas, packages and skip entries
 * and then swaps them in with [swap], one transaction (05 §4.5): the old catalog is either
 * replaced as a whole or not touched.
 */
@Dao
@Suppress("TooManyFunctions") // one query per step of the refresh and its swap (05 §4.5)
abstract class CatalogDao {
    /**
     * The teas in the shop's current range (not discontinued), by name. A tea is „Neu" (CAT-10,
     * D-083) when its first sighting is the start of the latest successful refresh and an earlier
     * successful refresh exists: the first refresh finds every tea, so none is new then.
     */
    @Query(
        "SELECT key, name, secondary_name, tea_type, base_price_cents_per_100g, available, photo_file, " +
            "origin_country, is_organic, is_flavored, taste_scales_json, recipe_check_reason, " +
            "(first_seen_at = (SELECT l.started_at FROM catalog_refresh_log l WHERE l.status = 'SUCCESS' " +
            "ORDER BY l.finished_at DESC, l.id DESC LIMIT 1) " +
            "AND EXISTS (SELECT 1 FROM catalog_refresh_log e WHERE e.status = 'SUCCESS' " +
            "AND e.started_at < catalog_tea.first_seen_at)) AS is_new " +
            "FROM catalog_tea " +
            "WHERE discontinued = 0 ORDER BY name, key",
    )
    abstract fun observeList(): Flow<List<CatalogListRow>>

    /**
     * The keys of the teas whose name, secondary name, description, cultivar or region match
     * [match], an FTS4 query (05 §4.1 `catalog_fts`, CAT-10).
     */
    @Query(
        "SELECT catalog_tea.key FROM catalog_tea JOIN catalog_fts ON catalog_tea.rowid = catalog_fts.rowid " +
            "WHERE catalog_fts MATCH :match",
    )
    abstract fun observeSearch(match: String): Flow<List<String>>

    /** One tea of the catalog (SCR-21), or null when the catalog has none with [key]. */
    @Query("SELECT * FROM catalog_tea WHERE key = :key")
    abstract fun observeTea(key: String): Flow<CatalogTeaEntity?>

    @Query("SELECT * FROM catalog_tea WHERE key = :key")
    abstract suspend fun tea(key: String): CatalogTeaEntity?

    /** A tea's package sizes, smallest first (05 §4.2). */
    @Query("SELECT * FROM catalog_package WHERE catalog_key = :key ORDER BY grams")
    abstract fun observePackages(key: String): Flow<List<CatalogPackageEntity>>

    /** When the last successful refresh finished (CAT-07 AC6), or null before the first one. */
    @Query("SELECT MAX(finished_at) FROM catalog_refresh_log WHERE status = 'SUCCESS'")
    abstract fun observeLastSuccess(): Flow<Long?>

    /** The green teas in the current catalog (08 §2.9's previous count). */
    @Query("SELECT COUNT(*) FROM catalog_tea WHERE discontinued = 0")
    abstract suspend fun countCurrent(): Int

    @Query("SELECT key FROM catalog_tea")
    abstract suspend fun keys(): List<String>

    /** Every tea of the catalog, discontinued ones included, as a later refresh compares it (#70). */
    @Query("SELECT key, photo_url, photo_file, content_hash, recipe_check_reason, discontinued FROM catalog_tea")
    abstract suspend fun teaStates(): List<CatalogTeaState>

    /** Every package's article number, by tea: the base numbers merged into a tea are among them (D-081). */
    @Query("SELECT catalog_key, article_number FROM catalog_package")
    abstract suspend fun packageNumbers(): List<CatalogPackageNumber>

    /** Base numbers classified out of scope at or after [since] (05 §4.2a: re-checked after 90 days). */
    @Query("SELECT base_key FROM catalog_skip WHERE checked_at >= :since")
    abstract suspend fun skippedSince(since: Long): List<String>

    @Query("SELECT * FROM catalog_skip ORDER BY base_key")
    abstract suspend fun skipList(): List<CatalogSkipEntity>

    @Query("SELECT * FROM catalog_refresh_log ORDER BY id")
    abstract suspend fun refreshLog(): List<CatalogRefreshLogEntity>

    /** The guides (*Ratgeber*, CAT-09, SCR-20). */
    @Query("SELECT * FROM catalog_guide")
    abstract fun observeGuides(): Flow<List<CatalogGuideEntity>>

    /** One guide, or null when the catalog has none with [slug]. */
    @Query("SELECT * FROM catalog_guide WHERE slug = :slug")
    abstract fun observeGuide(slug: String): Flow<CatalogGuideEntity?>

    @Query("SELECT * FROM catalog_guide")
    abstract suspend fun guides(): List<CatalogGuideEntity>

    /** The photo and guide image URLs that failed for good (05 §4.6, D-091). */
    @Query("SELECT * FROM catalog_image_failure")
    abstract suspend fun imageFailures(): List<CatalogImageFailureEntity>

    /** Empties the staging tables, before a refresh and after a failed one. */
    @Transaction
    open suspend fun clearStaging() {
        clearStagedPackages()
        clearStagedTeas()
        clearStagedGuides()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun stageGuide(guide: StagedCatalogGuideEntity)

    /** Carries the current guide [slug] over into the staging as it is (D-087); nothing when there is none. */
    @Query("INSERT OR REPLACE INTO catalog_guide_staging SELECT * FROM catalog_guide WHERE slug = :slug")
    abstract suspend fun stageCurrentGuide(slug: String)

    /** Carries every guide of the current catalog over into the staging (D-087). */
    @Query("INSERT OR REPLACE INTO catalog_guide_staging SELECT * FROM catalog_guide")
    abstract suspend fun stageCurrentGuides()

    @Query("SELECT COUNT(*) FROM catalog_guide_staging")
    abstract suspend fun countStagedGuides(): Int

    @Query("SELECT slug FROM catalog_guide_staging")
    abstract suspend fun stagedGuideSlugs(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun stage(tea: StagedCatalogTeaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun stagePackages(packages: List<StagedCatalogPackageEntity>)

    @Query("SELECT COUNT(*) FROM catalog_tea_staging")
    abstract suspend fun countStaged(): Int

    @Query("SELECT key FROM catalog_tea_staging")
    abstract suspend fun stagedKeys(): List<String>

    /**
     * Sets a staged tea's photo file and the URL that photo came from (05 §4.1 `photo_file`,
     * `photo_url`), once the refresh has its photo (CAT-08): downloaded now, or kept from before.
     */
    @Query("UPDATE catalog_tea_staging SET photo_file = :file, photo_url = :url WHERE key = :key")
    abstract suspend fun setStagedPhoto(key: String, file: String, url: String?)

    /** Writes a refresh that ended without a swap (cancelled or failed, 05 §4.4). */
    @Insert
    abstract suspend fun log(entry: CatalogRefreshLogEntity): Long

    /**
     * The atomic swap (05 §4.5): the staged teas, packages and guides replace the catalog, a tea
     * seen before keeps its `first_seen_at` („Neu"), [skips] are added to the skip list, [entry]
     * is logged and the staging tables are emptied, all in one transaction.
     *
     * Teas of the old catalog that the refresh did not stage (#70): those in [unchanged] stay as
     * they were (still in the sitemap, but their page couldn't be read this time, 08 §2.4 item 5);
     * those in [discontinued] stay hidden as „Nicht mehr im Sortiment" while a collection tea
     * links to them (CAT-07 AC4); every other one is deleted with its packages.
     *
     * [imageFailures], when given, replace the image URLs not to ask for again (05 §4.6, D-091).
     */
    @Transaction
    open suspend fun swap(
        skips: List<CatalogSkipEntity>,
        entry: CatalogRefreshLogEntity,
        unchanged: List<String> = emptyList(),
        discontinued: List<String> = emptyList(),
        imageFailures: List<CatalogImageFailureEntity>? = null,
    ) {
        stageCurrent(unchanged + discontinued)
        markStagedDiscontinued(discontinued)
        keepFirstSeen()
        deletePackages()
        deleteTeas()
        insertStagedTeas()
        insertStagedPackages()
        replaceGuides()
        putSkips(skips)
        if (imageFailures != null) {
            deleteImageFailures()
            putImageFailures(imageFailures)
        }
        log(entry)
        clearStagedPackages()
        clearStagedTeas()
    }

    /** The staged guides replace the guides (D-087); part of [swap]'s transaction. */
    @Transaction
    protected open suspend fun replaceGuides() {
        deleteGuides()
        insertStagedGuides()
        clearStagedGuides()
    }

    @Query("DELETE FROM catalog_guide")
    protected abstract suspend fun deleteGuides()

    @Query("INSERT INTO catalog_guide SELECT * FROM catalog_guide_staging")
    protected abstract suspend fun insertStagedGuides()

    @Query("DELETE FROM catalog_guide_staging")
    protected abstract suspend fun clearStagedGuides()
    private suspend fun stageCurrent(keys: List<String>) {
        if (keys.isEmpty()) return
        stageCurrentTeas(keys)
        stageCurrentPackages(keys)
    }

    // The staged page wins over the old row (never both: the refresh stages neither twice).
    @Query("INSERT OR IGNORE INTO catalog_tea_staging SELECT * FROM catalog_tea WHERE key IN (:keys)")
    protected abstract suspend fun stageCurrentTeas(keys: List<String>)

    @Query("INSERT OR IGNORE INTO catalog_package_staging SELECT * FROM catalog_package WHERE catalog_key IN (:keys)")
    protected abstract suspend fun stageCurrentPackages(keys: List<String>)

    @Query("UPDATE catalog_tea_staging SET discontinued = 1 WHERE key IN (:keys)")
    protected abstract suspend fun markStagedDiscontinued(keys: List<String>)

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

    @Query("DELETE FROM catalog_image_failure")
    protected abstract suspend fun deleteImageFailures()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putImageFailures(failures: List<CatalogImageFailureEntity>)

    @Query("DELETE FROM catalog_tea_staging")
    protected abstract suspend fun clearStagedTeas()

    @Query("DELETE FROM catalog_package_staging")
    protected abstract suspend fun clearStagedPackages()
}
