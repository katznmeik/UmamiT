package com.umamit.core.testing

import com.umamit.core.data.catalog.CatalogBookmarkRepository
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.domain.catalog.CatalogBrowser
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.CatalogTeaSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CatalogRepository]: tests set [catalog] and the [details] by key. The teas' marks
 * come from [bookmarks] (CAT-18), besides the ones a test sets; a discontinued tea in [catalog]
 * shows only in the *Gemerkt* view, as with the database.
 */
class FakeCatalogRepository(
    initial: CatalogOverview = CatalogOverview(emptyList(), null),
    details: List<CatalogTeaDetail> = emptyList(),
    val bookmarks: FakeCatalogBookmarkRepository = FakeCatalogBookmarkRepository(),
) : CatalogRepository {
    val catalog = MutableStateFlow(initial)

    val details = MutableStateFlow(details.associateBy { it.key })

    override fun observeCatalog(): Flow<CatalogOverview> = catalog

    /** The real filters on [catalog]; the search matches words of the name (the database's index is not faked). */
    override fun observeListing(filter: CatalogFilter): Flow<CatalogListing> =
        combine(catalog, bookmarks.marked) { overview, marked ->
            val teas = overview.teas.map { if (it.key in marked) it.copy(marked = true) else it }
            val words = filter.search.lowercase().split(' ').filter { it.isNotBlank() }
            val hits = words.takeIf { it.isNotEmpty() }?.let {
                teas.filter { tea -> words.all { word -> word in tea.name.lowercase() } }.map { it.key }.toSet()
            }
            CatalogBrowser.listing(teas, filter, hits, overview.lastRefreshAtMillis)
        }

    override fun observeLastRefresh(): Flow<Long?> = catalog.map { it.lastRefreshAtMillis }

    override fun observeTea(key: String): Flow<CatalogTeaDetail?> = details.map { it[key] }

    /** The Kräutertees still to come (D-093 Q20); tests set it. */
    val herbalPending = MutableStateFlow(false)

    override fun observeHerbalPending(): Flow<Boolean> = herbalPending
}

/** In-memory Merkliste (CAT-18): tests read and set [marked]. */
class FakeCatalogBookmarkRepository(initial: Set<String> = emptySet()) : CatalogBookmarkRepository {
    val marked = MutableStateFlow(initial)

    override fun observeMarked(): Flow<Set<String>> = marked

    override suspend fun isMarked(catalogKey: String): Boolean = catalogKey in marked.value

    override suspend fun mark(catalogKey: String) = marked.update { it + catalogKey }

    override suspend fun unmark(catalogKey: String) = marked.update { it - catalogKey }
}

/** In-memory [CatalogRefreshRunner]: counts the calls; tests drive [state]. */
class FakeCatalogRefreshRunner(initial: CatalogRefreshState = CatalogRefreshState.Idle) : CatalogRefreshRunner {
    override val state = MutableStateFlow(initial)

    var starts = 0
        private set

    var cancels = 0
        private set

    var acknowledgements = 0
        private set

    /** What [recallLastResult] reads from the refresh log; null for nothing to recall. */
    var lastResult: CatalogRefreshOutcome? = null

    var recalls = 0
        private set

    override fun start() {
        starts++
    }

    override fun cancel() {
        cancels++
    }

    override fun acknowledge() {
        acknowledgements++
        if (state.value is CatalogRefreshState.Finished) state.value = CatalogRefreshState.Idle
    }

    override suspend fun recallLastResult() {
        recalls++
        val last = lastResult ?: return
        if (state.value == CatalogRefreshState.Idle) state.value = CatalogRefreshState.Finished(last)
    }
}

/** A catalog tea for tests: invented names (D-016). */
fun catalogTea(
    key: String,
    name: String,
    teaType: String? = "Sencha",
    basePriceCentsPer100g: Long? = 1240,
    thumbnailPath: String? = null,
    secondaryName: String? = null,
) = CatalogTeaSummary(
    key = key,
    name = name,
    secondaryName = secondaryName,
    teaType = teaType,
    basePriceCentsPer100g = basePriceCentsPer100g,
    available = true,
    thumbnailPath = thumbnailPath,
)
