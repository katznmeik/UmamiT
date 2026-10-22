package com.umamit.feature.brew.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.TeaRepository
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
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** A tea of the picker: its name, type and cover photo file (null: the leaf placeholder, 03 §3.5). */
data class PickerTea(val id: String, val name: String, val teaType: String?, val photoPath: String?)

/**
 * What the tea picker (SCR-03) shows.
 *
 * @property recent *Zuletzt*: the teas brewed last, distinct, newest first.
 * @property favorites *Favoriten*.
 * @property all *Alle*: every tea of the collection that is not archived, by name.
 * @property query the search text as typed.
 * @property results the search hits; null while no search is active (then the sections show).
 */
data class TeaPickerUiState(
    val recent: List<PickerTea> = emptyList(),
    val favorites: List<PickerTea> = emptyList(),
    val all: List<PickerTea> = emptyList(),
    val query: String = "",
    val results: List<PickerTea>? = null,
    val loading: Boolean = true,
) {
    /** The collection has no tea to brew (GEN-09); only *Ohne Tee (Schnelltimer)* is left. */
    val isEmpty: Boolean get() = !loading && all.isEmpty()
}

/**
 * The tea picker's view model (SCR-03, BRW-01): the non-archived teas of the
 * collection in three sections, and an instant local search (GEN-10:
 * case- and diacritics-insensitive word prefixes, debounced 150 ms).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class TeaPickerViewModel @Inject constructor(
    teas: TeaRepository,
    sessions: SessionRepository,
    private val photos: PhotoFiles,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<TeaPickerUiState> = combine(
        teas.observeTeas(),
        sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW),
        query.asStateFlow(),
        query.debounce(SEARCH_DEBOUNCE_MS),
    ) { overviews, recentSessions, typed, search ->
        val owned = overviews.filter { !it.tea.isArchived }
        val byId = owned.associateBy { it.tea.id }
        val all = owned.map { it.toPickerTea() }
        val term = normalized(search.trim())
        TeaPickerUiState(
            recent = recentSessions.mapNotNull { it.snapshot.teaId }.distinct()
                .mapNotNull { byId[it]?.toPickerTea() }
                .take(RECENT_TEAS),
            favorites = owned.filter { it.tea.isFavorite }.map { it.toPickerTea() },
            all = all,
            query = typed,
            results = if (term.isEmpty() || typed.isBlank()) null else all.filter { it.matches(term) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TeaPickerUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    private fun TeaOverview.toPickerTea() = PickerTea(
        id = tea.id,
        name = tea.name,
        teaType = tea.teaType,
        photoPath = coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val RECENT_TEAS = 3

        /** Enough recent sessions to find a few distinct teas. */
        const val RECENT_SESSIONS_WINDOW = 50
    }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WORD_BREAK = Regex("[\\s\\-–/·,()]+")

/** Lower case without diacritics: „Kōridashi" → „koridashi" (GEN-10). */
internal fun normalized(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.GERMAN)

/** A word of the name starts with [term] (already [normalized]); several words must each match a word. */
private fun PickerTea.matches(term: String): Boolean {
    val words = normalized(name).split(WORD_BREAK) + listOfNotNull(teaType?.let(::normalized))
    return term.split(WORD_BREAK).filter { it.isNotEmpty() }.all { part -> words.any { it.startsWith(part) } }
}
