package com.umamit.feature.brew.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The quick timer route (SCR-07), from Start's *Schnelltimer* and the tea
 * picker's *Ohne Tee (Schnelltimer)*. [onOpenTimer] runs once the quick timer
 * runs (the timer, SCR-05, then shows it), or when another timer alerts.
 */
@Composable
fun QuickTimerRoute(
    onBack: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickTimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenTimer by rememberUpdatedState(onOpenTimer)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                QuickTimerEvent.OpenTimer -> currentOnOpenTimer()
            }
        }
    }
    val actions = remember(viewModel, onBack) {
        QuickTimerActions(
            back = onBack,
            select = viewModel::select,
            start = viewModel::start,
            confirmRestart = viewModel::confirmRestart,
            dismissQuestion = viewModel::dismissQuestion,
        )
    }
    QuickTimerScreen(state = state, actions = actions, modifier = modifier)
}
