package com.umamit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.umamit.core.database.dao.ActiveBrewDao
import com.umamit.core.database.dao.CatalogBookmarkDao
import com.umamit.core.database.dao.InventoryDao
import com.umamit.core.database.dao.JournalDao
import com.umamit.core.database.dao.RecipeDao
import com.umamit.core.database.dao.SessionDao
import com.umamit.core.database.dao.SessionPhotoDao
import com.umamit.core.database.dao.TastingDao
import com.umamit.core.database.dao.TeaDao
import com.umamit.core.database.dao.VesselDao
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.CatalogBookmarkEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.database.entity.VesselEntity
import java.time.LocalDate

/**
 * The personal database `umamit.db` (05 §1, §3), schema version 7 (v0.6.1: taste and a note per infusion on
 * `session_step`, D-114; v6 the descriptors `session_descriptor`, D-112; v5 the per-tea stock warning
 * `tea.low_stock_warning`, D-108; v4 `active_brew` per timer slot, D-098; v3 brought the Merkliste
 * `catalog_bookmark` and the quick timer's own pre-alert, D-097; v2 the pre-alert per step, D-093). Every version
 * change ships a migration and a test; there is no destructive fallback and no downgrade (05 §9, D-093 Q16).
 */
@Database(
    entities = [
        TeaEntity::class,
        BrewProfileEntity::class,
        BrewStepEntity::class,
        VesselEntity::class,
        BrewSessionEntity::class,
        SessionStepEntity::class,
        InventoryEventEntity::class,
        PhotoEntity::class,
        ActiveBrewEntity::class,
        CatalogBookmarkEntity::class,
        SessionDescriptorEntity::class,
    ],
    version = UmamiDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class UmamiDatabase : RoomDatabase() {
    abstract fun teaDao(): TeaDao

    abstract fun recipeDao(): RecipeDao

    abstract fun vesselDao(): VesselDao

    abstract fun inventoryDao(): InventoryDao

    abstract fun sessionDao(): SessionDao

    abstract fun activeBrewDao(): ActiveBrewDao

    abstract fun journalDao(): JournalDao

    abstract fun catalogBookmarkDao(): CatalogBookmarkDao

    abstract fun tastingDao(): TastingDao

    abstract fun sessionPhotoDao(): SessionPhotoDao

    companion object {
        const val NAME = "umamit.db"
        const val VERSION = 7
    }
}

/** Calendar dates are stored as ISO TEXT (`2026-10-21`, 05 §3.1). */
internal object DateConverters {
    @TypeConverter
    fun fromIso(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun toIso(date: LocalDate?): String? = date?.toString()
}
