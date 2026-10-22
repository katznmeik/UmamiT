package com.umamit.feature.brew.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.umamit.core.designsystem.component.AlertSurface
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.delay

/** Test tag of the TalkBack announcement node (a polite, while alerting assertive, live region). */
const val TIMER_ANNOUNCEMENT_TAG = "timer_announcement"

/** Test tag of the ring; tappable in READY (TMR-08). */
const val TIMER_RING_TAG = "timer_ring"

/**
 * The timer (SCR-05; TMR-02…13, TMR-17, TMR-31): always in the dark
 * „Steeping" scheme, edge-to-edge, in one of five states. It draws [state]
 * and turns every tap into a controller event ([onEvent]); it calls nothing
 * else.
 *
 * - **READY:** kettle hint and the next step; *Start* (the ring too).
 * - **RUNNING / PAUSED:** the cream *Pause* / *Weiter* pill; „Zieht …",
 *   „Gleich fertig …" from the pre-alert on, with the glow.
 * - **ALERTING:** the pulsing [AlertSurface], entered without any transition
 *   (`alertEnter`), „Jetzt abgießen!", the overtime, from 15 s on large and
 *   marked with a warm-accent dot; *Abgegossen* (*Weggegossen* for a rinse).
 * - **FINISHED:** *Sitzung beenden* and *Weiterer Aufguss*.
 *
 * The overflow ⋮ follows the SCR-05 table ([menuItems]). *Sitzung verwerfen*
 * and a finish that would discard the session ask first (TMR-12 AC1, TMR-13).
 * The Schritte-Sheet is hosted with `alerting` bound to the phase: on ALERTING
 * it is removed in the same frame (D-051, rule 8), and so are a menu or a
 * question opened before the alert.
 *
 * The screen stays on ([TimerScreenSettings.keepScreenOn], SET-17) and dims a
 * running step after 10 s untouched ([ScreenDimming], TMR-17). TalkBack
 * announces state changes, the pre-alert, „noch 10 Sekunden" and the alert.
 *
 * @param nowElapsedMs the monotonic clock (`elapsedRealtime`), as the
 *   controller's deadlines use it.
 * @param teaPhoto the tea's photo, fading into the background; none for the quick timer.
 */
@Composable
fun TimerScreen(
    state: TimerUiState.Active,
    nowElapsedMs: () -> Long,
    onEvent: (TimerEvent) -> Unit,
    onBack: () -> Unit,
    onAdjustBrew: () -> Unit,
    onOpenTimerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settings: TimerScreenSettings = TimerScreenSettings(),
    reliabilityProblem: Boolean = false,
    onFixReliability: () -> Unit = {},
    teaPhoto: Painter? = null,
) {
    SteepingTheme(reduceMotion = UmamiTheme.reduceMotion) {
        val alerting = state.alerting
        val alertKey = state.alertKey()
        val now by rememberTimerNow(state, nowElapsedMs)
        val overlays = rememberTimerOverlays()
        // Alerts first (rule 8): whatever was opened before this alert is gone in the same frame.
        overlays.currentAlert = alertKey
        LaunchedEffect(alertKey) { alertKey?.let(overlays::closeAllNotOpenedIn) }

        val dimming = rememberScreenDimming(
            state = state,
            enabled = settings.dimWhileSteeping,
            interacting = overlays.anyOpen,
            nowElapsedMs = nowElapsedMs,
        )
        // Decided in this composition, so the alert and the pre-alert are bright in their first frame (rule 8).
        TimerWindowEffects(
            keepScreenOn = settings.keepScreenOn,
            dimmed = dimming.dimmed && state.phase == TimerPhase.RUNNING && !state.preAlertReached(now),
        )

        val actions = rememberTimerActions(state, overlays, onEvent, onBack, onAdjustBrew, onOpenTimerSettings)

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(dimming) {
                    awaitPointerEventScope {
                        while (true) {
                            // A touch is a press; moves and releases do not restart the 10 s again.
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                                dimming.touched()
                            }
                        }
                    }
                },
        ) {
            val args =
                TimerBodyArgs(
                    state,
                    now,
                    nowElapsedMs,
                    overlays,
                    actions,
                    reliabilityProblem,
                    onFixReliability,
                    teaPhoto,
                )
            // One body for both surfaces: it moves between them with its state (scroll, menu, focus).
            val body = remember { movableContentOf { bodyArgs: TimerBodyArgs -> TimerBody(bodyArgs) } }
            if (alerting) {
                // `alertEnter`: no transition into the alert, the pulse starts at full matcha.
                AlertSurface(modifier = Modifier.fillMaxSize()) { body(args) }
            } else {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { body(args) }
            }
            TimerAnnouncer(state, now)
        }

        TimerStepsSheet(state, overlays, onEvent)
        overlays.confirmation?.takeIf { overlays.confirmationShown }?.let { confirmation ->
            DiscardSessionDialog(
                onConfirm = {
                    overlays.confirmation = null
                    onEvent(
                        if (confirmation ==
                            TimerConfirmation.Discard
                        ) {
                            TimerEvent.Discard
                        } else {
                            TimerEvent.FinishSession
                        },
                    )
                },
                onDismiss = { overlays.confirmation = null },
            )
        }
    }
}

/**
 * The clock as the screen shows it: read again at every second boundary of the
 * display (and the pre-alert). A new state reads the clock in its first
 * composition, so the first frame after *Start* or *Weiter* is never stale.
 */
@Composable
private fun rememberTimerNow(state: TimerUiState.Active, nowElapsedMs: () -> Long): State<Long> {
    val now = remember(state, nowElapsedMs) { mutableLongStateOf(nowElapsedMs()) }
    LaunchedEffect(now, nowElapsedMs) {
        while (true) {
            val delayMs = state.nextTickDelayMs(now.longValue) ?: break
            delay(delayMs)
            now.longValue = nowElapsedMs()
        }
    }
    return now
}

/** What a tap does: haptics (03 §3.6) and the controller event, or the menu item's screen or overlay. */
@Composable
private fun rememberTimerActions(
    state: TimerUiState.Active,
    overlays: TimerOverlays,
    onEvent: (TimerEvent) -> Unit,
    onBack: () -> Unit,
    onAdjustBrew: () -> Unit,
    onOpenTimerSettings: () -> Unit,
): TimerActions {
    val haptics = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)
    return remember(overlays, onEvent, onBack, onAdjustBrew, onOpenTimerSettings) {
        TimerActions(
            event = { event ->
                haptics.performHapticFeedback(
                    if (event == TimerEvent.Poured) HapticFeedbackType.Confirm else HapticFeedbackType.ContextClick,
                )
                onEvent(event)
            },
            back = onBack,
            menu = { item ->
                overlays.menuOpen = false
                when (item) {
                    TimerMenuItem.AdjustBrew -> onAdjustBrew()

                    TimerMenuItem.EndStepNow -> onEvent(TimerEvent.EndStepNow)

                    TimerMenuItem.SkipStep -> onEvent(TimerEvent.SkipStep)

                    TimerMenuItem.EditSteps -> overlays.stepsOpen = true

                    TimerMenuItem.AddStep -> {
                        onEvent(TimerEvent.AddStep)
                        overlays.stepsOpen = true
                    }

                    TimerMenuItem.FinishSession -> overlays.finish(currentState, onEvent)

                    TimerMenuItem.DiscardSession -> overlays.ask(TimerConfirmation.Discard)

                    TimerMenuItem.TimerSettings -> onOpenTimerSettings()
                }
            },
        )
    }
}
