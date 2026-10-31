package com.umamit.feature.brew.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberPhotoPainter

/**
 * The timer route (SCR-05; SCR-07 once the quick timer runs). Back keeps the timer running (03 §2.3); the
 * route closes itself ([onClose]) once its timer is no longer active, e.g. after *Sitzung beenden*, *Sitzung
 * verwerfen* or *Fertig*; not before the controller has recovered the timer of a new process (D-068).
 *
 * **Two timers (D-098).** A route shows one timer: [slot]'s, or, opened for none, the one in front of the
 * controller when it opened (a notification, Start's card). It keeps that timer when the controller's front
 * changes (the row's *Pause* puts the other timer in front there). A ringing alert of either timer takes the
 * route at once, the brew's first (rule 8); a held quick alert never does. The other active timer is the row
 * below the band: a tap on it asks for the other timer's route ([onShowOther]), its button acts on that timer
 * only. When the route's timer ends, it closes, unless the other timer's alert rings: it shows that until *Fertig*.
 *
 * @param onAdjustBrew *Brühen anpassen*: the setup (SCR-04) of the waiting session, by its id (BRW-11).
 * @param slot the timer this route shows; null: the one in front when it opened.
 * @param onShowOther a tap on the other timer's row: that timer's route.
 */
@Composable
fun BrewTimerRoute(
    onClose: () -> Unit,
    onAdjustBrew: (sessionId: String) -> Unit,
    onOpenTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    slot: TimerSlot? = null,
    onShowOther: (TimerSlot) -> Unit = {},
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val front by viewModel.state.collectAsStateWithLifecycle()
    val brew by remember(viewModel) { viewModel.state(TimerSlot.BREW) }.collectAsStateWithLifecycle()
    val quick by remember(viewModel) { viewModel.state(TimerSlot.QUICK) }.collectAsStateWithLifecycle()
    val recovered by viewModel.recovered.collectAsStateWithLifecycle()
    val settings by viewModel.screenSettings.collectAsStateWithLifecycle()
    val problem by viewModel.reliabilityProblem.collectAsStateWithLifecycle()
    val seal by viewModel.teaSeal.collectAsStateWithLifecycle()
    val stock by viewModel.teaStock.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    // The timer this route shows, once known: kept across a configuration change and process death.
    var pinned by rememberSaveable { mutableStateOf(slot) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    val view = timerRouteView(pinned, brew, quick, front)
    when (val active = view.shown) {
        null -> if (recovered) {
            // Restored after process death (from recents, or a notification of a session the new process has not
            // read yet), the route sees Idle before recovery: it waits for it and closes only on a known end
            // (GEN-07, D-068). The current values decide, not the collected ones, which may lag by a frame.
            LaunchedEffect(Unit) {
                val now = timerRouteView(
                    pinned,
                    viewModel.state(TimerSlot.BREW).value,
                    viewModel.state(TimerSlot.QUICK).value,
                    viewModel.state.value,
                )
                if (now.shown == null) currentOnClose()
            }
        }

        else -> {
            if (pinned == null) SideEffect { pinned = active.slot }
            val other = view.other
            TimerScreen(
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
                // Read beside the timer, never on its path: it comes when it comes, in space kept for it (D-098).
                stock = stock,
                other = other,
                // The row's button and tap name the other timer's slot, never this one's (D-098).
                onOtherEvent = { event -> other?.let { viewModel.send(it.slot, event) } },
                onOpenOther = { other?.let { onShowOther(it.slot) } },
            )
        }
    }
}

/** What one timer route shows (D-098): its timer, and the other active timer for the row; null: none. */
internal class TimerRouteView(val shown: TimerUiState.Active?, val other: TimerUiState.Active?)

/**
 * The route's view of the two slots ([brew], [quick]) and the controller's [front] (D-098): a ringing alert first,
 * the brew's before the quick timer's (rule 8; a held alert does not ring); else the [pinned] slot's timer, or, with
 * none pinned yet, the one in front; null when that timer is not active. The other slot's active timer is the row.
 */
internal fun timerRouteView(
    pinned: TimerSlot?,
    brew: TimerUiState,
    quick: TimerUiState,
    front: TimerUiState,
): TimerRouteView {
    val brewTimer = brew as? TimerUiState.Active
    val quickTimer = quick as? TimerUiState.Active
    val shown = brewTimer?.takeIf { it.ringing }
        ?: quickTimer?.takeIf { it.ringing }
        ?: when (pinned) {
            null -> front as? TimerUiState.Active
            TimerSlot.BREW -> brewTimer
            TimerSlot.QUICK -> quickTimer
        }
    val other = when (shown?.slot) {
        null -> null
        TimerSlot.BREW -> quickTimer
        TimerSlot.QUICK -> brewTimer
    }
    return TimerRouteView(shown, other)
}
