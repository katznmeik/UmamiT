package com.umamit.core.data.catalog

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.catalog.parser.CollectionFields
import com.umamit.core.catalog.parser.ShopScale
import com.umamit.core.catalog.parser.StyleMapper
import com.umamit.core.common.WallClock
import com.umamit.core.data.CoverFile
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.coverCopy
import com.umamit.core.data.purchaseEvent
import com.umamit.core.data.saveCover
import com.umamit.core.data.withCoverFile
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Tea
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
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

    /** The database or the disk failed, or an unexpected error (D-090); nothing was written. */
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
 * - **Merkliste:** the add and the repurchase delete the tea's mark (`catalog_bookmark`) in their
 *   transaction (CAT-18 AC2, 05 §3.13); a failed add keeps it.
 */
interface AddFromCatalog {
    /**
     * The collection tea that owns [catalogKey], or null: decides between *In Sammlung aufnehmen*
     * and *Nachkauf erfassen*. An archived tea owns nothing; adding its key brings it back (TEA-04).
     */
    fun observeOwner(catalogKey: String): Flow<String?>

    /** Adds the tea of [purchase], or records the repurchase when it is owned; one transaction. */
    suspend fun add(purchase: CatalogPurchase): AddFromCatalogResult

    /**
     * *Neuen Tee anlegen* for a received recipe (PRF-08 AC6, 05 §8a): the catalog tea [catalogKey] added as [add]
     * adds it, but without a package, so it has no stock event (no stock is invented, as without catalog). A key a
     * collection tea already links to writes nothing and answers [AddFromCatalogResult.Repurchased] with that tea.
     */
    suspend fun addWithoutPackage(catalogKey: String): AddFromCatalogResult
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

    override suspend fun add(purchase: CatalogPurchase): AddFromCatalogResult = add(purchase.catalogKey, purchase)

    override suspend fun addWithoutPackage(catalogKey: String): AddFromCatalogResult = add(catalogKey, null)

    /** [purchase] null: no package, and an owned key is only named (PRF-08). */
    private suspend fun add(catalogKey: String, purchase: CatalogPurchase?): AddFromCatalogResult = try {
        val entry = catalog.tea(catalogKey)
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
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Rule 8: the timer runs in this process; a bug in the copy fails the add, never the app (D-090).
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
    ) {
        AddFromCatalogResult.Failed
    }

    /** The catalog photo as the collection's JPEG (D-076), or null without a usable photo. */
    private suspend fun cover(entry: CatalogTeaEntity): CoverFile? = catalogPhotos.coverCopy(entry)?.let(::CoverFile)

    private suspend fun write(
        entry: CatalogTeaEntity,
        purchase: CatalogPurchase?,
        cover: CoverFile?,
    ): AddFromCatalogResult = photoFiles.withCoverFile(cover, kept = { it is AddFromCatalogResult.Added }) {
        db.withTransaction {
            // Read again inside the transaction: a tea added meanwhile turns this into a repurchase.
            val owner = db.teaDao().idByCatalogKey(entry.key)
            // The tea leaves the Merkliste with the add, in its transaction: a failed add keeps the mark (CAT-18). A
            // package-less add of an owned key writes nothing.
            if (owner == null || purchase != null) db.catalogBookmarkDao().delete(entry.key)
            when {
                owner == null -> AddFromCatalogResult.Added(copy(entry, purchase, cover))
                purchase == null -> AddFromCatalogResult.Repurchased(owner)
                else -> repurchase(owner, purchase)
            }
        }
    }

    private suspend fun repurchase(teaId: String, purchase: CatalogPurchase): AddFromCatalogResult {
        // A tea archived when it was used up, or removed from the collection, comes back (TEA-04, D-093 Q6).
        teas.getTea(teaId)?.takeIf { it.isArchived }?.let { teas.saveTea(it.copy(isArchived = false)) }
        stock.addEvent(event(teaId, InventoryEventType.PURCHASE, purchase))
        return AddFromCatalogResult.Repurchased(teaId)
    }

    /** Writes the collection copy of [entry] and returns its id. */
    private suspend fun copy(entry: CatalogTeaEntity, purchase: CatalogPurchase?, cover: CoverFile?): String {
        val teaId = UUID.randomUUID().toString()
        val tea = entry.toTea(teaId)
        // Saving a recipe clears „Rezept prüfen" (TEA-17), so the tea's reason is written after its recipes.
        teas.saveTea(tea.copy(recipeCheckReason = null))
        CatalogDerivation.templates(entry).forEachIndexed { position, template ->
            recipes.saveRecipe(template.toRecipe(UUID.randomUUID().toString(), teaId, position))
        }
        if (tea.recipeCheckReason != null) teas.saveTea(tea)
        purchase?.let { stock.addEvent(event(teaId, InventoryEventType.INITIAL, it)) }
        if (cover != null) teas.saveCover(teaId, cover)
        return teaId
    }

    /** The collection fields of [this] catalog tea (05 §3.1; 08 §2.3's conversion table). */
    private fun CatalogTeaEntity.toTea(id: String): Tea {
        val style = CatalogDerivation.style(this)
        val match = StyleMapper.map(breadcrumb, name, teaType, isFlavored, ingredients = ingredients)
        val scales = CatalogColumns.tasteScales(tasteScalesJson).map { ShopScale(it.label, it.leaves) }
        val prose = descriptionText
        return Tea(
            id = id,
            name = (name ?: key).take(NAME_MAX),
            // A chai's is its base tea's (#206): the mapping's, while it maps to the stored style.
            category = if (match.style.key == style.key) match.category else style.category,
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
            description = CatalogDerivation.description(this),
            vendor = VENDOR,
            productUrl = productUrl,
            recipeCheckReason = recipeCheckReason,
        )
    }

    private fun currentYear(): Int = Instant.ofEpochMilli(time.nowMs()).atZone(time.zone()).year

    /** The stock event of [purchase] ([purchaseEvent]: bought today, now; on another day, that day at noon). */
    private fun event(teaId: String, type: InventoryEventType, purchase: CatalogPurchase): InventoryEvent =
        purchaseEvent(teaId, type, purchase.grams, purchase.priceCents, purchase.purchasedOn, time)

    private companion object {
        /** The default vendor of catalog teas (TEA-02). */
        const val VENDOR = "Tee-Kontor Kiel"

        // Field limits of 05 §3.1.
        const val NAME_MAX = 80
        const val INGREDIENTS_MAX = 1000
        const val PROCESSING_MAX = 300
    }
}

/** Binds [AddFromCatalog] (CAT-12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AddFromCatalogModule {
    @Binds
    abstract fun addFromCatalog(impl: RoomAddFromCatalog): AddFromCatalog
}
