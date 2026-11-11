package com.umamit.feature.journal

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.umamit.core.domain.search.SearchQuery

/**
 * The journal's search and filters (JRN-09, D-112), all optional: the [search] text, one tea ([teaKey], as in
 * [JournalEntry.teaKey]), a minimum rating ([minRating]) and *Mit Notizen* ([withNotes]). Kept while the app
 * runs, not across app starts.
 */
data class JournalFilter(
    val search: String = "",
    val teaKey: String? = null,
    val minRating: Double? = null,
    val withNotes: Boolean = false,
) {
    /** A filter (not the search) is set: the dot on the filter icon and the chips. */
    val hasFilters: Boolean get() = teaKey != null || minRating != null || withNotes

    /** A search or a filter is set: the in-progress session is not pinned (JRN-09). */
    val isActive: Boolean get() = hasFilters || !SearchQuery(search).isEmpty

    /** *Zurücksetzen* in the sheet: the filters are cleared, the search stays. */
    fun withoutFilters(): JournalFilter = copy(teaKey = null, minRating = null, withNotes = false)

    companion object {
        /** The rating steps the sheet offers: *ab 3 ★*, *ab 4 ★*, *ab 4,5 ★*. */
        val RATING_STEPS: List<Double> = listOf(3.0, 4.0, 4.5)

        /** Survives a tab switch, a visit to a session page and a rotation; not an app restart. */
        val Saver: Saver<JournalFilter, Any> = listSaver(
            save = { listOf(it.search, it.teaKey.orEmpty(), it.minRating ?: NO_RATING, it.withNotes) },
            restore = {
                JournalFilter(
                    search = it[0] as String,
                    teaKey = (it[1] as String).ifEmpty { null },
                    minRating = (it[2] as Double).takeIf { rating -> rating > NO_RATING },
                    withNotes = it[3] as Boolean,
                )
            },
        )

        private const val NO_RATING = 0.0
    }
}

/** A tea the filter sheet offers: [key] as in [JournalEntry.teaKey], [name] as its latest session recorded it. */
data class JournalTeaOption(val key: String, val name: String)

/** [entry] passes the search (word prefixes of its tea, notes and descriptors, GEN-10) and every filter. */
internal fun JournalFilter.accepts(entry: JournalEntry, query: SearchQuery = SearchQuery(search)): Boolean =
    (query.isEmpty || query.matches(entry.searchWords)) &&
        (teaKey == null || entry.teaKey == teaKey) &&
        (minRating == null || (entry.rating != null && entry.rating >= minRating)) &&
        (!withNotes || entry.hasNotes)

/**
 * The journal under [filter] (JRN-09): the finished sessions that pass, the day groups kept (an emptied day is
 * dropped), and no in-progress session while a search or filter is active. Pure; the list itself when nothing is set.
 */
fun JournalList.filtered(filter: JournalFilter): JournalList {
    if (!filter.isActive) return this
    val query = SearchQuery(filter.search)
    val days = days.mapNotNull { group ->
        val entries = group.entries.filter { filter.accepts(it, query) }
        if (entries.isEmpty()) null else group.copy(entries = entries)
    }
    return JournalList(inProgress = emptyList(), days = days)
}

/** How many finished sessions the list shows: the result count TalkBack announces. */
val JournalList.sessionCount: Int get() = days.sumOf { it.entries.size }

/** The teas that have finished sessions, by name; a deleted tea by its recorded name (JRN-09). */
fun JournalList.teaOptions(): List<JournalTeaOption> = days
    .asSequence()
    .flatMap { it.entries.asSequence() }
    .distinctBy { it.teaKey }
    .map { JournalTeaOption(it.teaKey, it.teaName) }
    .sortedBy { SearchQuery.normalized(it.name) }
    .toList()
