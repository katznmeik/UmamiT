package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.SystemWallClock
import com.umamit.core.common.WallClock
import com.umamit.core.data.AddPhotoResult
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.DeleteResult
import com.umamit.core.data.JournalRepository
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
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.domain.collection.FreshnessRule
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

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
}

/**
 * The tea detail (SCR-09, TEA-13, D-100) of the tea `teaId`: everything it shows comes from flows, so it
 * updates at once after an edit, a brew or a correction. ⋮ *Favorit*, *Archivieren*, *Wiederherstellen* and
 * *Löschen* write through the repositories; archive and delete go through [TeaArchive], which refuses the tea of
 * the active brew session (E15). *Bestand korrigieren* is the stock sheet's save, *Nachkauf erfassen* the purchase
 * sheet's (INV-03). *+ Foto* adds the
 * picked image through [TeaPhotos] (TEA-05, SCR-28).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList") // the repositories the detail reads
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
) : ViewModel() {
    private val teaId: String = checkNotNull(savedState[TEA_ID]) { "the tea detail needs a teaId" }

    private val events = Channel<TeaDetailEvent>(Channel.BUFFERED)

    /** Archive and delete outcomes, once each. */
    val navigation: Flow<TeaDetailEvent> = events.receiveAsFlow()

    private val brewed: Flow<Brewed> = combine(
        journal.observeSessions().map { list -> list.map { it.session }.filter { it.snapshot.teaId == teaId } },
        archive.observeActiveTeaId().map { it == teaId }.distinctUntilChanged(),
        settings.settings.map { Globals(it.lowStockGrams, it.deductStock) }.distinctUntilChanged(),
    ) { sessions, active, globals -> Brewed(sessions, active, globals) }

    /** INV-09's link from the tea's catalog key, its catalog tea and the size bought last. */
    private val reorderUrl: Flow<String?> = combine(
        teas.observeTea(teaId).map { CatalogLink(it?.tea?.catalogKey, it?.tea?.productUrl) }.distinctUntilChanged(),
        stock.observeEvents(teaId),
        ::Pair,
    ).flatMapLatest { (link, events) -> reorderUrl(link, events) }

    val state: StateFlow<TeaDetailUiState> = combine(
        teas.observeTea(teaId),
        combine(recipes.observeRecipes(teaId), vessels.observeVessels(), teaPhotos.observePhotos(teaId), ::Triple),
        stock.observeEvents(teaId),
        brewed,
        reorderUrl,
    ) { overview, (recipeList, vesselList, photoList), stockEvents, brew, reorder ->
        if (overview == null) {
            TeaDetailUiState.Gone
        } else {
            TeaDetailUiState.Ready(
                detail(overview, recipeList, vesselList, stockEvents, brew, reorder).withPhotos(overview, photoList),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TeaDetailUiState.Loading)

    /** ⋮ *Favorit* (TEA-06): marks or unmarks the tea. */
    fun toggleFavorite() {
        viewModelScope.launch {
            val tea = teas.getTea(teaId) ?: return@launch
            teas.saveTea(tea.copy(isFavorite = !tea.isFavorite))
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
        viewModelScope.launch { stock.correctStock(teaId, grams) }
    }

    /**
     * The history's *Löschen*, after its confirmation (INV-03, SCR-29, D-104): a purchase or a correction goes and
     * the stock, the price and the cost follow from the events; a brew is refused by the repository (D-102). The
     * opening date stays as it is.
     */
    fun deleteStockEntry(eventId: String) {
        viewModelScope.launch { stock.deleteEvent(eventId) }
    }

    /**
     * *Nachkauf erfassen* (INV-03, D-104): one PURCHASE of the sheet's grams, price and day; the stock follows from
     * the events. A day after today (the clock moved back since the sheet opened) is refused and nothing is written.
     */
    fun recordPurchase(purchase: TeaPurchase) {
        if (purchase.purchasedOn.isAfter(clock.today())) return
        viewModelScope.launch { stock.recordPurchase(teaId, purchase) }
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
            reorder = reorderUrl?.let {
                Reorder(
                    it,
                    prominent =
                    stockEvents.isNotEmpty() && level != StockLevel.AVAILABLE,
                )
            },
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
                openedDate = tea.openedDate,
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
        )
    }

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

    private data class Globals(val lowStockGrams: Double, val deductStock: Boolean)

    private data class Brewed(val sessions: List<BrewSession>, val active: Boolean, val globals: Globals) {
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

private fun WallClock.today(): LocalDate = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate()

/** FRISCHE's label by FreshnessRule, with the date it stems from (INV-06, D-104). */
private fun detailFreshness(tea: Tea, remainingGrams: Double?, today: LocalDate): DetailFreshness? {
    val label = FreshnessRule.of(tea, remainingGrams, today) ?: return null
    val passed = tea.bestBefore?.takeIf { today.isAfter(it) }
    return DetailFreshness(label, openedDate = tea.openedDate.takeIf { passed == null }, bestBefore = passed)
}
