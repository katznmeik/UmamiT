package com.umamit.feature.journal.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The session editor route; its argument is the view model's `sessionId` ([SessionEditorViewModel.SESSION_ID]). */
@Composable
fun SessionEditorRoute(
    host: SessionEditorHost,
    modifier: Modifier = Modifier,
    viewModel: SessionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionEditorEvent.Saved -> currentHost.onSaved()
            }
        }
    }
    val actions = remember(viewModel) {
        SessionEditorActions(
            close = { currentHost.onClose() },
            save = viewModel::save,
            update = viewModel::update,
            setDate = viewModel::setDate,
            setTime = viewModel::setTime,
        )
    }
    SessionEditorScreen(state = state, modifier = modifier, actions = actions, alertKey = host.alertKey)
}
