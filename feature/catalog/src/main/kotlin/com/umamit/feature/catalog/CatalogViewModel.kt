package com.umamit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.model.CatalogTeaSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What Sammlung › Katalog shows (SCR-19, SCR-22).
 *
 * @property teas the catalog's teas by name; empty before the first refresh.
 * @property lastRefreshAtMillis when the last successful refresh finished (CAT-07 AC6).
 * @property refresh the refresh's progress or result for the sheet.
 * @property loading true until the catalog was read once (then nothing is shown yet).
 */
data class CatalogUiState(
    val teas: List<CatalogTeaSummary> = emptyList(),
    val lastRefreshAtMillis: Long? = null,
    val refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    val loading: Boolean = true,
) {
    val isRefreshing: Boolean get() = refresh is CatalogRefreshState.Running
}

/** Sammlung › Katalog's view model: the local catalog and the refresh started from it (CAT-07 AC7: only on a tap). */
@HiltViewModel
class CatalogViewModel @Inject constructor(repository: CatalogRepository, private val refresh: CatalogRefreshRunner) :
    ViewModel() {
    val state: StateFlow<CatalogUiState> = combine(
        repository.observeCatalog(),
        refresh.state,
    ) { catalog, refreshState ->
        CatalogUiState(
            teas = catalog.teas,
            lastRefreshAtMillis = catalog.lastRefreshAtMillis,
            refresh = refreshState,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogUiState())

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
