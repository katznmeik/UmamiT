package com.umamit.core.data.catalog

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.catalog.parser.CollectionFields
import com.umamit.core.catalog.parser.ShopScale
import com.umamit.core.catalog.parser.StyleMapper
import com.umamit.core.common.WallClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * What the add sheet (SCR-23) records for catalog tea [catalogKey]: a package of [grams] for
 * [priceCents] (null: no price), bought on [purchasedOn] (CAT-12).
 */
data class CatalogPurchase(val catalogKey: String, val grams: Double, val priceCents: Long?, val purchasedOn: LocalDate)

/** How [AddFromCatalog.add] ended. */
sealed interface AddFromCatalogResult {
    /** A new collection tea [teaId] (*In Sammlung aufnehmen*). */
    data class Added(val teaId: String) : AddFromCatalogResult

    /** Stock added to the collection tea [teaId] that already links to the catalog tea (*Nachkauf erfassen*). */
    data class Repurchased(val teaId: String) : AddFromCatalogResult

    /** The catalog holds no tea with the key (any more); nothing was written. */
    data object NotInCatalog : AddFromCatalogResult

    /** The database or the disk failed; nothing was written. */
    data object Failed : AddFromCatalogResult
}

/**
 * *In Sammlung aufnehmen* and *Nachkauf erfassen* (CAT-12, TEA-01, INV-01, INV-03, D-073).
 *
 * - **Duplicate guard:** a catalog key a collection tea already links to (`tea.catalog_key`,
 *   the seed teas Tanabata and Hisui included) always records a repurchase, never a second tea.
 * - **Add:** an independent copy of the catalog tea (CAT-13: no refresh ever changes it) with
 *   its derived recipes (the style's when the catalog has none) and its „Rezept prüfen" reason,
 *   an INITIAL stock event and the catalog photo copied as the tea's own JPEG cover. The tea is
 *   at once in the tea picker and brewable with stock deduction.
 */
interface AddFromCatalog {
    /** The collection tea linked to [catalogKey], or null: decides between add and repurchase. */
    fun observeOwner(catalogKey: String): Flow<String?>

    /** Adds the tea of [purchase], or records the repurchase when it is owned; one transaction. */
    suspend fun add(purchase: CatalogPurchase): AddFromCatalogResult
}

/** [AddFromCatalog] from `catalog.db` and its photos into `umamit.db` and `filesDir/photos/`. */
@Suppress("LongParameterList") // both databases, both photo folders, the repositories and the clock
class RoomAddFromCatalog @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val stock: StockRepository,
    private val catalog: CatalogDao,
    private val catalogPhotos: CatalogPhotoFiles,
    private val photoFiles: PhotoFiles,
    private val time: WallClock,
) : AddFromCatalog {
    override fun observeOwner(catalogKey: String): Flow<String?> =
        db.teaDao().observeIdByCatalogKey(catalogKey).distinctUntilChanged()

    override suspend fun add(purchase: CatalogPurchase): AddFromCatalogResult = try {
        val entry = catalog.tea(purchase.catalogKey)
        if (entry == null) {
            AddFromCatalogResult.NotInCatalog
        } else {
            // The photo is prepared outside the transaction; a repurchase never needs it.
            val owner = db.teaDao().idByCatalogKey(entry.key)
            val cover = if (owner == null) cover(entry) else null
            withContext(NonCancellable + Dispatchers.IO) { write(entry, purchase, cover) }
        }
    } catch (_: SQLException) {
        AddFromCatalogResult.Failed
    } catch (_: IOException) {
        AddFromCatalogResult.Failed
    }

    /** The catalog photo as the collection's JPEG (D-076), or null without a usable photo. */
    private suspend fun cover(entry: CatalogTeaEntity): CoverCopy? {
        val bytes = entry.photoFile?.let {
            withContext(Dispatchers.IO) {
                try {
                    catalogPhotos.photo(entry.key).takeIf { it.isFile }?.readBytes()
                } catch (_: IOException) {
                    null
                }
            }
        }
        val photo = bytes?.let { withContext(Dispatchers.Default) { PhotoTranscoder.transcodeCatalogCopy(it) } }
        return photo?.let { CoverCopy(UUID.randomUUID().toString(), it.bytes, it.width, it.height) }
    }

    private suspend fun write(
        entry: CatalogTeaEntity,
        purchase: CatalogPurchase,
        cover: CoverCopy?,
    ): AddFromCatalogResult {
        cover?.let { photoFiles.write(it.id, it.bytes) }
        var result: AddFromCatalogResult = AddFromCatalogResult.Failed
        try {
            result = db.withTransaction {
                // Read again inside the transaction: a tea added meanwhile turns this into a repurchase.
                val owner = db.teaDao().idByCatalogKey(entry.key)
                if (owner != null) {
                    repurchase(owner, purchase)
                } else {
                    AddFromCatalogResult.Added(copy(entry, purchase, cover))
                }
            }
        } finally {
            val coverStored = result is AddFromCatalogResult.Added && cover != null
            if (cover != null && !coverStored) photoFiles.delete(cover.id)
        }
        return result
    }

    private suspend fun repurchase(teaId: String, purchase: CatalogPurchase): AddFromCatalogResult {
        // A tea archived when it was used up is back in stock (TEA-04).
        teas.getTea(teaId)?.takeIf { it.isArchived }?.let { teas.saveTea(it.copy(isArchived = false)) }
        stock.addEvent(event(teaId, InventoryEventType.PURCHASE, purchase))
        return AddFromCatalogResult.Repurchased(teaId)
    }

    /** Writes the collection copy of [entry] and returns its id. */
    private suspend fun copy(entry: CatalogTeaEntity, purchase: CatalogPurchase, cover: CoverCopy?): String {
        val teaId = UUID.randomUUID().toString()
        teas.saveTea(entry.toTea(teaId))
        templates(entry).forEachIndexed { position, template ->
            recipes.saveRecipe(template.toRecipe(UUID.randomUUID().toString(), teaId, position))
        }
        stock.addEvent(event(teaId, InventoryEventType.INITIAL, purchase))
        if (cover != null) {
            teas.savePhoto(
                Photo(
                    id = cover.id,
                    owner = PhotoOwner.OfTea(teaId),
                    origin = PhotoOrigin.CATALOG_COPY,
                    widthPx = cover.width,
                    heightPx = cover.height,
                    byteSize = cover.bytes.size.toLong(),
                ),
            )
            teas.setCoverPhoto(teaId, cover.id)
        }
        return teaId
    }

    /** The derived recipes (CAT-04), else the style's; exactly one is the default, the default first. */
    private fun templates(entry: CatalogTeaEntity): List<RecipeTemplate> {
        val derived = DerivedRecipesColumn.recipes(entry.derivedRecipesJson)
        val templates = derived.ifEmpty { style(entry).recipes }
        val default = templates.firstOrNull { it.isDefault } ?: templates.first()
        return listOf(default.copy(isDefault = true)) +
            templates.filter { it !== default }.map { it.copy(isDefault = false) }
    }

    private fun style(entry: CatalogTeaEntity) = entry.styleKey?.let(BuiltInStyles::byKey) ?: BuiltInStyles.GENERIC

    /** The collection fields of [this] catalog tea (05 §3.1; 08 §2.3's conversion table). */
    private fun CatalogTeaEntity.toTea(id: String): Tea {
        val style = style(this)
        val match = StyleMapper.map(breadcrumb, name, teaType, isFlavored)
        val scales = CatalogColumns.tasteScales(tasteScalesJson).map { ShopScale(it.label, it.leaves) }
        val prose = descriptionText
        return Tea(
            id = id,
            name = (name ?: key).take(NAME_MAX),
            category = TeaCategory.GREEN,
            form = match.form,
            secondaryName = secondaryName?.take(NAME_MAX),
            catalogKey = key,
            styleKey = style.key,
            teaType = teaType,
            umamiLevel = StyleMapper.umamiLevel(scales, style),
            isFlavored = isFlavored,
            isOrganic = isOrganic,
            ingredients = ingredients?.take(INGREDIENTS_MAX),
            originCountry = CollectionFields.countryCode(originCountry),
            originRegion = originRegion?.take(NAME_MAX),
            cultivar = cultivar?.take(NAME_MAX),
            harvestYear = CollectionFields.harvestYear(currentYear(), harvest, name),
            harvestSeason = CollectionFields.harvestSeason(harvest, prose),
            shadingDays = CollectionFields.shadingDays(shading, prose),
            processingNotes = steaming?.take(PROCESSING_MAX),
            description = prose?.take(DESCRIPTION_MAX),
            vendor = VENDOR,
            productUrl = productUrl,
            recipeCheckReason = recipeCheckReason,
        )
    }

    private fun currentYear(): Int = Instant.ofEpochMilli(time.nowMs()).atZone(time.zone()).year

    /**
     * The stock event of [purchase]: bought today, now; on another day, that day at noon in the
     * phone's zone (the order among the events of one day doesn't matter, D-085).
     */
    private fun event(teaId: String, type: InventoryEventType, purchase: CatalogPurchase): InventoryEvent {
        val now = time.nowMs()
        val zone = time.zone()
        val at = if (purchase.purchasedOn == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) {
            now
        } else {
            purchase.purchasedOn.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
        return InventoryEvent(
            id = UUID.randomUUID().toString(),
            teaId = teaId,
            type = type,
            deltaGrams = purchase.grams,
            occurredAtMillis = at,
            priceCents = purchase.priceCents,
            packageLabel = CatalogColumns.packageLabel(purchase.grams, null),
        )
    }

    /** A catalog photo re-encoded for the collection, written as `photos/<id>.jpg`. */
    private class CoverCopy(val id: String, val bytes: ByteArray, val width: Int, val height: Int)

    private companion object {
        /** The default vendor of catalog teas (TEA-02). */
        const val VENDOR = "Tee-Kontor Kiel"

        // Field limits of 05 §3.1.
        const val NAME_MAX = 80
        const val INGREDIENTS_MAX = 1000
        const val PROCESSING_MAX = 300
        const val DESCRIPTION_MAX = 10_000
    }
}

/** Binds [AddFromCatalog] (CAT-12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AddFromCatalogModule {
    @Binds
    abstract fun addFromCatalog(impl: RoomAddFromCatalog): AddFromCatalog
}
