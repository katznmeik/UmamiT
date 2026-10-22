package com.umamit.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.ScreenScaffold

/**
 * Settings (SCR-25), a calm placeholder until M1-22. [extraContent] is a slot
 * the app fills with the throwaway probe entry (M1-03a), because a feature
 * must not depend on the app.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable () -> Unit = {},
) {
    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        navigationIcon = { BackAction(onBack) },
    ) {
        EmptyState(
            title = stringResource(R.string.settings_empty_title),
            body = stringResource(R.string.settings_empty_body),
        )
        extraContent()
    }
}
