package com.umamit.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SettingsGearAction

/** Start (SCR-02), a calm placeholder until M1-12. The settings sit behind the gear. */
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier, scrollToTopRequest: Int = 0) {
    ScreenScaffold(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        actions = { SettingsGearAction(onOpenSettings) },
        scrollToTopRequest = scrollToTopRequest,
    ) {
        EmptyState(
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
        )
    }
}
