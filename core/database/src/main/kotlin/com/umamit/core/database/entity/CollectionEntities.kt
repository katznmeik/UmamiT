package com.umamit.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import java.time.LocalDate

// Collection tables of 05 §3.1–§3.4, §3.9, §3.10. Enums are stored by name (05 §5),
// booleans as INTEGER 0/1, dates as ISO TEXT, timestamps as epoch ms UTC.

/** Table `tea` (05 §3.1). */
@Entity(
    tableName = "tea",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["cover_photo_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("name"),
        Index("catalog_key"),
        Index("style_key"),
        Index("is_archived"),
        Index("cover_photo_id"),
    ],
)
data class TeaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "secondary_name") val secondaryName: String?,
    @ColumnInfo(name = "catalog_key") val catalogKey: String?,
    @ColumnInfo(name = "style_key") val styleKey: String?,
    @ColumnInfo(name = "tea_type") val teaType: String?,
    @ColumnInfo(name = "category") val category: TeaCategory,
    @ColumnInfo(name = "form") val form: TeaForm,
    @ColumnInfo(name = "umami_level") val umamiLevel: UmamiLevel?,
    @ColumnInfo(name = "is_flavored") val isFlavored: Boolean,
    @ColumnInfo(name = "is_organic") val isOrganic: Boolean,
    @ColumnInfo(name = "ingredients") val ingredients: String?,
    @ColumnInfo(name = "origin_country") val originCountry: String?,
    @ColumnInfo(name = "origin_region") val originRegion: String?,
    @ColumnInfo(name = "cultivar") val cultivar: String?,
    @ColumnInfo(name = "harvest_year") val harvestYear: Int?,
    @ColumnInfo(name = "harvest_season") val harvestSeason: HarvestSeason?,
    @ColumnInfo(name = "shading_days") val shadingDays: Int?,
    @ColumnInfo(name = "processing_notes") val processingNotes: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "vendor") val vendor: String?,
    @ColumnInfo(name = "product_url") val productUrl: String?,
    @ColumnInfo(name = "opened_date") val openedDate: LocalDate?,
    @ColumnInfo(name = "best_before") val bestBefore: LocalDate?,
    @ColumnInfo(name = "freshness_days") val freshnessDays: Int?,
    @ColumnInfo(name = "low_stock_grams") val lowStockGrams: Double?,
    @ColumnInfo(name = "storage_note") val storageNote: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "recipe_check_reason") val recipeCheckReason: RecipeCheckReason?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Table `brew_profile`, a recipe (05 §3.2). */
@Entity(
    tableName = "brew_profile",
    foreignKeys = [
        ForeignKey(
            entity = TeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["tea_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VesselEntity::class,
            parentColumns = ["id"],
            childColumns = ["vessel_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tea_id"), Index("vessel_id")],
)
data class BrewProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tea_id") val teaId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "method") val method: BrewMethod,
    @ColumnInfo(name = "leaf_grams") val leafGrams: Double,
    @ColumnInfo(name = "water_ml") val waterMl: Int,
    @ColumnInfo(name = "base_temp_c") val baseTempC: Int,
    @ColumnInfo(name = "vessel_id") val vesselId: String?,
    @ColumnInfo(name = "pre_alert_seconds") val preAlertSeconds: Int?,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "source") val source: RecipeSource,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Table `brew_step` (05 §3.3); `position` is unique per recipe. `pre_alert_seconds` (schema v2, D-093) is the
 * step's pre-alert: null *Automatisch*, 0 *Aus*, else 5–30 s.
 */
@Entity(
    tableName = "brew_step",
    foreignKeys = [
        ForeignKey(
            entity = BrewProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profile_id", "position"], unique = true)],
)
data class BrewStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "kind") val kind: StepKind,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "temp_c") val tempC: Int?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "pre_alert_seconds") val preAlertSeconds: Int?,
)

/** Table `vessel` (05 §3.4). */
@Entity(
    tableName = "vessel",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["cover_photo_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("cover_photo_id")],
)
data class VesselEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: VesselType,
    @ColumnInfo(name = "capacity_ml") val capacityMl: Int,
    @ColumnInfo(name = "usable_ml") val usableMl: Int?,
    @ColumnInfo(name = "material") val material: VesselMaterial?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Table `photo` (05 §3.9): exactly one of `tea_id` / `session_id` / `vessel_id` is set. */
@Entity(
    tableName = "photo",
    foreignKeys = [
        ForeignKey(
            entity = TeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["tea_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BrewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VesselEntity::class,
            parentColumns = ["id"],
            childColumns = ["vessel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tea_id"), Index("session_id"), Index("vessel_id")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tea_id") val teaId: String?,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "vessel_id") val vesselId: String?,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "width_px") val widthPx: Int,
    @ColumnInfo(name = "height_px") val heightPx: Int,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "origin") val origin: PhotoOrigin,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Table `inventory_event` (05 §3.10); at most one event per session. */
@Entity(
    tableName = "inventory_event",
    foreignKeys = [
        ForeignKey(
            entity = TeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["tea_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BrewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tea_id"), Index(value = ["session_id"], unique = true)],
)
data class InventoryEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tea_id") val teaId: String,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "type") val type: InventoryEventType,
    @ColumnInfo(name = "delta_grams") val deltaGrams: Double,
    @ColumnInfo(name = "price_cents") val priceCents: Long?,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "package_label") val packageLabel: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Table `catalog_bookmark` (05 §3.13, schema v3, CAT-18, D-097): the Merkliste, catalog teas marked for later.
 * Personal data, so it lives in `umamit.db`, outside the rebuilt `catalog.db`, and no refresh touches it. The key
 * is the catalog tea's (`catalog_tea.catalog_key`), with no foreign key across the databases.
 */
@Entity(tableName = "catalog_bookmark")
data class CatalogBookmarkEntity(
    @PrimaryKey @ColumnInfo(name = "catalog_key") val catalogKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
