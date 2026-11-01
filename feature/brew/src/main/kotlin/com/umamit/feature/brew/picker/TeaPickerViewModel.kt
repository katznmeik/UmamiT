package com.umamit.feature.brew.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaRepository
import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A tea's kind, the picker's group (SCR-03, D-097), in the order the groups show: every green
 * style is a „Grüntee", style `herbal` a „Kräutertee". M3's own teas will add more kinds.
 */
enum class TeaKind {
    GREEN,
    HERBAL,
    ;

    companion object {
        /** The kind from the tea's style; a tea without a style counts by its category. */
        fun of(tea: Tea): TeaKind = when {
            tea.styleKey == HERBAL_STYLE -> HERBAL
            tea.styleKey == null && tea.category == TeaCategory.HERBAL -> HERBAL
            else -> GREEN
        }

        private const val HERBAL_STYLE = "herbal"
    }
}

/**
 * A tea of the picker: its name, type and cover photo file (null: the leaf placeholder, 03 §3.5).
 * [removable] is false for the tea of the active brew session: *Archivieren* is greyed
 * out with „Erst die Sitzung beenden" (TEA-04, E15). [kind] is its group in *Alle* (D-097).
 */
data class PickerTea(
    val id: String,
    val name: String,
    val teaType: String?,
    val photoPath: String?,
    val removable: Boolean = true,
    val kind: TeaKind = TeaKind.GREEN,
)

/** The teas of one [kind] in *Alle* or the search results, in their order (A–Z; SCR-03, D-097). */
data class PickerGroup(val kind: TeaKind, val teas: List<PickerTea>)

/** The teas split by kind: groups in [TeaKind] order, empty ones left out, each keeping the teas' order. */
internal fun List<PickerTea>.groupedByKind(): List<PickerGroup> {
    val byKind = groupBy { it.kind }
    return TeaKind.entries.mapNotNull { kind -> byKind[kind]?.let { PickerGroup(kind, it) } }
}

/**
 * What the tea picker (SCR-03) shows.
 *
 * @property recent *Zuletzt*: the teas brewed last, distinct, newest first.
 * @property favorites *Favoriten*.
 * @property all *Alle*: every tea of the collection that is not archived, by name.
 * @property query the search text as typed.
 * @property results the search hits; null while no search is active (then the sections show).
 * @property removed the tea removed last, while its snackbar with *Rückgängig* shows (TEA-04, D-093 Q21).
 */
data class TeaPickerUiState(
    val recent: List<PickerTea> = emptyList(),
    val favorites: List<PickerTea> = emptyList(),
    val all: List<PickerTea> = emptyList(),
    val query: String = "",
    val results: List<PickerTea>? = null,
    val loading: Boolean = true,
    val removed: PickerTea? = null,
) {
    /** The collection has no tea to brew (GEN-09); only *Ohne Tee (Schnelltimer)* is left. */
    val isEmpty: Boolean get() = !loading && all.isEmpty()

    /** *Alle* split by the teas' kind (D-097). */
    val allGroups: List<PickerGroup> get() = all.groupedByKind()

    /** [results] split the same way; null while no search is active. */
    val resultGroups: List<PickerGroup>? get() = results?.groupedByKind()

    /** *Alle* shows its group headers only when it holds teas of more than one kind (D-097). */
    val showGroupHeaders: Boolean get() = allGroups.size > 1

    /** The search results show group headers only when they hold teas of more than one kind (D-097). */
    val showResultHeaders: Boolean get() = (resultGroups?.size ?: 0) > 1
}

/**
 * The tea picker's view model (SCR-03, BRW-01): the non-archived teas of the
 * collection in three sections, and an instant local search (GEN-10:
 * case- and diacritics-insensitive word prefixes, debounced 150 ms). A long
 * press removes a tea from the collection: archived, with undo, except the
 * active brew session's (TEA-04, E15).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class TeaPickerViewModel @Inject constructor(
    teas: TeaRepository,
    sessions: SessionRepository,
    private val archive: TeaArchive,
    private val photos: PhotoFiles,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val removed = MutableStateFlow<PickerTea?>(null)

    private val input = combine(query.asStateFlow(), query.debounce(SEARCH_DEBOUNCE_MS), removed, ::Input)

    val state: StateFlow<TeaPickerUiState> = combine(
        teas.observeOwnedTeas(),
        sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW),
        archive.observeActiveTeaId(),
        input,
    ) { owned, recentSessions, activeTeaId, input ->
        val all = owned.map { it.toPickerTea(removable = it.tea.id != activeTeaId) }
        val byId = all.associateBy { it.id }
        val search = SearchQuery(input.search)
        TeaPickerUiState(
            recent = recentSessions.mapNotNull { it.snapshot.teaId }.distinct()
                .mapNotNull { byId[it] }
                .take(RECENT_TEAS),
            favorites = owned.filter { it.tea.isFavorite }.mapNotNull { byId[it.tea.id] },
            all = all,
            query = input.typed,
            results = if (input.search.isBlank() || input.typed.isBlank()) null else all.filter { it.matches(search) },
            loading = false,
            removed = input.removed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TeaPickerUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    /**
     * *Archivieren* (TEA-04, SCR-03): archives the tea at once, without a dialog; the
     * snackbar then offers *Rückgängig*. The active brew session's tea is refused and stays (E15).
     */
    fun remove(teaId: String) {
        val tea = state.value.all.firstOrNull { it.id == teaId }?.takeIf { it.removable } ?: return
        viewModelScope.launch {
            if (archive.archive(teaId) == ArchiveResult.Archived) removed.value = tea
        }
    }

    /** *Rückgängig*: the tea removed last is back where it was. */
    fun undoRemove() {
        val tea = removed.value ?: return
        removed.value = null
        viewModelScope.launch { archive.restore(tea.id) }
    }

    /** The snackbar timed out, or the picker closed: the tea stays removed (the catalog brings it back). */
    fun dismissRemoved() {
        removed.value = null
    }

    private fun TeaOverview.toPickerTea(removable: Boolean) = PickerTea(
        id = tea.id,
        name = tea.name,
        teaType = tea.teaType,
        photoPath = coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path,
        removable = removable,
        kind = TeaKind.of(tea),
    )

    private data class Input(val typed: String, val search: String, val removed: PickerTea?)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val RECENT_TEAS = 3

        /** Enough recent sessions to find a few distinct teas. */
        const val RECENT_SESSIONS_WINDOW = 50
    }
}

/** GEN-10: every word of the [search] starts a word of the name, or the type (as a whole). */
private fun PickerTea.matches(search: SearchQuery): Boolean =
    search.matches(SearchQuery.words(name) + listOfNotNull(teaType?.let(SearchQuery::normalized)))
