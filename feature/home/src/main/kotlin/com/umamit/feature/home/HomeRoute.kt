package com.umamit.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Start (SCR-02) with its view model. *Nochmal brühen* opens the timer once
 * the session waits in READY ([onOpenTimer]); the other taps go to the host
 * through [actions], whose *Nochmal brühen* and question callbacks the route
 * replaces with the view model's.
 */
@Composable
fun HomeRoute(
    actions: HomeActions,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenTimer by rememberUpdatedState(onOpenTimer)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenTimer -> currentOnOpenTimer()
            }
        }
    }
    val routeActions = remember(actions, viewModel) {
        HomeActions(
            openSettings = actions.openSettings,
            startBrew = actions.startBrew,
            openTimer = actions.openTimer,
            quickTimer = actions.quickTimer,
            brewAgain = viewModel::brewAgain,
            openSetup = actions.openSetup,
            showJournal = actions.showJournal,
            openCatalog = actions.openCatalog,
            confirmRestart = viewModel::confirmRestart,
            dismissQuestion = viewModel::dismissQuestion,
        )
    }
    HomeScreen(
        state = state,
        nowElapsedMs = remember(viewModel) { viewModel::nowElapsedMs },
        modifier = modifier,
        actions = routeActions,
        scrollToTopRequest = scrollToTopRequest,
    )
}
