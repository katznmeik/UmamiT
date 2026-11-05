package com.umamit.feature.journal.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The session page route; its argument is the view model's `sessionId` ([SessionPageViewModel.SESSION_ID]). */
@Composable
fun SessionPageRoute(
    host: SessionPageHost,
    modifier: Modifier = Modifier,
    viewModel: SessionPageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentHost by rememberUpdatedState(host)
    val actions = remember(viewModel) {
        SessionPageActions(
            back = { currentHost.onBack() },
            openTea = { currentHost.onOpenTea(it) },
            brewAgain = { currentHost.onBrewAgain(it) },
            restore = viewModel::restore,
        )
    }
    SessionPageScreen(state = state, modifier = modifier, actions = actions)
}
