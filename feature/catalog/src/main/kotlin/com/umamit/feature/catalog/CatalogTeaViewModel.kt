package com.umamit.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.data.StockRepository
import com.umamit.core.data.catalog.AddFromCatalog
import com.umamit.core.data.catalog.AddFromCatalogResult
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.ExactPriceSession
import com.umamit.core.data.catalog.ExactPrices
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * What a catalog tea's detail shows (SCR-21).
 *
 * @property loading true until the catalog was read once.
 * @property tea the tea, or null when the catalog has none with this key (any more).
 * @property shopUrl the page *Im Shop öffnen* opens (CAT-11, D-072).
 * @property ownedTeaId the collection tea linked to this catalog tea, if any: the primary action
 *   is then *Nachkauf erfassen* instead of *In Sammlung aufnehmen* (CAT-12).
 * @property lastBoughtGrams the size of the owned tea's latest INITIAL or PURCHASE event.
 */
data class CatalogTeaUiState(
    val loading: Boolean = true,
    val tea: CatalogTeaDetail? = null,
    val shopUrl: String? = null,
    val ownedTeaId: String? = null,
    val lastBoughtGrams: Double? = null,
)

/**
 * The detail of the catalog tea [KEY] (SCR-21, CAT-11) and its add sheet (SCR-23, CAT-12).
 *
 * The detail reads only local data: the tea from the local catalog, and for *Im Shop öffnen* and
 * the add sheet the stock events of the collection tea linked to it by `catalog_key`, if any (the
 * size bought last, D-072). The add sheet fetches the exact price of a size whose price is an
 * estimate or missing from the size's own page, once per size and sheet, through one shop
 * session (network use (c) of GEN-01); saving never waits for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // the detail, its add sheet and their sources
class CatalogTeaViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    private val stock: StockRepository,
    private val add: AddFromCatalog,
    private val exactPrices: ExactPrices,
    private val clock: WallClock,
) : ViewModel() {
    private val key: String = checkNotNull(savedState[KEY]) { "no catalog key" }

    val state: StateFlow<CatalogTeaUiState> = catalog.observeTea(key).flatMapLatest { tea ->
        if (tea == null) {
            flowOf(CatalogTeaUiState(loading = false))
        } else {
            add.observeOwner(tea.key).flatMapLatest { owner -> ownedEvents(owner).map { owner to it } }
                .map { (owner, events) ->
                    CatalogTeaUiState(
                        loading = false,
                        tea = tea,
                        shopUrl = ShopLink.url(tea.productUrl, tea.packages, events),
                        ownedTeaId = owner,
                        lastBoughtGrams = events?.lastBought()?.deltaGrams,
                    )
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogTeaUiState())

    private val sheetState = MutableStateFlow<AddSheetState?>(null)

    /** The add sheet (SCR-23) while it is open. */
    val sheet: StateFlow<AddSheetState?> = sheetState.asStateFlow()

    private val addedTeas = Channel<String>(Channel.BUFFERED)

    /** The collection tea of each saved add or repurchase, once (for the picker flow, #72). */
    val added: Flow<String> = addedTeas.receiveAsFlow()

    private var prices: ExactPriceSession? = null
    private val fetches = mutableListOf<Job>()

    /** The sheet of a save still running after an alert removed it (D-090): a failure brings it back. */
    private var savingClosed: AddSheetState? = null

    /** The stock events of the collection tea [teaId], or null when there is none. */
    private fun ownedEvents(teaId: String?): Flow<List<InventoryEvent>?> =
        if (teaId == null) flowOf(null) else stock.observeEvents(teaId)

    /** *In Sammlung aufnehmen* / *Nachkauf erfassen*: opens the sheet with today's date. */
    fun openSheet() {
        val current = state.value
        val tea = current.tea ?: return
        if (sheetState.value != null || savingClosed != null) return
        val today = Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()
        prices = exactPrices.session()
        sheetState.value = AddSheetState.open(
            teaName = tea.name,
            packages = tea.packages,
            repurchase = current.ownedTeaId != null,
            lastBoughtGrams = current.lastBoughtGrams,
            today = today,
        )
        fetchSelected()
    }

    /** Chooses size [index] (`sizes.size`: a custom amount) and fetches its exact price when it needs one. */
    fun selectSize(index: Int) {
        sheetState.update { it?.select(index) }
        fetchSelected()
    }

    fun editPrice(text: String) = sheetState.update { it?.editPrice(text) }

    fun editGrams(text: String) = sheetState.update { it?.editGrams(text) }

    fun pickDate(day: LocalDate) = sheetState.update { it?.pickDate(day) }

    /** The user dismissed the sheet: it closes unless it saves (D-090), so a save's outcome is never lost. */
    fun dismissSheet() {
        if (sheetState.value?.saving != true) closeSheet()
    }

    /**
     * Closes the sheet at once (an alert, rule 8; or a dismissal); a running price fetch is
     * cancelled. A save goes on: it is recorded, or a failure brings the sheet back with its
     * message once nothing covers it (D-090).
     */
    fun closeSheet() {
        savingClosed = sheetState.value?.takeIf { it.saving } ?: savingClosed
        sheetState.value = null
        fetches.forEach { it.cancel() }
        fetches.clear()
        prices = null
    }

    /**
     * *Aufnehmen*: records the purchase (CAT-12); the sheet closes once it is written. Once
     * started, the save runs to its end even if the detail is left (D-090).
     */
    fun save() {
        val sheet = sheetState.value?.takeIf { it.canSave } ?: return
        val grams = sheet.grams ?: return
        sheetState.value = sheet.copy(saving = true, failed = false)
        viewModelScope.launch {
            val result = withContext(NonCancellable) {
                add.add(CatalogPurchase(key, grams, sheet.priceCents, sheet.date))
            }
            val teaId = when (result) {
                is AddFromCatalogResult.Added -> result.teaId
                is AddFromCatalogResult.Repurchased -> result.teaId
                AddFromCatalogResult.NotInCatalog, AddFromCatalogResult.Failed -> null
            }
            val closed = savingClosed
            savingClosed = null
            if (teaId != null) {
                closeSheet()
                addedTeas.send(teaId)
            } else if (closed != null) {
                // Removed meanwhile: the sheet comes back with its input and the message; a price
                // fetch the removal cancelled counts as failed, so the estimate stays.
                prices = exactPrices.session()
                sheetState.value = closed.copy(
                    sizes = closed.sizes.map { size ->
                        if (size.fetch == PriceFetch.RUNNING) size.copy(fetch = PriceFetch.FAILED) else size
                    },
                    saving = false,
                    failed = true,
                )
            } else {
                sheetState.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    /** One fetch per size and sheet, only for an estimate or a missing price with its own page. */
    private fun fetchSelected() {
        val sheet = sheetState.value ?: return
        val index = sheet.selected
        val size = sheet.selectedSize?.takeIf { it.needsFetch }
        val url = size?.variantUrl
        val session = prices
        if (size != null && url != null && session != null) {
            sheetState.update { it?.fetching(index) }
            fetches += viewModelScope.launch {
                val cents = session.price(url, size.grams)
                sheetState.update { it?.fetched(index, cents) }
            }
        }
    }

    companion object {
        /** The navigation argument: the catalog key (05 §4.1). */
        const val KEY = "key"

        private const val STOP_TIMEOUT_MS = 5_000L

        private val BOUGHT = setOf(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)

        /** The latest INITIAL or PURCHASE event (D-072), the later one of the same moment. */
        private fun List<InventoryEvent>.lastBought(): InventoryEvent? = withIndex()
            .filter { it.value.type in BOUGHT }
            .maxWithOrNull(compareBy({ it.value.occurredAtMillis }, { it.index }))
            ?.value
    }
}
