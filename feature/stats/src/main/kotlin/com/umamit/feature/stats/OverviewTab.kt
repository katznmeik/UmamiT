package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.model.StatsRange

/**
 * *Überblick* (INS-01, INS-03, INS-06), the slot #260 fills. [stats] is null until the first read and changes in
 * place with [range]; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a deleted tea.
 */
@Composable
@Suppress("UNUSED_PARAMETER") // the slot's contract: #260 draws the figures
fun OverviewTab(
    stats: OverviewStats?,
    range: StatsRange,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsTabColumn(scrollState, modifier) {
        StatsSection(title = stringResource(R.string.stats_tab_overview), isEmpty = true) {}
    }
}
