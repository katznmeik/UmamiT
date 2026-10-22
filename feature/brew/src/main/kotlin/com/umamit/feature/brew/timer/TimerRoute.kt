package com.umamit.feature.brew.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.timer.controller.TimerUiState

/**
 * The timer route (SCR-05). Back keeps the session running (03 §2.3); the
 * route closes itself ([onClose]) once no session is active any more, e.g.
 * after *Sitzung beenden* or *Sitzung verwerfen*.
 *
 * @param onAdjustBrew *Brühen anpassen*: back to the setup (SCR-04, M1-13).
 */
@Composable
fun BrewTimerRoute(
    onClose: () -> Unit,
    onAdjustBrew: () -> Unit,
    onOpenTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.screenSettings.collectAsStateWithLifecycle()
    val problem by viewModel.reliabilityProblem.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    when (val active = state) {
        is TimerUiState.Active -> TimerScreen(
            state = active,
            nowElapsedMs = remember(viewModel) { viewModel::nowElapsedMs },
            onEvent = viewModel::send,
            onBack = onClose,
            onAdjustBrew = onAdjustBrew,
            onOpenTimerSettings = onOpenTimerSettings,
            modifier = modifier,
            settings = settings,
            reliabilityProblem = problem,
            onFixReliability = viewModel::fixReliability,
        )

        TimerUiState.Idle -> LaunchedEffect(Unit) { currentOnClose() }
    }
}
