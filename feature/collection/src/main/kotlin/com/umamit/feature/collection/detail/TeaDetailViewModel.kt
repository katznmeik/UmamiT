package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.SystemWallClock
import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.core.data.AddPhotoResult
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.DeleteResult
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.PhotoFileContext
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaPhotos
import com.umamit.core.data.TeaPurchase
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.CatalogReset
import com.umamit.core.data.catalog.CatalogResetPlan
import com.umamit.core.data.catalog.CatalogResetResult
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.domain.collection.CostRule
import com.umamit.core.domain.collection.FreshnessRule
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.model.StockSummary
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.TeaTasting
import com.umamit.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

/** One-shot outcomes for the host of the tea detail. */
sealed interface TeaDetailEvent {
    /** *Archivieren* (or *Stattdessen archivieren*): the detail closes, the screen below offers *Rückgängig*. */
    data class Archived(val teaId: String, val teaName: String) : TeaDetailEvent

    /** *Löschen*: the detail closes. */
    data object Deleted : TeaDetailEvent

    /** *+ Foto*: the picked image couldn't be read; nothing was added (TEA-05). */
    data object PhotoUnreadable : TeaDetailEvent

    /** *+ Foto*: the tea has 10 photos already; nothing was added (TEA-05). */
    data object PhotoLimitReached : TeaDetailEvent

    /** *+ Foto*: the photo couldn't be stored; nothing was added. */
    data object PhotoFailed : TeaDetailEvent

    /** *Als Titelbild* or *Löschen* couldn't be written; nothing changed. */
    data object PhotoChangeFailed : TeaDetailEvent

    /** *Bestand korrigieren*, *Nachkauf erfassen* or a history entry's *Löschen* couldn't be written (INV-03). */
    data object StockChangeFailed : TeaDetailEvent

    /** ⋮ *Aus Katalog zurücksetzen* couldn't be written; nothing changed (PRF-06). */
    data object ResetFailed : TeaDetailEvent
}

/**
 * The tea detail (SCR-09, TEA-13, D-100) of the tea `teaId`: everything it shows comes from flows, so it
 * updates at once after an edit, a brew or a correction. ⋮ *Favorit*, *Archivieren*, *Wiederherstellen* and
 * *Löschen* write through the repositories; archive and delete go through [TeaArchive], which refuses the tea of
 * the active brew session (E15). *Bestand korrigieren* is the stock sheet's save, *Nachkauf erfassen* the purchase
 * sheet's (INV-03). *+ Foto* adds the
 * picked image through [TeaPhotos] (TEA-05, SCR-28). ⋮ *Aus Katalog zurücksetzen* is offered while [catalogReset]
 * has a plan for the tea and resets it through it (PRF-06, CAT-13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // the repositories the detail reads; one function per action
class TeaDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val vessels: VesselRepository,
    private val stock: StockRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val archive: TeaArchive,
    private val photos: PhotoFiles,
    private val teaPhotos: TeaPhotos,
    private val clock: WallClock = SystemWallClock,
    private val catalogReset: CatalogReset = NoCatalogReset,
    @PhotoFileContext private val photoFileContext: CoroutineContext = EmptyCoroutineContext,
) : ViewModel() {
    private val teaId: String = checkNotNull(savedState[TEA_ID]) { "the tea detail needs a teaId" }

    private val events = Channel<TeaDetailEvent>(Channel.BUFFERED)

    /** VORRAT's writes of the tea, one at a time ([updateTea]). */
    private val writes = Mutex()

    /** Archive and delete outcomes, once each. */
    val navigation: Flow<TeaDetailEvent> = events.receiveAsFlow()

    private val brewed: Flow<Brewed> = combine(
        journal.observeSessions().map { list -> list.map { it.session }.filter { it.snapshot.teaId == teaId } },
        // Either brew slot (v0.5.0, D-108): the tea's session may be the second brew.
        archive.observeActiveTeaIds().map { teaId in it }.distinctUntilChanged(),
        settings.settings.map { Globals(it.lowStockGrams, it.deductStock, it.cupSizeMl) }.distinctUntilChanged(),
        // The rating and GESCHMACK follow the sessions' tasting inputs at once (TEA-13, D-112).
        journal.observeTeaTasting(teaId),
    ) { sessions, active, globals, tasting -> Brewed(sessions, active, globals, tasting) }

    /** INV-09's link from the tea's catalog key, its catalog tea and the size bought last. */
    private val reorderUrl: Flow<String?> = combine(
        teas.observeTea(teaId).map { CatalogLink(it?.tea?.catalogKey, it?.tea?.productUrl) }.distinctUntilChanged(),
        stock.observeEvents(teaId),
        ::Pair,
    ).flatMapLatest { (link, events) -> reorderUrl(link, events) }

    /** The tea's catalog key while the catalog has its tea, discontinued or not, else null (SCR-04, D-114 Q10). */
    private val catalogEntry: Flow<String?> = teas.observeTea(teaId).map { it?.tea?.catalogKey }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) flowOf(null) else catalog.observeTea(key).map { tea -> key.takeIf { tea != null } }
        }.distinctUntilChanged()

    val state: StateFlow<TeaDetailUiState> = combine(
        teas.observeTea(teaId),
        combine(recipes.observeRecipes(teaId), vessels.observeVessels(), teaPhotos.observePhotos(teaId), ::Triple),
        stock.observeEvents(teaId),
        brewed,
        combine(reorderUrl, catalogReset.observePlan(teaId), catalogEntry, ::Triple),
    ) { overview, (recipeList, vesselList, photoList), stockEvents, brew, (reorder, resetPlan, catalogKey) ->
        if (overview == null) {
            TeaDetailUiState.Gone
        } else {
            TeaDetailUiState.Ready(
                detail(overview, recipeList, vesselList, stockEvents, brew, reorder)
                    .withPhotos(overview, photoList)
                    .copy(catalogReset = resetPlan?.let { CatalogResetOffer(it.leftOut) }, catalogKey = catalogKey),
            )
        }
    }.flowOn(photoFileContext) // the photo files are checked off the main thread (the app provides IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TeaDetailUiState.Loading)

    /** ⋮ *Favorit* (TEA-06): marks or unmarks the tea. */
    fun toggleFavorite() {
        viewModelScope.launch {
            val tea = teas.getTea(teaId) ?: return@launch
            teas.saveTea(tea.copy(isFavorite = !tea.isFavorite))
        }
    }

    /** VORRAT's *Warnung* (D-108): the tea's stock warning on or off; a purchase never changes it. */
    fun setStockWarning(on: Boolean) {
        updateTea { it.copy(lowStockWarning = on) }
    }

    /**
     * VORRAT's *Knapp ab* (D-108): the tea's own threshold in whole grams 1–500, null for the global one (SET-16).
     * A value outside is refused and the stored one kept.
     */
    fun setLowStockGrams(grams: Int?) {
        if (grams != null && grams !in Tea.OWN_LOW_STOCK_GRAMS) return
        updateTea { it.copy(lowStockGrams = grams?.toDouble()) }
    }

    /**
     * Writes the newest row of the tea changed by [change]; nothing when it is gone or unchanged. The writes go one
     * after another ([writes]), so a slow earlier one never overwrites a later one, and a write that has started
     * finishes when the detail closes (*Knapp ab* commits as the field goes, D-108).
     */
    private fun updateTea(change: (Tea) -> Tea) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                writes.withLock {
                    val tea = teas.getTea(teaId) ?: return@withLock
                    val changed = change(tea)
                    if (changed != tea) teas.saveTea(changed)
                }
            }
        }
    }

    /** ⋮ *Archivieren* and the dialog's *Stattdessen archivieren*; refused for the active brew's tea (E15). */
    fun archive() {
        viewModelScope.launch {
            val name = teas.getTea(teaId)?.name ?: return@launch
            if (archive.archive(teaId) == ArchiveResult.Archived) events.send(TeaDetailEvent.Archived(teaId, name))
        }
    }

    /** ⋮ *Wiederherstellen* of an archived tea: it stays on the detail. */
    fun restore() {
        viewModelScope.launch { archive.restore(teaId) }
    }

    /** The dialog's *Löschen*; refused for the active brew's tea (E15). */
    fun delete() {
        viewModelScope.launch {
            if (archive.delete(teaId) == DeleteResult.Deleted) events.send(TeaDetailEvent.Deleted)
        }
    }

    /** *Bestand korrigieren*: the new total in g (INV-03). */
    fun correctStock(grams: Double) {
        writeStock { stock.correctStock(teaId, grams) }
    }

    /**
     * The history's *Löschen*, after its confirmation (INV-03, SCR-29, D-104): a purchase or a correction goes and
     * the stock, the price and the cost follow from the events; a brew is refused by the repository (D-102). The
     * opening date stays as it is.
     */
    fun deleteStockEntry(eventId: String) {
        writeStock { stock.deleteEvent(eventId) }
    }

    /**
     * *Nachkauf erfassen* (INV-03, D-104): one PURCHASE of the sheet's grams, price and day; the stock follows from
     * the events. A day after today (the clock moved back since the sheet opened) is refused and nothing is written.
     */
    fun recordPurchase(purchase: TeaPurchase) {
        if (purchase.purchasedOn.isAfter(clock.today())) return
        writeStock { stock.recordPurchase(teaId, purchase) }
    }

    /** A stock write that fails (a full disk, a locked database) changes nothing and is said once (D-090). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun writeStock(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                events.send(TeaDetailEvent.StockChangeFailed)
            }
        }
    }

    /** *+ Foto*: adds the image the photo picker returned (TEA-05); a refusal or failure is said once. */
    fun addPhoto(uri: String) {
        viewModelScope.launch {
            when (teaPhotos.add(teaId, uri)) {
                is AddPhotoResult.Added -> Unit
                AddPhotoResult.LimitReached -> events.send(TeaDetailEvent.PhotoLimitReached)
                AddPhotoResult.Unreadable -> events.send(TeaDetailEvent.PhotoUnreadable)
                AddPhotoResult.Failed -> events.send(TeaDetailEvent.PhotoFailed)
            }
        }
    }

    /** *Als Titelbild* (TEA-05): photo [photoId] becomes the tea's cover. */
    fun setCoverPhoto(photoId: String) {
        viewModelScope.launch {
            if (!teaPhotos.setCover(teaId, photoId)) events.send(TeaDetailEvent.PhotoChangeFailed)
        }
    }

    /** The confirmation's *Löschen* (TEA-05): photo [photoId] goes; the next one becomes the cover if it was. */
    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            if (!teaPhotos.delete(teaId, photoId)) events.send(TeaDetailEvent.PhotoChangeFailed)
        }
    }

    /** The confirmation's *Zurücksetzen* (PRF-06): the catalog's description and recipes; a failure is said once. */
    fun resetFromCatalog() {
        viewModelScope.launch {
            if (catalogReset.reset(teaId) == CatalogResetResult.Failed) events.send(TeaDetailEvent.ResetFailed)
        }
    }

    /** The pager's photos whose files exist: the cover first, then the others by position (SCR-28). */
    private fun TeaDetail.withPhotos(overview: TeaOverview, photoList: List<Photo>): TeaDetail {
        val coverId = overview.coverPhoto?.id
        val pager = photoList.sortedBy { if (it.id == coverId) -1 else it.position }
            .mapNotNull { photo ->
                this@TeaDetailViewModel.photos.file(photo.id).takeIf { it.isFile }
                    ?.let { DetailPhoto(photo.id, it.path, photo.id == coverId) }
            }
        return copy(photos = pager, photoCount = photoList.size)
    }

    private fun reorderUrl(link: CatalogLink, events: List<InventoryEvent>): Flow<String?> {
        val key = link.catalogKey ?: return flowOf(null)
        return catalog.observeTea(key).map { ShopLink.reorderUrl(key, it, link.productUrl, events) }
    }

    private data class CatalogLink(val catalogKey: String?, val productUrl: String?)

    @Suppress("LongParameterList") // the flows' values, one each
    private fun detail(
        overview: TeaOverview,
        recipeList: List<Recipe>,
        vesselList: List<Vessel>,
        stockEvents: List<InventoryEvent>,
        brew: Brewed,
        reorderUrl: String?,
    ): TeaDetail {
        val tea = overview.tea
        val grams = stockEvents.sumOf { it.deltaGrams }
        val level = StockLevel.of(grams, tea.lowStockGrams ?: brew.globals.lowStockGrams)
        val finished = brew.sessions.filter { it.status != SessionStatus.IN_PROGRESS }
        val latest = finished.maxByOrNull { it.startedAtMs }
        val vesselNames = vesselList.associate { it.id to it.name }
        return TeaDetail(
            id = tea.id,
            name = tea.name,
            secondaryName = tea.secondaryName,
            teaType = tea.teaType,
            styleKey = tea.styleKey,
            umamiLevel = tea.umamiLevel,
            photoPath = overview.coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path,
            sessionCount = finished.size,
            lastBrewed = latest?.day(),
            stockGrams = grams,
            hasStock = stockEvents.isNotEmpty(),
            stockLevel = level,
            freshness = detailFreshness(tea, if (stockEvents.isEmpty()) null else grams, clock.today()),
            recipes = recipeList.sortedBy { it.position }.map { it.summary(vesselNames) },
            // With the warning off the link stays a text link, Knapp or not (INV-09 AC1, D-108).
            reorder = reorderUrl?.let {
                Reorder(
                    it,
                    prominent = tea.lowStockWarning && stockEvents.isNotEmpty() && level != StockLevel.AVAILABLE,
                )
            },
            stockWarning = tea.lowStockWarning,
            ownLowStockGrams = tea.lowStockGrams?.roundToInt(),
            globalLowStockGrams = brew.globals.lowStockGrams.roundToInt(),
            description = tea.description?.takeIf { it.isNotBlank() },
            notes = tea.notes?.takeIf { it.isNotBlank() },
            details = TeaFacts(
                originCountry = tea.originCountry?.takeIf { it.isNotBlank() },
                originRegion = tea.originRegion?.takeIf { it.isNotBlank() },
                cultivar = tea.cultivar?.takeIf { it.isNotBlank() },
                harvestYear = tea.harvestYear,
                harvestSeason = tea.harvestSeason,
                shadingDays = tea.shadingDays,
                processing = tea.processingNotes?.takeIf { it.isNotBlank() },
                ingredients = tea.ingredients?.takeIf { it.isNotBlank() },
                vendor = tea.vendor?.takeIf { it.isNotBlank() },
                bestBefore = tea.bestBefore,
                isOrganic = tea.isOrganic,
                isFlavored = tea.isFlavored,
            ),
            isFavorite = tea.isFavorite,
            isArchived = tea.isArchived,
            inActiveBrew = brew.active,
            sessionLeafGrams = brew.sessionLeaf(),
            today = clock.today(),
            history = stockEvents.asReversed().map { it.entry(clock.zone()) },
            cost = costOf(stockEvents, recipeList, finished, brew.globals.cupSizeMl),
            rating = brew.tasting.rating,
            taste = DetailTasting.of(brew.tasting),
            recentSessions = finished.sortedByDescending { it.startedAtMs }.take(RECENT_SESSIONS).map { it.recent() },
        )
    }

    private fun costOf(
        stockEvents: List<InventoryEvent>,
        recipes: List<Recipe>,
        finished: List<BrewSession>,
        cupMl: Int,
    ) = CostRule.of(StockSummary.of(stockEvents), recipes.firstOrNull { it.isDefault }, finished, cupMl)

    private data class Globals(val lowStockGrams: Double, val deductStock: Boolean, val cupSizeMl: Int)

    private data class Brewed(
        val sessions: List<BrewSession>,
        val active: Boolean,
        val globals: Globals,
        val tasting: TeaTasting,
    ) {
        /** The leaf the active session of this tea deducts at *Sitzung beenden* (D-096), while deduction is on. */
        fun sessionLeaf(): Double? = sessions.takeIf { active && globals.deductStock }
            ?.filter { it.status == SessionStatus.IN_PROGRESS }
            ?.maxByOrNull { it.startedAtMs }
            ?.snapshot?.leafGrams
            ?.takeIf { it > 0.0 }
    }

    companion object {
        /** The navigation argument (the route's property name). */
        const val TEA_ID = "teaId"
    }
}

/** The session's day in its own time zone (the device's for one it doesn't know). */
private fun BrewSession.day(): LocalDate {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    return Instant.ofEpochMilli(startedAtMs).atZone(zone).toLocalDate()
}

/** A recipe row of REZEPTE: its key values, the vessel by name. */
private fun Recipe.summary(vesselNames: Map<String, String>) = RecipeSummary(
    id = id,
    name = name,
    isDefault = isDefault,
    method = method,
    vesselName = vesselId?.let(vesselNames::get),
    leafGrams = leafGrams,
    waterMl = waterMl,
    tempC = steps.firstOrNull { it.kind != StepKind.RINSE }?.tempC ?: baseTempC,
    infusions = steps.count { it.kind != StepKind.RINSE },
)

/** A stock event as a row of VORRAT's history, on its day in [zone] (SCR-29). */
private fun InventoryEvent.entry(zone: ZoneId) = StockEntry(
    id = id,
    kind = when (type) {
        InventoryEventType.INITIAL, InventoryEventType.PURCHASE -> StockEntryKind.PURCHASE
        InventoryEventType.ADJUSTMENT -> StockEntryKind.CORRECTION
        InventoryEventType.BREW -> StockEntryKind.BREW
    },
    deltaGrams = deltaGrams,
    priceCents = priceCents,
    day = Instant.ofEpochMilli(occurredAtMillis).atZone(zone).toLocalDate(),
    sessionId = sessionId.takeIf { type == InventoryEventType.BREW },
)

/** FRISCHE's label by FreshnessRule, with the date it stems from (INV-06, D-104). */
private fun detailFreshness(tea: Tea, remainingGrams: Double?, today: LocalDate): DetailFreshness? {
    val label = FreshnessRule.of(tea, remainingGrams, today) ?: return null
    val passed = tea.bestBefore?.takeIf { today.isAfter(it) }
    return DetailFreshness(label, openedDate = tea.openedDate.takeIf { passed == null }, bestBefore = passed)
}

/** Without a [CatalogReset] (tests that don't need it): never offered. */
private object NoCatalogReset : CatalogReset {
    override fun observePlan(teaId: String): Flow<CatalogResetPlan?> = flowOf(null)

    override suspend fun reset(teaId: String): CatalogResetResult = CatalogResetResult.NotInCatalog
}
