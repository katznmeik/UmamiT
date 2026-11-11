package com.umamit.feature.journal.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionPageEvent.Deleted -> currentHost.onDeleted()
            }
        }
    }
    val actions = remember(viewModel) {
        SessionPageActions(
            back = { currentHost.onBack() },
            openTea = { currentHost.onOpenTea(it) },
            brewAgain = { currentHost.onBrewAgain(it) },
            restore = viewModel::restore,
            delete = viewModel::delete,
            setRating = viewModel::setRating,
            setTaste = viewModel::setTaste,
            clearTaste = viewModel::clearTaste,
            notesChanged = viewModel::onNotesChanged,
            flushNotes = viewModel::flushNotes,
            setDescriptor = viewModel::setDescriptor,
        )
    }
    SessionPageScreen(state = state, modifier = modifier, actions = actions, alertKey = host.alertKey)
}
