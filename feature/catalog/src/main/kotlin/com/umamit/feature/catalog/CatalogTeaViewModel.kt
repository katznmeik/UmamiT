package com.umamit.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.ShopLink
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What a catalog tea's detail shows (SCR-21).
 *
 * @property loading true until the catalog was read once.
 * @property tea the tea, or null when the catalog has none with this key (any more).
 * @property shopUrl the page *Im Shop öffnen* opens (CAT-11, D-072).
 */
data class CatalogTeaUiState(
    val loading: Boolean = true,
    val tea: CatalogTeaDetail? = null,
    val shopUrl: String? = null,
)

/**
 * The detail of the catalog tea [KEY] (SCR-21, CAT-11): the tea from the local catalog, and for
 * *Im Shop öffnen* the stock events of the collection tea linked to it by `catalog_key`, if any
 * (the size bought last, D-072). Reads only local data; the app sends no request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogTeaViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    private val teas: TeaRepository,
    private val stock: StockRepository,
) : ViewModel() {
    private val key: String = checkNotNull(savedState[KEY]) { "no catalog key" }

    val state: StateFlow<CatalogTeaUiState> = catalog.observeTea(key).flatMapLatest { tea ->
        if (tea == null) {
            flowOf(CatalogTeaUiState(loading = false))
        } else {
            ownedEvents(tea.key).map { events ->
                CatalogTeaUiState(
                    loading = false,
                    tea = tea,
                    shopUrl = ShopLink.url(tea.productUrl, tea.packages, events),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogTeaUiState())

    /** The stock events of the collection tea linked to [catalogKey], or null when none is. */
    private fun ownedEvents(catalogKey: String): Flow<List<InventoryEvent>?> = teas.observeTeas()
        .map { overviews -> overviews.firstOrNull { it.tea.catalogKey == catalogKey }?.tea?.id }
        .distinctUntilChanged()
        .flatMapLatest { teaId -> if (teaId == null) flowOf(null) else stock.observeEvents(teaId) }

    companion object {
        /** The navigation argument: the catalog key (05 §4.1). */
        const val KEY = "key"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
