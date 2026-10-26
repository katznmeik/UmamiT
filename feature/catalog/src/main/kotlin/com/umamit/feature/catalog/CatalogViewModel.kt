package com.umamit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.GuideRepository
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * What Sammlung › Katalog shows (SCR-19, SCR-22).
 *
 * @property listing the catalog's teas as [filter] shows them; empty before the first refresh.
 * @property filter the search, filters and sort (CAT-10); the default view at first (D-073).
 * @property refresh the refresh's progress or result for the sheet.
 * @property loading true until the catalog was read once (then nothing is shown yet).
 * @property guideCount the *Ratgeber*'s guides (CAT-09); its row shows only when there are some.
 */
data class CatalogUiState(
    val listing: CatalogListing = CatalogListing.EMPTY,
    val filter: CatalogFilter = CatalogFilter.DEFAULT,
    val refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    val loading: Boolean = true,
    val guideCount: Int = 0,
) {
    val isRefreshing: Boolean get() = refresh is CatalogRefreshState.Running

    /** Never loaded (or emptied): SCR-19's empty state, whatever the filters. */
    val isEmpty: Boolean get() = listing.totalCount == 0
}

/**
 * Sammlung › Katalog's view model: the local catalog as the filters show it (CAT-10) and the
 * refresh started from it (CAT-07 AC7: only on a tap). The filters live as long as the screen's
 * view model; the list opens in the default view (D-073, D-083).
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    repository: CatalogRepository,
    private val refresh: CatalogRefreshRunner,
    guides: GuideRepository = GuideRepository.NONE,
) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter.DEFAULT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CatalogUiState> = combine(
        filter.flatMapLatest { repository.observeListing(it) },
        filter,
        refresh.state,
        guides.observeGuides().map { it.size },
    ) { listing, filter, refreshState, guideCount ->
        CatalogUiState(
            listing = listing,
            filter = filter,
            refresh = refreshState,
            loading = false,
            guideCount = guideCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogUiState())

    /** A changed search, filter or sort. */
    fun changeFilter(transform: (CatalogFilter) -> CatalogFilter) = filter.update(transform)

    /** *Katalog laden*. */
    fun startRefresh() = refresh.start()

    /** *Abbrechen*. */
    fun cancelRefresh() = refresh.cancel()

    /** The result sheet was closed. */
    fun acknowledgeResult() = refresh.acknowledge()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
