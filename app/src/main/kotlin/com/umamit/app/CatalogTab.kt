package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.catalog.CatalogContent
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogUiState

/**
 * What Sammlung's *Katalog* segment gets from the shell (SCR-19).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property alerting whether an alert plays now: the refresh sheet gives way (rule 8).
 * @property refreshRequest the ⋮ menu's *Katalog aktualisieren* counter (CAT-07, D-049).
 */
internal data class CatalogTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val alerting: Boolean,
    val refreshRequest: Int = 0,
)

/** The catalog segment with its view model and the refresh (#64, #65). */
@Composable
internal fun CatalogTabContent(tab: CatalogTab) {
    CatalogRoute(
        alerting = tab.alerting,
        modifier = tab.modifier,
        scrollToTopRequest = tab.scrollToTopRequest,
        refreshRequest = tab.refreshRequest,
    )
}

/** A catalog without data, for tests and previews of the shell: the empty state. */
@Composable
internal fun PlaceholderCatalog(tab: CatalogTab) {
    CatalogContent(state = CatalogUiState(loading = false), onLoad = {}, onShowProgress = {}, modifier = tab.modifier)
}
