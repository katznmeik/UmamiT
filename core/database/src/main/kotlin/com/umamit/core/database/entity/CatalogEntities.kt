package com.umamit.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings
import com.umamit.core.model.RecipeCheckReason

// Catalog tables of 05 §4 in `catalog.db`, rebuilt by each refresh (CAT-07). Booleans are
// INTEGER 0/1, timestamps epoch ms UTC, enums by name (05 §5). A field the shop doesn't show
// stays null.

/** Table `catalog_tea` (05 §4.1): one entry per tea, keyed by the shop's base article number. */
@Entity(
    tableName = "catalog_tea",
    indices = [Index("tea_type"), Index("discontinued")],
)
data class CatalogTeaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String?,
    @ColumnInfo(name = "secondary_name") val secondaryName: String?,
    @ColumnInfo(name = "product_url") val productUrl: String,
    @ColumnInfo(name = "shop_category") val shopCategory: String?,
    @ColumnInfo(name = "tea_type") val teaType: String?,
    @ColumnInfo(name = "style_key") val styleKey: String? = null,
    @ColumnInfo(name = "origin_country") val originCountry: String? = null,
    @ColumnInfo(name = "origin_region") val originRegion: String? = null,
    @ColumnInfo(name = "cultivar") val cultivar: String? = null,
    @ColumnInfo(name = "harvest") val harvest: String? = null,
    @ColumnInfo(name = "shading") val shading: String? = null,
    @ColumnInfo(name = "steaming") val steaming: String? = null,
    @ColumnInfo(name = "is_organic") val isOrganic: Boolean = false,
    @ColumnInfo(name = "is_flavored") val isFlavored: Boolean = false,
    @ColumnInfo(name = "cold_brew_suitable") val coldBrewSuitable: Boolean = false,
    @ColumnInfo(name = "aroma") val aroma: String? = null,
    @ColumnInfo(name = "taste_notes") val tasteNotes: String? = null,
    @ColumnInfo(name = "manufacturer") val manufacturer: String? = null,
    @ColumnInfo(name = "season") val season: String? = null,
    @ColumnInfo(name = "matcha_grade") val matchaGrade: String? = null,
    @ColumnInfo(name = "taste_scales_json") val tasteScalesJson: String? = null,
    @ColumnInfo(name = "breadcrumb") val breadcrumb: String?,
    @ColumnInfo(name = "ingredients") val ingredients: String? = null,
    @ColumnInfo(name = "description_html") val descriptionHtml: String? = null,
    @ColumnInfo(name = "description_text") val descriptionText: String? = null,
    @ColumnInfo(name = "brew_table_raw") val brewTableRaw: String? = null,
    @ColumnInfo(name = "brew_text_raw") val brewTextRaw: String? = null,
    @ColumnInfo(name = "derived_recipes_json") val derivedRecipesJson: String? = null,
    @ColumnInfo(name = "recipe_check_reason") val recipeCheckReason: RecipeCheckReason? = null,
    @ColumnInfo(name = "base_price_cents_per_100g") val basePriceCentsPer100g: Long?,
    @ColumnInfo(name = "available") val available: Boolean?,
    @ColumnInfo(name = "discontinued") val discontinued: Boolean = false,
    @ColumnInfo(name = "photo_file") val photoFile: String? = null,
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    @ColumnInfo(name = "photo_etag") val photoEtag: String? = null,
    @ColumnInfo(name = "content_hash") val contentHash: String? = null,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)

/**
 * Table `catalog_tea_staging` (05 §4.5): a refresh writes its teas here and swaps them into
 * [CatalogTeaEntity]'s table in one transaction. The same columns in the same order (embedded),
 * without the indices; `CatalogSchemaTest` holds the two tables equal.
 */
@SuppressWarnings(RoomWarnings.PRIMARY_KEY_FROM_EMBEDDED_IS_DROPPED, RoomWarnings.INDEX_FROM_EMBEDDED_ENTITY_IS_DROPPED)
@Entity(tableName = "catalog_tea_staging", primaryKeys = ["key"])
data class StagedCatalogTeaEntity(@Embedded val tea: CatalogTeaEntity)

/** FTS4 `catalog_fts` (05 §4.1) over [CatalogTeaEntity]'s searchable text, kept in sync by Room's triggers. */
@Fts4(contentEntity = CatalogTeaEntity::class)
@Entity(tableName = "catalog_fts")
data class CatalogFtsEntity(
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "secondary_name") val secondaryName: String?,
    @ColumnInfo(name = "description_text") val descriptionText: String?,
    @ColumnInfo(name = "cultivar") val cultivar: String?,
    @ColumnInfo(name = "origin_region") val originRegion: String?,
)

/** Table `catalog_package` (05 §4.2): one package size of a catalog tea. */
@Entity(
    tableName = "catalog_package",
    primaryKeys = ["catalog_key", "grams"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogTeaEntity::class,
            parentColumns = ["key"],
            childColumns = ["catalog_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CatalogPackageEntity(
    @ColumnInfo(name = "catalog_key") val catalogKey: String,
    @ColumnInfo(name = "article_number") val articleNumber: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "grams") val grams: Double,
    @ColumnInfo(name = "price_cents") val priceCents: Long?,
    @ColumnInfo(name = "price_is_estimate") val priceIsEstimate: Boolean,
    @ColumnInfo(name = "savings_percent") val savingsPercent: Int?,
    @ColumnInfo(name = "variant_url") val variantUrl: String?,
    @ColumnInfo(name = "available") val available: Boolean?,
)

/** Table `catalog_package_staging` (05 §4.5): [CatalogPackageEntity]'s columns, without the foreign key. */
@SuppressWarnings(RoomWarnings.PRIMARY_KEY_FROM_EMBEDDED_IS_DROPPED, RoomWarnings.INDEX_FROM_EMBEDDED_ENTITY_IS_DROPPED)
@Entity(tableName = "catalog_package_staging", primaryKeys = ["catalog_key", "grams"])
data class StagedCatalogPackageEntity(@Embedded val pkg: CatalogPackageEntity)

/** Why a base number is out of the catalog's scope (05 §4.2a). */
enum class CatalogSkipReason { NOT_GREEN, SET, NO_TEA }

/** Table `catalog_skip` (05 §4.2a): base numbers classified out of scope, re-checked after 90 days. */
@Entity(tableName = "catalog_skip")
data class CatalogSkipEntity(
    @PrimaryKey @ColumnInfo(name = "base_key") val baseKey: String,
    @ColumnInfo(name = "reason") val reason: CatalogSkipReason,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
)

/** Table `catalog_guide` (05 §4.3): one Tee-Info article. */
@Entity(tableName = "catalog_guide")
data class CatalogGuideEntity(
    @PrimaryKey @ColumnInfo(name = "slug") val slug: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "body_markdown") val bodyMarkdown: String,
    @ColumnInfo(name = "hero_file") val heroFile: String?,
    @ColumnInfo(name = "linked_catalog_keys") val linkedCatalogKeys: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** How a refresh ended (05 §4.4). */
enum class CatalogRefreshStatus { SUCCESS, FAILED, CANCELLED }

/** Table `catalog_refresh_log` (05 §4.4): one row per finished refresh. */
@Entity(tableName = "catalog_refresh_log", indices = [Index("status", "finished_at")])
data class CatalogRefreshLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "status") val status: CatalogRefreshStatus,
    @ColumnInfo(name = "new_count") val newCount: Int,
    @ColumnInfo(name = "changed_count") val changedCount: Int,
    @ColumnInfo(name = "discontinued_count") val discontinuedCount: Int,
    @ColumnInfo(name = "flagged_count") val flaggedCount: Int,
    @ColumnInfo(name = "photos_count") val photosCount: Int,
    @ColumnInfo(name = "parser_version") val parserVersion: Int,
    @ColumnInfo(name = "error_summary") val errorSummary: String?,
)
