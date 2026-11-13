package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.feature.stats.StatsContent
import com.umamit.feature.stats.StatsRoute
import com.umamit.feature.stats.StatsUiState

/**
 * What the shell hands the Statistik tab (SCR-18).
 *
 * @property openTea opens a collection tea's detail (SCR-09) in the tab's stack, from the resumed screen only.
 */
internal class StatsTabHost(
    val openSettings: () -> Unit,
    val scrollToTopRequest: Int,
    val openTea: (teaId: String) -> Unit,
    val modifier: Modifier = Modifier,
)

/** Statistik with its view model (#259). */
@Composable
internal fun StatsTabContent(tab: StatsTabHost) {
    StatsRoute(
        onOpenSettings = tab.openSettings,
        onOpenTea = tab.openTea,
        modifier = tab.modifier,
        scrollToTopRequest = tab.scrollToTopRequest,
    )
}

/** Statistik without data, for tests and previews of the shell: the empty state (A9). */
@Composable
internal fun PlaceholderStats(tab: StatsTabHost) {
    StatsContent(
        state = StatsUiState.Empty,
        onRangeChange = { _: StatsRange -> },
        onTabChange = { _: StatsTab -> },
        onOpenSettings = tab.openSettings,
        modifier = tab.modifier,
        scrollToTopRequest = tab.scrollToTopRequest,
    )
}
