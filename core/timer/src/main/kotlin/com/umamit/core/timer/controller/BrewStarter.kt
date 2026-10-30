package com.umamit.core.timer.controller

import com.umamit.core.model.BrewSession
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * What Start (SCR-02) and the setup (SCR-04) need from the
 * [BrewSessionController] to start a brew (TMR-01, BRW-11, TMR-21, TMR-22): its state,
 * for *Aufguss fortsetzen* and the second-timer questions, and the
 * starts. A brew session and a quick timer start while the other runs (D-098); only a
 * second one of the same kind asks. Features depend only on `core:*` (06 §3), so the seam lives here; the
 * timer screen's own seam is `BrewTimer` in `feature:brew` (D-058).
 */
interface BrewStarter {
    /** The controller's [BrewSessionController.state]: the timer in front. */
    val state: StateFlow<TimerUiState>

    /** The active timer of [slot] now (the brew session, the quick timer); null while that slot is idle. */
    fun activeIn(slot: TimerSlot): TimerUiState.Active?

    /**
     * *Brühen starten* / *Nochmal brühen*: the session waits in READY, also while a quick timer runs. Returns
     * `Rejected(ActiveTimerExists)` while another brew session is active; the UI then
     * asks „Erst die … -Sitzung beenden?" (TMR-21).
     */
    suspend fun startSession(request: BrewStart): TransitionOutcome

    /**
     * *Beenden & neu starten* (TMR-21): the active session is finished as by
     * *Sitzung beenden* (saved, or discarded without a completed non-rinse
     * step), then [request] starts; a quick timer is never touched (D-098).
     */
    suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome

    /**
     * *Start* on the quick timer (SCR-07, TMR-22, 04 §10): one infusion of
     * [durationSeconds] with the *Vorwarnung* chosen on SCR-07, [preAlertSeconds]
     * (D-097: null *Automatisch* with B = the global pre-alert, 0 *Aus*, else
     * 5–30 s as set), running at once (SCR-07's *Start* is the start of its one
     * step). Nothing is recorded. It starts while a brew session runs (D-098). Returns
     * `Rejected(ActiveTimerExists)` while another quick timer is active (the UI asks „Schnelltimer beenden und
     * neu starten?") and `Rejected(InvalidQuickTimer)` outside
     * 5 s – 24 h or for a choice other than 0 or 5–30 s.
     */
    suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int? = null): TransitionOutcome

    /**
     * „Schnelltimer beenden und neu starten?" (D-098): the active quick timer ends, then this one runs; the brew
     * stays.
     */
    suspend fun finishActiveAndStartQuickTimer(durationSeconds: Int, preAlertSeconds: Int? = null): TransitionOutcome
}

/**
 * The [BrewStarter] of the app: the controller itself. *Beenden & neu
 * starten* is the controller's one-turn operation, which posts the finish's
 * saved sheet as `NotifySessionSaved` (D-054, D-063).
 */
class ControllerBrewStarter @Inject constructor(private val controller: BrewSessionController) : BrewStarter {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override fun activeIn(slot: TimerSlot): TimerUiState.Active? = controller.state(slot).value as? TimerUiState.Active

    override suspend fun startSession(request: BrewStart): TransitionOutcome = controller.startSession(request)

    override suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome =
        controller.finishActiveAndStart(request)

    override suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome =
        controller.startQuickTimer(durationSeconds, preAlertSeconds, run = true)

    override suspend fun finishActiveAndStartQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int?,
    ): TransitionOutcome = controller.startQuickTimer(durationSeconds, preAlertSeconds, run = true, finishActive = true)
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
