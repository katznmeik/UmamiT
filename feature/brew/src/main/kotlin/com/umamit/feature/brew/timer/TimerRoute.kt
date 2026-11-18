package com.umamit.feature.brew.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.WashiColorScheme
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.lastRinging
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberPhotoPainter

/**
 * The timer route (SCR-05; SCR-07 once the quick timer runs). Back keeps the timer running (03 §2.3); the
 * route closes itself ([onClose]) once its timer is no longer active, e.g. after *Session beenden*, *Session
 * verwerfen* or *Fertig*; not before the controller has recovered the timer of a new process (D-068).
 *
 * **Two timers (D-098).** A route shows one timer: [slot]'s, or, opened for none, the one in front of the
 * controller when it opened (a notification, Start's card). It keeps that timer when the controller's front
 * changes (the row's *Pause* puts the other timer in front there). A ringing alert of either timer takes the
 * route at once, the brew's first (rule 8); a held quick alert never does. The other active timer is the row
 * below the band: a tap on it asks for the other timer's route ([onShowOther]), its button acts on that timer
 * only. When the route's timer ends, it closes, unless the other timer's alert rings: it shows that until *Fertig*.
 *
 * @param onAdjustBrew *Session anpassen*: the setup (SCR-04) of the waiting session, by its id (BRW-11).
 * @param slot the timer this route shows; null: the one in front when it opened.
 * @param onPin opened for none, the timer it then keeps showing, once known (the navigation reads it when a
 *   notification asks for a timer, 04 §8.2).
 * @param onShowOther a tap on the other timer's row: that timer's route.
 */
@Composable
fun BrewTimerRoute(
    onClose: () -> Unit,
    onAdjustBrew: (sessionId: String) -> Unit,
    onOpenTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    slot: TimerSlot? = null,
    onPin: (TimerSlot) -> Unit = {},
    onShowOther: (TimerSlot) -> Unit = {},
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val front by viewModel.state.collectAsStateWithLifecycle()
    val brew by remember(viewModel) { viewModel.state(TimerSlot.BREW) }.collectAsStateWithLifecycle()
    val brew2 by remember(viewModel) { viewModel.state(TimerSlot.BREW_2) }.collectAsStateWithLifecycle()
    val quick by remember(viewModel) { viewModel.state(TimerSlot.QUICK) }.collectAsStateWithLifecycle()
    val recovered by viewModel.recovered.collectAsStateWithLifecycle()
    val settings by viewModel.screenSettings.collectAsStateWithLifecycle()
    val problem by viewModel.reliabilityProblem.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    // The timer this route shows, once known: kept across a configuration change and process death.
    var pinned by rememberSaveable { mutableStateOf(slot) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    val view = timerRouteView(pinned, brew, quick, front, brew2)
    // The last timer this route showed, never an alert: drawn, inert, while the route closes, so the pop's `exitFade`
    // fades the timer out instead of an empty frame (A-15). Not saved: a restored route has none.
    var last by remember { mutableStateOf<TimerUiState.Active?>(null) }
    val closing = view.shown == null
    // Under everything, the timer's background in the app's theme (D-128 Q20): before recovery and while closing the
    // route is never the window background of the other theme under the timer (A-05, A-15). It draws no content and
    // delays nothing (rule 8).
    val routeBackground = if (UmamiTheme.isDark) SteepingColorScheme.background else WashiColorScheme.background
    Box(modifier.fillMaxSize().background(routeBackground)) {
        if (closing && recovered) {
            // Restored after process death (from recents, or a notification of a session the new process has not
            // read yet), the route sees Idle before recovery: it waits for it and closes only on a known end
            // (GEN-07, D-068). The current values decide, not the collected ones, which may lag by a frame.
            LaunchedEffect(Unit) {
                val now = timerRouteView(
                    pinned,
                    viewModel.state(TimerSlot.BREW).value,
                    viewModel.state(TimerSlot.QUICK).value,
                    viewModel.state.value,
                    viewModel.state(TimerSlot.BREW_2).value,
                )
                if (now.shown == null) currentOnClose()
            }
        }
        // One call site, so the closing frame recomposes the same screen instead of composing a new one.
        val active = view.shown ?: last?.takeUnless { it.alerting }
        if (active != null) {
            if (!closing) {
                SideEffect { last = active }
                if (pinned == null) {
                    SideEffect {
                        pinned = active.slot
                        onPin(active.slot)
                    }
                }
            }
            val other = view.other
            // The shown brew's own tea: each of two brews has its seal and stock (D-108).
            val seal by remember(viewModel, active.slot) { viewModel.teaSeal(active.slot) }
                .collectAsStateWithLifecycle()
            val stock by remember(viewModel, active.slot) { viewModel.teaStock(active.slot) }
                .collectAsStateWithLifecycle()
            val tastings by remember(viewModel, active.slot) { viewModel.stepTastings(active.slot) }
                .collectAsStateWithLifecycle()
            val tasteSet by remember(viewModel, active.slot) { viewModel.tasteSet(active.slot) }
                .collectAsStateWithLifecycle()
            val refusals by viewModel.tasteWrites.refusals.collectAsStateWithLifecycle()
            TimerScreen(
                state = active,
                nowElapsedMs = remember(viewModel) { viewModel::nowElapsedMs },
                // Every tap acts on the timer the screen shows (04 §3.3, D-098).
                onEvent = inertWhile(closing) { event: TimerEvent -> viewModel.send(active.slot, event) },
                onBack = inertWhile(closing, onClose),
                onAdjustBrew = inertWhile(closing) { active.sessionId?.let(onAdjustBrew) },
                onOpenTimerSettings = inertWhile(closing, onOpenTimerSettings),
                // Closing, the screen is inert: no tap reaches it, nothing opens over it.
                modifier = if (closing) Modifier.consumeAllPointers() else Modifier,
                closing = closing,
                settings = settings,
                reliabilityProblem = problem,
                onFixReliability = inertWhile(closing) { context.startFirstIntent(viewModel.fixReliability()) },
                // The seal shows the tea's cover photo, the type-coloured leaf until it is read or without one;
                // its space is fixed, so nothing moves when the photo arrives (D-058, D-095).
                teaPhoto = seal?.photoPath?.let { rememberPhotoPainter(it, rememberSealLeafPainter(seal?.styleKey)) },
                teaStyleKey = seal?.styleKey,
                // Read beside the timer, never on its path: it comes when it comes, in space kept for it (D-098).
                stock = stock,
                other = other,
                // The row's button and tap name the other timer's slot, never this one's (D-098). Closing, there is
                // no row: the other timer is only ever beside a shown one.
                onOtherEvent = { event -> other?.let { viewModel.send(it.slot, event) } },
                onOpenOther = { other?.let { onShowOther(it.slot) } },
                // The shown session's taste and taste set (D-114, D-128): read and written beside the timer, never as
                // an engine event; a read of another session (the slot's previous one) is not used.
                tasting = active.sessionId?.let { sessionId ->
                    TimerTasting(
                        byPosition = tastings?.takeIf { it.sessionId == sessionId }?.byPosition,
                        onTaste = { position, axis, value ->
                            viewModel.tasteWrites.setTaste(sessionId, position, axis, value)
                        },
                        onReset = { position, axes -> viewModel.tasteWrites.clearTaste(sessionId, position, axes) },
                        onNote = { position, note -> viewModel.tasteWrites.setNote(sessionId, position, note) },
                        refusals = refusals,
                        set = tasteSet?.takeIf { it.sessionId == sessionId }?.set,
                    )
                },
            )
        }
    }
}

/** [action], or nothing while the route is [closing]: the closing timer is inert (A-15). */
private fun inertWhile(closing: Boolean, action: () -> Unit): () -> Unit = if (closing) NoAction else action

/** [action], or nothing while the route is [closing] (A-15). */
private fun <T> inertWhile(closing: Boolean, action: (T) -> Unit): (T) -> Unit = if (closing) ({ _ -> }) else action

private val NoAction: () -> Unit = {}

/** Swallows every touch before the content sees it: the closing timer is inert (A-15). */
private fun Modifier.consumeAllPointers(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** What one timer route shows (D-098): its timer, and the other active timer for the row; null: none. */
internal class TimerRouteView(val shown: TimerUiState.Active?, val other: TimerUiState.Active?)

/**
 * The route's view of the slots ([brew], [brew2], [quick]) and the controller's [front] (D-098, D-108): a ringing
 * alert first, of two the one that rang last, whose sound plays (rule 8, D-109; a held alert does not ring, one whose
 * sound ended stays the row); else the [pinned] slot's timer, or, with none pinned yet, the one in front; null when
 * that timer is not active. The other active timer is the row: the quick timer, or the other brew session with its
 * own seal and stock on its own route (v0.5.0-03).
 */
internal fun timerRouteView(
    pinned: TimerSlot?,
    brew: TimerUiState,
    quick: TimerUiState,
    front: TimerUiState,
    brew2: TimerUiState = TimerUiState.Idle,
): TimerRouteView {
    fun activeIn(slot: TimerSlot?): TimerUiState.Active? = when (slot) {
        null -> null
        TimerSlot.BREW -> brew as? TimerUiState.Active
        TimerSlot.BREW_2 -> brew2 as? TimerUiState.Active
        TimerSlot.QUICK -> quick as? TimerUiState.Active
    }
    val shown = TimerSlot.entries.mapNotNull(::activeIn).lastRinging()
        ?: if (pinned == null) front as? TimerUiState.Active else activeIn(pinned)
    val other = TimerSlot.entries.filter { it != shown?.slot }.firstNotNullOfOrNull { activeIn(it) }
        ?.takeIf { shown != null }
    return TimerRouteView(shown, other)
}
