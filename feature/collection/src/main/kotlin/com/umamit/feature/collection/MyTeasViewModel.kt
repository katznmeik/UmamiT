package com.umamit.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.CollectionListEntry
import com.umamit.core.data.CollectionListRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.domain.collection.CollectionBrowser
import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.CollectionRow
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TeaCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A tea of *Meine Tees* as its row shows it (TEA-08, D-100): cover, name, secondary name, Sorte, the stock chip
 * („38 g", *Knapp*, *Leer*; none without stock events), the favourite star and „Rezept prüfen". An archived tea
 * ([isArchived], under *Archiviert*) offers *Wiederherstellen*.
 *
 * @property photoPath the cover photo's file, or null for the leaf placeholder (03 §3.5).
 */
data class MyTeaRow(
    val id: String,
    val name: String,
    val secondaryName: String?,
    val sorte: String?,
    val category: TeaCategory,
    val remainingGrams: Double?,
    val stockLevel: StockLevel?,
    val isFavorite: Boolean,
    val checkRecipe: Boolean,
    val isArchived: Boolean,
    val photoPath: String?,
)

/** The empty states of *Meine Tees* (TEA-08): no tea yet (A7), or none that matches (A10). */
enum class MyTeasEmpty {
    /** „Noch keine Tees" · *Im Katalog stöbern* (A7). */
    NO_TEAS,

    /** „Kein Tee passt zu deiner Suche." (A10). */
    NO_MATCH,
}

/**
 * What *Meine Tees* (SCR-08) shows.
 *
 * @property rows the teas the [filter] and [sort] show, in order.
 * @property categories the *Kategorien* the *Kategorie* chip offers: only those present (TEA-10).
 * @property sorten the *Sorten* the *Sorte* chip offers: only those present.
 * @property ownedCount the teas that are not archived.
 * @property archivedCount the archived teas.
 */
data class MyTeasUiState(
    val loading: Boolean = true,
    val rows: List<MyTeaRow> = emptyList(),
    val filter: CollectionFilter = CollectionFilter(),
    val sort: CollectionSort = CollectionSort.LAST_BREWED,
    val categories: List<TeaCategory> = emptyList(),
    val sorten: List<String> = emptyList(),
    val ownedCount: Int = 0,
    val archivedCount: Int = 0,
) {
    /**
     * The empty state, or null for the list: „Noch keine Tees" while no tea is in the collection and no search
     * or chip is on; „Kein Tee passt zu deiner Suche." when they leave nothing.
     */
    val empty: MyTeasEmpty?
        get() = when {
            loading -> null
            ownedCount == 0 && !filter.isActive -> MyTeasEmpty.NO_TEAS
            rows.isEmpty() -> MyTeasEmpty.NO_MATCH
            else -> null
        }

    /** The search, chips and sort show once any tea exists, archived ones too: *Archiviert* reaches them. */
    val showControls: Boolean get() = ownedCount + archivedCount > 0
}

/**
 * *Meine Tees* (SCR-08; TEA-08–TEA-11, D-100): the collection's teas through [CollectionBrowser]'s in-memory
 * search, filters and sort. The sort is remembered (`collectionSort`, 05 §6); the search and the chips are not:
 * the list opens unfiltered. *Wiederherstellen* brings an archived tea back (TeaArchive).
 */
@HiltViewModel
class MyTeasViewModel @Inject constructor(
    list: CollectionListRepository,
    private val settings: SettingsRepository,
    private val archive: TeaArchive,
    private val photos: PhotoFiles,
) : ViewModel() {
    private val filter = MutableStateFlow(CollectionFilter())

    // The photo files are looked up once per change of the collection, not per keystroke.
    private val rows = list.observeRows().map { entries -> entries.map { it.withPhoto() } }

    val state: StateFlow<MyTeasUiState> = combine(
        rows,
        filter,
        settings.settings.map { it.collectionSort }.distinctUntilChanged(),
    ) { all, filter, sort ->
        val listing = CollectionBrowser.listing(all.map { it.first }, filter, sort)
        val paths = all.associate { it.first.tea.id to it.second }
        MyTeasUiState(
            loading = false,
            rows = listing.rows.map { it.toUi(paths[it.tea.id]) },
            filter = filter,
            sort = sort,
            categories = listing.categories,
            sorten = listing.sorten,
            ownedCount = listing.ownedCount,
            archivedCount = all.size - listing.ownedCount,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MyTeasUiState())

    /** A change of the search or the chips (TEA-09, TEA-10). */
    fun updateFilter(change: (CollectionFilter) -> CollectionFilter) = filter.update(change)

    /** *Filter zurücksetzen* after „Kein Tee passt zu deiner Suche.": the search and every chip off. */
    fun resetFilters() {
        filter.value = CollectionFilter()
    }

    /** The sort (TEA-11), remembered as `collectionSort` across restarts. */
    fun setSort(sort: CollectionSort) {
        viewModelScope.launch { settings.update { it.copy(collectionSort = sort) } }
    }

    /** *Wiederherstellen* (D-100): the archived tea is back in Start, the picker and the list. */
    fun restore(teaId: String) {
        viewModelScope.launch { archive.restore(teaId) }
    }

    private fun CollectionListEntry.withPhoto(): Pair<CollectionRow, String?> =
        row to coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path

    private fun CollectionRow.toUi(photoPath: String?) = MyTeaRow(
        id = tea.id,
        name = tea.name,
        secondaryName = tea.secondaryName?.takeIf(String::isNotBlank),
        sorte = tea.teaType?.takeIf(String::isNotBlank),
        category = tea.category,
        remainingGrams = remainingGrams,
        stockLevel = stockLevel,
        isFavorite = tea.isFavorite,
        checkRecipe = tea.recipeCheckReason != null,
        isArchived = tea.isArchived,
        photoPath = photoPath,
    )
}
