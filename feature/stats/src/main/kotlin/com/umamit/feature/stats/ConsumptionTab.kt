package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.model.StatsRange

/**
 * *Verbrauch* (INS-05), the slot #262 fills. [stats] is null until the first read and changes in place with
 * [range]; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a deleted tea.
 */
@Composable
@Suppress("UNUSED_PARAMETER") // the slot's contract: #262 draws the figures
fun ConsumptionTab(
    stats: ConsumptionStats?,
    range: StatsRange,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsTabColumn(scrollState, modifier) {
        StatsSection(title = stringResource(R.string.stats_tab_consumption), isEmpty = true) {}
    }
}
