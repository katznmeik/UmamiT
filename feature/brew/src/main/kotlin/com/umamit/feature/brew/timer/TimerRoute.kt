package com.umamit.feature.brew.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberPhotoPainter

/**
 * The timer route (SCR-05). Back keeps the session running (03 §2.3); the
 * route closes itself ([onClose]) once no session is active any more, e.g.
 * after *Sitzung beenden* or *Sitzung verwerfen*; not before the controller has
 * recovered the timer of a new process (D-068).
 *
 * @param onAdjustBrew *Brühen anpassen*: the setup (SCR-04) of the waiting session, by its id (BRW-11).
 */
@Composable
fun BrewTimerRoute(
    onClose: () -> Unit,
    onAdjustBrew: (sessionId: String) -> Unit,
    onOpenTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recovered by viewModel.recovered.collectAsStateWithLifecycle()
    val settings by viewModel.screenSettings.collectAsStateWithLifecycle()
    val problem by viewModel.reliabilityProblem.collectAsStateWithLifecycle()
    val seal by viewModel.teaSeal.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    when (val active = state) {
        is TimerUiState.Active -> TimerScreen(
            state = active,
            nowElapsedMs = remember(viewModel) { viewModel::nowElapsedMs },
            // Every tap acts on the timer the screen shows (04 §3.3, D-098).
            onEvent = { event -> viewModel.send(active.slot, event) },
            onBack = onClose,
            onAdjustBrew = { active.sessionId?.let(onAdjustBrew) },
            onOpenTimerSettings = onOpenTimerSettings,
            modifier = modifier,
            settings = settings,
            reliabilityProblem = problem,
            onFixReliability = { context.startFirstIntent(viewModel.fixReliability()) },
            // The seal shows the tea's cover photo, the type-coloured leaf until it is read or without one;
            // its space is fixed, so nothing moves when the photo arrives (D-058, D-095).
            teaPhoto = seal?.photoPath?.let { rememberPhotoPainter(it, rememberSealLeafPainter(seal?.styleKey)) },
            teaStyleKey = seal?.styleKey,
        )

        // Restored after process death (from recents, or a notification of a session the new
        // process has not read yet), the route sees Idle before recovery: it waits for it and
        // closes only on a known Idle (GEN-07, D-068). The current value decides, not the
        // collected one, which may lag the recovered state by a frame.
        TimerUiState.Idle -> if (recovered) {
            LaunchedEffect(Unit) { if (viewModel.state.value == TimerUiState.Idle) currentOnClose() }
        }
    }
}
