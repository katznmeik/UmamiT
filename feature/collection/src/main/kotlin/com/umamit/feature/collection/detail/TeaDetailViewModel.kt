package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.DeleteResult
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
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
}

/**
 * The tea detail (SCR-09, TEA-13, D-100) of the tea `teaId`: everything it shows comes from flows, so it
 * updates at once after an edit, a brew or a correction. ⋮ *Favorit*, *Archivieren*, *Wiederherstellen* and
 * *Löschen* write through the repositories; archive and delete go through [TeaArchive], which refuses the tea of
 * the active brew session (E15). *Bestand korrigieren* is the stock sheet's save (INV-03).
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
        combine(recipes.observeRecipes(teaId), vessels.observeVessels(), ::Pair),
        stock.observeEvents(teaId),
        brewed,
        reorderUrl,
    ) { overview, (recipeList, vesselList), stockEvents, brew, reorder ->
        if (overview == null) {
            TeaDetailUiState.Gone
        } else {
            TeaDetailUiState.Ready(detail(overview, recipeList, vesselList, stockEvents, brew, reorder))
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
            stockLevel = level,
            recipes = recipeList.sortedBy { it.position }.map { it.summary(vesselNames) },
            reorder = reorderUrl?.let { Reorder(it, prominent = level != StockLevel.AVAILABLE) },
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
                isOrganic = tea.isOrganic,
                isFlavored = tea.isFlavored,
            ),
            isFavorite = tea.isFavorite,
            isArchived = tea.isArchived,
            inActiveBrew = brew.active,
            sessionLeafGrams = brew.sessionLeaf(),
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

    private fun BrewSession.day(): LocalDate {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochMilli(startedAtMs).atZone(zone).toLocalDate()
    }

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
