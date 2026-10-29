package com.umamit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.GuideRepository
import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What Sammlung › Katalog shows (SCR-19, SCR-22).
 *
 * @property listing the catalog's teas as [filter] shows them; empty before the first refresh.
 * @property filter the search, filters and sort (CAT-10); the default view at first (D-073).
 * @property refresh the refresh's progress or result for the sheet.
 * @property loading true until the catalog was read once (then nothing is shown yet).
 * @property guideCount the *Ratgeber*'s guides (CAT-09); its row shows only when there are some.
 * @property herbalPending the Kräutertees are still to come with the first refresh after the
 *   update (D-093 Q20): the *Kräutertee* view shows its hint with *Jetzt laden*.
 */
data class CatalogUiState(
    val listing: CatalogListing = CatalogListing.EMPTY,
    val filter: CatalogFilter = CatalogFilter.DEFAULT,
    val refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    val loading: Boolean = true,
    val guideCount: Int = 0,
    val herbalPending: Boolean = false,
) {
    val isRefreshing: Boolean get() = refresh is CatalogRefreshState.Running

    /** Never loaded (or emptied): SCR-19's empty state, whatever the filters and the kind shown. */
    val isEmpty: Boolean get() = listing.catalogCount == 0
}

/**
 * A tea from a shop link (CAT-17): its [sheet] while open, and the catalog key of a tea a link
 * [opened] (stored, refreshed or found), until its detail (SCR-21) is shown.
 */
data class TeaLinkState(val sheet: TeaLinkSheetState? = null, val opened: String? = null)

/**
 * Sammlung › Katalog's view model: the local catalog as the filters show it (CAT-10), the
 * refresh started from it (CAT-07 AC7: only on a tap) and a tea from a pasted or shared shop
 * link (CAT-17: only on the user's action, one at a time). The filters live as long as the
 * screen's view model; the list opens in the default view (D-073, D-083).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // the list, its refresh and a tea from a shop link, one function per action
class CatalogViewModel @Inject constructor(
    repository: CatalogRepository,
    private val refresh: CatalogRefreshRunner,
    guides: GuideRepository = GuideRepository.NONE,
    private val links: TeaLinkLoader = TeaLinkLoader.NONE,
) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter.DEFAULT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CatalogUiState> = combine(
        filter.flatMapLatest { repository.observeListing(it) },
        filter,
        refresh.state,
        guides.observeGuides().map { it.size },
        repository.observeHerbalPending(),
    ) { listing, filter, refreshState, guideCount, herbalPending ->
        CatalogUiState(
            listing = listing,
            filter = filter,
            refresh = refreshState,
            loading = false,
            guideCount = guideCount,
            herbalPending = herbalPending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogUiState())

    private val mutableLink = MutableStateFlow(TeaLinkState())

    /** The tea link's sheet and the tea to open (CAT-17). */
    val link: StateFlow<TeaLinkState> = mutableLink.asStateFlow()

    private var linkJob: Job? = null
    private var lastShare: Long? = null

    /** A changed search, filter or sort. */
    fun changeFilter(transform: (CatalogFilter) -> CatalogFilter) = filter.update(transform)

    /** *Katalog laden*. */
    fun startRefresh() = refresh.start()

    /** *Abbrechen*. */
    fun cancelRefresh() = refresh.cancel()

    /** The result sheet was closed. */
    fun acknowledgeResult() = refresh.acknowledge()

    /** *Link einfügen*: the paste sheet, empty (CAT-17 AC1). */
    fun openLinkSheet() {
        linkJob?.cancel()
        mutableLink.update { it.copy(sheet = TeaLinkSheetState()) }
    }

    /**
     * The clipboard's [text]: when it holds a tea link and the paste field is still empty, the
     * field shows that link (SCR-19). Anything else on the clipboard is never shown.
     */
    fun fillLinkFromClipboard(text: String?) {
        val link = text?.let(links::linkIn) ?: return
        mutableLink.update { state ->
            val sheet = state.sheet?.takeIf { !it.shared && !it.loading && it.text.isEmpty() } ?: return@update state
            state.copy(sheet = sheet.copy(text = link))
        }
    }

    /** The paste field changed: its message goes. */
    fun changeLinkText(text: String) = mutableLink.update { state ->
        val sheet = state.sheet?.takeIf { !it.loading } ?: return@update state
        state.copy(sheet = sheet.copy(text = text, message = null))
    }

    /** *Tee laden*: the pasted link. */
    fun loadLink() {
        val sheet = mutableLink.value.sheet?.takeIf { !it.shared && it.canLoad } ?: return
        load(sheet.text, sheet)
    }

    /** A link shared to the app (CAT-17 AC1): loaded at once, each [share] once. */
    fun loadSharedLink(share: TeaLinkShare) {
        if (share.token == lastShare) return
        lastShare = share.token
        load(share.text, TeaLinkSheetState(shared = true))
    }

    /** The sheet was closed: a link still loading is cancelled. */
    fun dismissLink() {
        linkJob?.cancel()
        linkJob = null
        mutableLink.update { it.copy(sheet = null) }
    }

    /** The detail of the tea a link opened is shown. */
    fun teaOpened() = mutableLink.update { it.copy(opened = null) }

    /** One link at a time: a link still loading is cancelled, and ends, before [text] is loaded. */
    private fun load(text: String, sheet: TeaLinkSheetState) {
        val previous = linkJob
        mutableLink.update { it.copy(sheet = sheet.copy(loading = true, message = null)) }
        linkJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            val outcome = links.load(text)
            mutableLink.update { state ->
                when (outcome) {
                    is TeaLinkOutcome.Opened -> TeaLinkState(sheet = null, opened = outcome.key)
                    else -> state.copy(sheet = state.sheet?.copy(loading = false, message = outcome))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
