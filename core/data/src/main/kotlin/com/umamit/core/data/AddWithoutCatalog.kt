package com.umamit.core.data

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.data.catalog.CatalogColumns
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** A package entered in *Kauf* (TEA-02, INV-01): [grams] for [priceCents] (null: no price), bought on [purchasedOn]. */
data class TeaPurchase(val grams: Double, val priceCents: Long?, val purchasedOn: LocalDate) {
    init {
        require(grams.isFinite() && grams > 0.0) { "A package weighs more than 0 g" }
        require(priceCents == null || priceCents >= 0) { "A price is at least 0" }
    }
}

/** How [AddWithoutCatalog.add] ended. */
sealed interface AddWithoutCatalogResult {
    /** The new collection tea [teaId]. */
    data class Added(val teaId: String) : AddWithoutCatalogResult

    /** The database failed; nothing was written. */
    data object Failed : AddWithoutCatalogResult
}

/**
 * *Ohne Katalog* (TEA-01, PRF-02, INV-01, SCR-10, SCR-11; D-100): a tea added by hand, e.g. a gift or one
 * from another shop.
 */
interface AddWithoutCatalog {
    /**
     * Writes, in one transaction, a new tea with the fields of [tea] (its id is replaced by a new one; it
     * is not archived, has no photo and no „Rezept prüfen"), its style's recipes with the style's default
     * as the default (PRF-02; *Westlich* 3 g / 250 ml / 85 °C / 3:00 for a tea without a known style) and,
     * only with a [purchase], the INITIAL stock event (INV-01). Without a package the tea has no stock
     * events: no stock is invented.
     */
    suspend fun add(tea: Tea, purchase: TeaPurchase?): AddWithoutCatalogResult
}

/** [AddWithoutCatalog] into `umamit.db`. */
class RoomAddWithoutCatalog @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val stock: StockRepository,
    private val time: WallClock,
) : AddWithoutCatalog {
    override suspend fun add(tea: Tea, purchase: TeaPurchase?): AddWithoutCatalogResult = try {
        val teaId = UUID.randomUUID().toString()
        db.withTransaction {
            teas.saveTea(
                tea.copy(id = teaId, isArchived = false, coverPhotoId = null, recipeCheckReason = null),
            )
            templates(tea.styleKey).forEachIndexed { position, template ->
                recipes.saveRecipe(template.toRecipe(UUID.randomUUID().toString(), teaId, position))
            }
            purchase?.let {
                stock.addEvent(
                    purchaseEvent(teaId, InventoryEventType.INITIAL, it.grams, it.priceCents, it.purchasedOn, time),
                )
            }
        }
        AddWithoutCatalogResult.Added(teaId)
    } catch (_: SQLException) {
        AddWithoutCatalogResult.Failed
    }

    /** The style's recipes, its default first and the only default; PRF-02's *Westlich* without a style. */
    private fun templates(styleKey: String?): List<RecipeTemplate> {
        val templates = styleKey?.let(BuiltInStyles::byKey)?.recipes ?: listOf(WESTERN_DEFAULT)
        val default = templates.firstOrNull { it.isDefault } ?: templates.first()
        return listOf(default.copy(isDefault = true)) +
            templates.filter { it !== default }.map { it.copy(isDefault = false) }
    }

    companion object {
        /**
         * PRF-02: a tea without a style brews *Westlich* 3 g / 250 ml / 85 °C, one infusion of 3:00; also the
         * recipe editor's start for *Rezept hinzufügen* on a tea without any recipe (SCR-12).
         */
        val WESTERN_DEFAULT = RecipeTemplate(
            name = "Westlich",
            method = BrewMethod.WESTERN,
            leafGrams = 3.0,
            waterMl = 250,
            baseTempC = 85,
            steps = listOf(RecipeStep(StepKind.INFUSION, 180)),
            source = RecipeSource.STYLE_DEFAULT,
            isDefault = true,
        )
    }
}

/**
 * The stock event of a package of [grams] for [priceCents] bought on [purchasedOn] (INV-01, INV-03): bought
 * today, now; on another day, that day at noon in the phone's zone (the order among the events of one day
 * doesn't matter, D-085). Its label is the package's („50 g").
 */
@Suppress("LongParameterList") // the event's fields and the clock
internal fun purchaseEvent(
    teaId: String,
    type: InventoryEventType,
    grams: Double,
    priceCents: Long?,
    purchasedOn: LocalDate,
    time: WallClock,
): InventoryEvent {
    val now = time.nowMs()
    val zone = time.zone()
    val at = if (purchasedOn == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) {
        now
    } else {
        purchasedOn.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }
    return InventoryEvent(
        id = UUID.randomUUID().toString(),
        teaId = teaId,
        type = type,
        deltaGrams = grams,
        occurredAtMillis = at,
        priceCents = priceCents,
        packageLabel = CatalogColumns.packageLabel(grams, null),
    )
}

/** Binds [AddWithoutCatalog] (TEA-01). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AddWithoutCatalogModule {
    @Binds
    abstract fun addWithoutCatalog(impl: RoomAddWithoutCatalog): AddWithoutCatalog
}
