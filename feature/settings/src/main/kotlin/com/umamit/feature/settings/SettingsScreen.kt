package com.umamit.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Settings (SCR-25), a calm placeholder until M1-15. It already leads to
 * *Diagnose* ([onOpenDiagnostics], SCR-27), which M1-15 moves under *Über*.
 * [extraContent] is a slot the app fills with the throwaway probe entry
 * (M1-03a), because a feature must not depend on the app.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    extraContent: @Composable () -> Unit = {},
) {
    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        navigationIcon = { BackAction(onBack) },
        scrollToTopRequest = scrollToTopRequest,
    ) {
        EmptyState(
            title = stringResource(R.string.settings_empty_title),
            body = stringResource(R.string.settings_empty_body),
        )
        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = UmamiSpacing.Large)) {
            Text(stringResource(R.string.settings_diagnostics_entry))
        }
        extraContent()
    }
}
