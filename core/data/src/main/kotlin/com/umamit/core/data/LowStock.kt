package com.umamit.core.data

import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.database.dao.InventoryDao
import com.umamit.core.database.dao.TeaStockLevel
import com.umamit.core.model.LowStockCard
import com.umamit.core.model.LowStockTea
import com.umamit.core.model.StockLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Start's low-stock card (INV-10, SCR-02, D-097): which tea it reminds of, and *Ausblenden*; and the teas of
 * the daily low-stock reminder (INV-11, D-098).
 */
interface LowStockRepository {
    /**
     * The card, or null: the non-archived tea with stock events whose stock is *Knapp* or *Leer* (INV-05; its
     * own `low_stock_grams`, else SET-16's „Knapp ab", inclusive) with the least stock left, skipping hidden
     * ones. One card at a time; the next most urgent tea takes the slot of a hidden one.
     */
    fun observeCard(): Flow<LowStockCard?>

    /** *Ausblenden*: hides [card]'s tea until its next INITIAL or PURCHASE event (INV-10 AC2). */
    suspend fun hide(card: LowStockCard)

    /**
     * The daily reminder's teas (INV-11): every non-archived tea with stock events that is *Knapp* or *Leer*
     * by the card's rules (a tea's own threshold first, inclusive), **including** the ones hidden with
     * *Ausblenden*; least stock first, then by name. Empty when there is none.
     */
    suspend fun reminderTeas(): List<LowStockTea>
}

/** [LowStockRepository] on the collection's stock events, the settings and the local catalog (INV-09). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomLowStockRepository @Inject constructor(
    private val dao: InventoryDao,
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
) : LowStockRepository {
    override fun observeCard(): Flow<LowStockCard?> = combine(
        dao.observeStockLevels(),
        settings.settings.map { Choice(it.lowStockGrams, it.lowStockHidden) }.distinctUntilChanged(),
    ) { levels, choice ->
        // Least stock first already: the first one that is Knapp or Leer and not hidden.
        levels.firstOrNull { level ->
            StockLevel.of(level.remainingGrams, level.lowStockGrams ?: choice.thresholdGrams) != StockLevel.AVAILABLE &&
                level.hideKey() !in choice.hidden
        }
    }.distinctUntilChanged().flatMapLatest { level ->
        if (level == null) {
            flowOf(null)
        } else {
            reorderUrl(level).map { url ->
                LowStockCard(
                    teaId = level.teaId,
                    teaName = level.name,
                    styleKey = level.styleKey,
                    remainingGrams = level.remainingGrams,
                    reorderUrl = url,
                    hideKey = level.hideKey(),
                )
            }
        }
    }.distinctUntilChanged()

    override suspend fun hide(card: LowStockCard) {
        val tea = "${card.teaId}/"
        settings.update { s ->
            // One entry per tea: an older purchase's entry has nothing left to hide.
            val others = s.lowStockHidden.filterNot { it.startsWith(tea) }.toSet()
            s.copy(lowStockHidden = others + card.hideKey)
        }
    }

    override suspend fun reminderTeas(): List<LowStockTea> {
        val threshold = settings.settings.first().lowStockGrams
        // Least stock first already. `lowStockHidden` is not read: hiding the card never hides the problem.
        return dao.observeStockLevels().first()
            .filter { StockLevel.of(it.remainingGrams, it.lowStockGrams ?: threshold) != StockLevel.AVAILABLE }
            .map { LowStockTea(teaId = it.teaId, teaName = it.name, remainingGrams = it.remainingGrams) }
    }

    /**
     * INV-09's link: the shop page of the size bought last (AC2: none without a `catalog_key` or for a
     * discontinued catalog tea). Before the catalog was ever loaded the tea's own product page stands in.
     */
    private fun reorderUrl(level: TeaStockLevel): Flow<String?> {
        val key = level.catalogKey ?: return flowOf(null)
        return combine(catalog.observeTea(key), dao.observeForTea(level.teaId)) { tea, events ->
            ShopLink.reorderUrl(key, tea, level.productUrl, events.map { it.toModel() })
        }
    }

    private data class Choice(val thresholdGrams: Double, val hidden: Set<String>)
}

/** `<tea id>/<latest INITIAL or PURCHASE event id>` (05 §6 `lowStockHidden`); a tea without one ends in „/". */
private fun TeaStockLevel.hideKey(): String = "$teaId/${lastPurchaseId.orEmpty()}"
