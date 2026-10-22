package com.umamit.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold

/** Journal (SCR-15), a calm placeholder until M4. */
@Composable
fun JournalScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier, scrollToTopRequest: Int = 0) {
    ScreenScaffold(
        title = stringResource(R.string.journal_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings) },
        scrollToTopRequest = scrollToTopRequest,
    ) {
        EmptyState(
            title = stringResource(R.string.journal_empty_title),
            body = stringResource(R.string.journal_empty_body),
        )
    }
}
