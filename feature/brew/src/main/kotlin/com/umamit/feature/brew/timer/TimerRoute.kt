package com.umamit.feature.brew.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberPhotoPainter

/**
 * The timer route (SCR-05). Back keeps the session running (03 §2.3); the
 * route closes itself ([onClose]) once no session is active any more, e.g.
 * after *Sitzung beenden* or *Sitzung verwerfen*.
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
    val settings by viewModel.screenSettings.collectAsStateWithLifecycle()
    val problem by viewModel.reliabilityProblem.collectAsStateWithLifecycle()
    val photoPath by viewModel.teaPhotoPath.collectAsStateWithLifecycle()
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
            onAdjustBrew = { active.sessionId?.let(onAdjustBrew) },
            onOpenTimerSettings = onOpenTimerSettings,
            modifier = modifier,
            settings = settings,
            reliabilityProblem = problem,
            onFixReliability = { context.startFirstIntent(viewModel.fixReliability()) },
            // A brew always has its photo's space: the tea's cover photo, the placeholder until it is read
            // or without one, so nothing moves when it arrives (D-058). The quick timer has none.
            teaPhoto = if (active.sessionId != null) {
                rememberPhotoPainter(photoPath, painterResource(UmamiIcons.LandscapePlaceholder))
            } else {
                null
            },
        )

        TimerUiState.Idle -> LaunchedEffect(Unit) { currentOnClose() }
    }
}
