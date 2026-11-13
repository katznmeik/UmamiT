package com.umamit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogBookmarkRepository
import com.umamit.core.data.catalog.CatalogFoldKey
import com.umamit.core.data.catalog.CatalogFoldRepository
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.GuideRepository
import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogSort
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
 * @property pendingKinds the kinds still to come with the first refresh after the update (D-093 Q20,
 *   D-108): each one's view shows its hint with *Jetzt laden*.
 * @property folded the folded *Sorte* groups of every kind's view and the folded kind groups of the view without a
 *   kind, as `catalogFolded` keys (CAT-10, D-098, D-114).
 */
data class CatalogUiState(
    val listing: CatalogListing = CatalogListing.EMPTY,
    val filter: CatalogFilter = CatalogFilter.DEFAULT,
    val refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    val loading: Boolean = true,
    val guideCount: Int = 0,
    val pendingKinds: Set<CatalogKind> = emptySet(),
    val folded: Set<String> = emptySet(),
) {
    val isRefreshing: Boolean get() = refresh is CatalogRefreshState.Running

    /** Never loaded (or emptied): SCR-19's empty state, whatever the filters and the kind shown. */
    val isEmpty: Boolean get() = listing.catalogCount == 0

    /**
     * The groups fold (D-098, D-114): a kind's *Sorte* groups in its grouped view, the kind groups in every sort of
     * the view without a kind; only in a loaded catalog, never in *Gemerkt* or a kind's flat sort order, and not while
     * a search is typed (it shows every hit unfolded).
     */
    val foldable: Boolean
        get() = !loading && !isEmpty && !filter.marked && filter.search.isBlank() &&
            (filter.kind == null || filter.sort == CatalogSort.TYPE)

    /** Whether the group of [teaType] (null = „Ohne Sorte") is folded in the view of a kind. */
    fun isFolded(teaType: String?): Boolean =
        foldable && filter.kind?.let { CatalogFoldKey.of(it, teaType) in folded } == true

    /** Whether [kind]'s group is folded in the view without a kind (D-114). */
    fun isKindFolded(kind: CatalogKind): Boolean =
        foldable && filter.kind == null && CatalogFoldKey.ofKind(kind) in folded
}

/**
 * A tea from a shop link (CAT-17): its [sheet] while open, and the catalog key of a tea a link
 * [opened] (stored, refreshed or found), until its detail (SCR-21) is shown, with the [notice] why
 * its page didn't refresh it, if so (CAT-17 AC6, D-096), shown as the detail opens.
 */
data class TeaLinkState(
    val sheet: TeaLinkSheetState? = null,
    val opened: String? = null,
    val notice: TeaLinkOutcome? = null,
)

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
    private val bookmarks: CatalogBookmarkRepository = CatalogBookmarkRepository.NONE,
    private val folds: CatalogFoldRepository = CatalogFoldRepository.NONE,
) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter.DEFAULT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CatalogUiState> = combine(
        filter.flatMapLatest { repository.observeListing(it) },
        filter,
        refresh.state,
        guides.observeGuides().map { it.size },
        combine(repository.observePendingKinds(), folds.observeFolded(), ::Pair),
    ) { listing, filter, refreshState, guideCount, (pendingKinds, folded) ->
        CatalogUiState(
            listing = listing,
            filter = filter,
            refresh = refreshState,
            loading = false,
            guideCount = guideCount,
            pendingKinds = pendingKinds,
            folded = folded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogUiState())

    private val mutableLink = MutableStateFlow(TeaLinkState())

    /** The tea link's sheet and the tea to open (CAT-17). */
    val link: StateFlow<TeaLinkState> = mutableLink.asStateFlow()

    private var linkJob: Job? = null
    private var lastShare: Long? = null

    /**
     * A changed search, filter or sort. A kind change keeps only the taste notes and scales the new view's range has
     * (D-108 point 8), whichever control changed the kind.
     */
    fun changeFilter(transform: (CatalogFilter) -> CatalogFilter) = filter.update { old ->
        val new = transform(old)
        val listing = state.value.listing
        if (new.kind == old.kind || listing.flavoursByKind.isEmpty()) new else new.keepingFlavoursOf(listing)
    }

    /** A row's bookmark toggle (CAT-18): marks or unmarks the tea [key]. */
    fun toggleMark(key: String, marked: Boolean) {
        viewModelScope.launch { if (marked) bookmarks.mark(key) else bookmarks.unmark(key) }
    }

    /** A tap on the header of [teaType]'s group (null = „Ohne Sorte"): folds or unfolds it in a kind's view (D-098). */
    fun toggleFold(teaType: String?) {
        val kind = state.value.takeIf { it.foldable }?.filter?.kind ?: return
        toggle(CatalogFoldKey.of(kind, teaType))
    }

    /** A tap on [kind]'s group header in the view without a kind: folds or unfolds it (D-114). */
    fun toggleKindFold(kind: CatalogKind) {
        if (state.value.takeIf { it.foldable }?.filter?.kind != null) return
        toggle(CatalogFoldKey.ofKind(kind))
    }

    private fun toggle(key: String) {
        viewModelScope.launch { folds.update { if (key in it) it - key else it + key } }
    }

    /**
     * ⋮ *Alle einklappen*: every *Sorte* of the kind's view shown, those the filters hide and „Ohne Sorte" included,
     * so a changed filter shows no group open; without a kind every kind group (D-114). The other views keep their
     * folding (D-098).
     */
    fun foldAll() {
        val shown = state.value.takeIf { it.foldable } ?: return
        val kind = shown.filter.kind
        val keys = if (kind == null) {
            CatalogKind.ALL.map(CatalogFoldKey::ofKind)
        } else {
            (shown.listing.types + shown.listing.sections.map { it.teaType } + null).map { CatalogFoldKey.of(kind, it) }
        }.toSet()
        viewModelScope.launch { folds.update { it + keys } }
    }

    /** ⋮ *Alle ausklappen*: every group of the view shown, also keys of a *Sorte* that is gone (D-098, D-114). */
    fun unfoldAll() {
        val shown = state.value.takeIf { it.foldable } ?: return
        val prefix = shown.filter.kind?.let(CatalogFoldKey::prefix) ?: CatalogFoldKey.ALL_PREFIX
        viewModelScope.launch { folds.update { folded -> folded.filterNot { it.startsWith(prefix) }.toSet() } }
    }

    /** *Katalog laden*. */
    fun startRefresh() = refresh.start()

    /** *Abbrechen*. */
    fun cancelRefresh() = refresh.cancel()

    /** The result sheet was closed. */
    fun acknowledgeResult() = refresh.acknowledge()

    /** A refresh notification was tapped: the last result from the log when nothing is held (SCR-22, D-097). */
    suspend fun recallRefreshResult() = refresh.recallLastResult()

    /** Whether the refresh sheet has something to show: a running refresh or a result. */
    fun refreshShowing(): Boolean = refresh.state.value != CatalogRefreshState.Idle

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
    fun teaOpened() = mutableLink.update { it.copy(opened = null, notice = null) }

    /** One link at a time: a link still loading is cancelled, and ends, before [text] is loaded. */
    private fun load(text: String, sheet: TeaLinkSheetState) {
        val previous = linkJob
        mutableLink.update { it.copy(sheet = sheet.copy(loading = true, message = null)) }
        linkJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            val outcome = links.load(text)
            mutableLink.update { state ->
                when (outcome) {
                    is TeaLinkOutcome.Opened ->
                        TeaLinkState(sheet = null, opened = outcome.key, notice = outcome.notRefreshed)

                    else -> state.copy(sheet = state.sheet?.copy(loading = false, message = outcome))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
