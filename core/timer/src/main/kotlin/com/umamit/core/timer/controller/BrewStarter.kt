package com.umamit.core.timer.controller

import com.umamit.core.model.BrewSession
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * What Start (SCR-02) and the setup (SCR-04) need from the
 * [BrewSessionController] to start a brew (TMR-01, BRW-11, TMR-21): its state,
 * for *Aufguss fortsetzen* and the second-session question, and the two
 * starts. Features depend only on `core:*` (06 §3), so the seam lives here; the
 * timer screen's own seam is `BrewTimer` in `feature:brew` (D-058).
 */
interface BrewStarter {
    /** The controller's [BrewSessionController.state]. */
    val state: StateFlow<TimerUiState>

    /**
     * *Brühen starten* / *Nochmal brühen*: the session waits in READY. Returns
     * `Rejected(ActiveTimerExists)` while another timer is active; the UI then
     * asks „Erst die … -Sitzung beenden?" (TMR-21).
     */
    suspend fun startSession(request: BrewStart): TransitionOutcome

    /**
     * *Beenden & neu starten* (TMR-21): the active session is finished as by
     * *Sitzung beenden* (saved, or discarded without a completed non-rinse
     * step), an active quick timer is cancelled, then [request] starts.
     */
    suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome

    /**
     * *Start* on the quick timer (SCR-07, TMR-22, 04 §10): one infusion of
     * [durationSeconds] with the global pre-alert, running at once (SCR-07's
     * *Start* is the start of its one step). Nothing is recorded. Returns
     * `Rejected(ActiveTimerExists)` while another timer is active (TMR-21) and
     * `Rejected(InvalidQuickTimer)` outside 5 s – 24 h.
     */
    suspend fun startQuickTimer(durationSeconds: Int): TransitionOutcome

    /** *Beenden & neu starten* (TMR-21) for the quick timer: the active timer ends as above, then it runs. */
    suspend fun finishActiveAndStartQuickTimer(durationSeconds: Int): TransitionOutcome
}

/**
 * The [BrewStarter] of the app: the controller itself. *Beenden & neu
 * starten* is the controller's one-turn operation, which posts the finish's
 * saved sheet as `NotifySessionSaved` (D-054, D-063).
 */
class ControllerBrewStarter @Inject constructor(private val controller: BrewSessionController) : BrewStarter {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override suspend fun startSession(request: BrewStart): TransitionOutcome = controller.startSession(request)

    override suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome =
        controller.finishActiveAndStart(request)

    override suspend fun startQuickTimer(durationSeconds: Int): TransitionOutcome =
        controller.startQuickTimer(durationSeconds, run = true)

    override suspend fun finishActiveAndStartQuickTimer(durationSeconds: Int): TransitionOutcome =
        controller.startQuickTimer(durationSeconds, run = true, finishActive = true)
}

/**
 * *Nochmal brühen* (BRW-11): a start with the parameters of [last], the tea's
 * last session: its snapshot of tea, recipe, vessel, leaf and water, its steps
 * as planned (in order, each with its own pre-alert; D-093) and its pre-alert
 * base (B from umamit.db v2, the effective value before), frozen like a
 * recipe's. A session finished early has kept only its started steps (TMR-12),
 * so those are the ones brewed again (D-062).
 */
fun brewAgainStart(last: BrewSession): BrewStart = BrewStart(
    snapshot = last.snapshot,
    steps = last.steps.sortedBy { it.position }.map {
        PlannedStep(it.kind, it.plannedSeconds, it.tempC, it.preAlertSeconds)
    },
    recipePreAlertSeconds = last.preAlertSeconds,
)
