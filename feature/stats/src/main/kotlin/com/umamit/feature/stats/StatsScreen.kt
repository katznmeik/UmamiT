package com.umamit.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold

/** Statistik (SCR-18), a calm placeholder until M4 that stays true after the first brew (#54 S2). */
@Composable
fun StatsScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier, scrollToTopRequest: Int = 0) {
    ScreenScaffold(
        title = stringResource(R.string.stats_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings) },
        scrollToTopRequest = scrollToTopRequest,
    ) {
        EmptyState(
            title = stringResource(R.string.stats_later_title),
            body = stringResource(R.string.stats_later_body),
        )
    }
}
