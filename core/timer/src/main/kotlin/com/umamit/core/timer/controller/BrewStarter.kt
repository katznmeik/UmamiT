package com.umamit.core.timer.controller

import com.umamit.core.model.BrewSession
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What Start (SCR-02) and the setup (SCR-04) need from the
 * [BrewSessionController] to start a brew (TMR-01, BRW-11, TMR-21, TMR-22): its state,
 * for *Aufguss fortsetzen* and the second-timer questions, and the
 * starts. Any timer starts while one other runs (D-098, D-108): a second brew session of another tea, or a quick
 * timer; a start of a tea with an active session, a second quick timer and any third timer are refused, so the
 * UI opens that session or asks which timer to end. Features depend only on `core:*` (06 §3), so the seam lives
 * here; the timer screen's own seam is `BrewTimer` in `feature:brew` (D-058).
 */
interface BrewStarter {
    /** The controller's [BrewSessionController.state]: the timer in front. */
    val state: StateFlow<TimerUiState>

    /** The active timer of [slot] now (the brew session, the quick timer); null while that slot is idle. */
    fun activeIn(slot: TimerSlot): TimerUiState.Active?

    /** The active timer of [slot] as it changes, whichever timer is in front; null while that slot is idle. */
    fun observeActive(slot: TimerSlot): Flow<TimerUiState.Active?>

    /**
     * *Brühen starten* / *Nochmal brühen*: the session waits in READY in a free brew slot, also while another
     * timer runs (D-108). Returns `Rejected(ActiveTimerExists)` naming the session of the same tea (the UI opens
     * it), and `Rejected(TwoTimersActive)` naming the active slots while two timers are active (the UI asks
     * „Welchen Timer beenden?", TMR-21).
     */
    suspend fun startSession(request: BrewStart): TransitionOutcome

    /**
     * *Beenden & neu starten* (TMR-21, D-108): the timer of [ending] ends, a session as by
     * *Sitzung beenden* (saved, or discarded without a completed non-rinse
     * step), the quick timer as by *Abbrechen*; then [request] starts. No other timer is touched (D-098). With
     * [endingSession] (the answer's [TimerToEnd.sessionId]) the slot ends only while it still holds that session.
     */
    suspend fun finishActiveAndStart(
        request: BrewStart,
        ending: TimerSlot = TimerSlot.BREW,
        endingSession: String? = null,
    ): TransitionOutcome

    /**
     * *Start* on the quick timer (SCR-07, TMR-22, 04 §10): one infusion of
     * [durationSeconds] with the *Vorwarnung* chosen on SCR-07, [preAlertSeconds]
     * (D-097: null *Automatisch* with B = the global pre-alert, 0 *Aus*, else
     * 5–30 s as set), running at once (SCR-07's *Start* is the start of its one
     * step). Nothing is recorded. It starts while a brew session runs (D-098). Returns
     * `Rejected(ActiveTimerExists)` while another quick timer is active (the UI asks „Schnelltimer beenden und
     * neu starten?"), `Rejected(TwoTimersActive)` while two brew sessions are active (D-108) and
     * `Rejected(InvalidQuickTimer)` outside
     * 5 s – 24 h or for a choice other than 0 or 5–30 s.
     */
    suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int? = null): TransitionOutcome

    /**
     * „Schnelltimer beenden und neu starten?" (D-098): the active quick timer ends, then this one runs; the brew
     * stays. With [ending] a brew slot, that session ends instead, as by *Sitzung beenden* (D-108), and with
     * [endingSession] only while the slot still holds that session.
     */
    suspend fun finishActiveAndStartQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int? = null,
        ending: TimerSlot = TimerSlot.QUICK,
        endingSession: String? = null,
    ): TransitionOutcome
}

/**
 * The [BrewStarter] of the app: the controller itself. *Beenden & neu
 * starten* is the controller's one-turn operation, which posts the finish's
 * saved sheet as `NotifySessionSaved` (D-054, D-063).
 */
class ControllerBrewStarter @Inject constructor(private val controller: BrewSessionController) : BrewStarter {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override fun activeIn(slot: TimerSlot): TimerUiState.Active? = controller.state(slot).value as? TimerUiState.Active

    override fun observeActive(slot: TimerSlot): Flow<TimerUiState.Active?> =
        controller.state(slot).map { it as? TimerUiState.Active }.distinctUntilChanged()

    override suspend fun startSession(request: BrewStart): TransitionOutcome = controller.startSession(request)

    override suspend fun finishActiveAndStart(
        request: BrewStart,
        ending: TimerSlot,
        endingSession: String?,
    ): TransitionOutcome = controller.finishActiveAndStart(request, ending, endingSession)

    override suspend fun startQuickTimer(durationSeconds: Int, preAlertSeconds: Int?): TransitionOutcome =
        controller.startQuickTimer(durationSeconds, preAlertSeconds, run = true)

    override suspend fun finishActiveAndStartQuickTimer(
        durationSeconds: Int,
        preAlertSeconds: Int?,
        ending: TimerSlot,
        endingSession: String?,
    ): TransitionOutcome = controller.startQuickTimer(
        durationSeconds,
        preAlertSeconds,
        run = true,
        ending = ending,
        endingSession = endingSession,
    )
}

/**
 * *Nochmal brühen* (BRW-11): a start with the parameters of [last], the tea's
 * last session: its snapshot of tea, recipe, vessel, leaf and water, its steps
 * as planned (in order, each with its own pre-alert; D-093) and its pre-alert
 * base (B from umamit.db v2, the effective value before), frozen like a
 * recipe's. A session finished early is brewed again with its started steps,
 * its ended rows (TMR-12, D-062): from v0.4.0 it keeps its never-started rows
 * too (Q-19), which only *So nochmal brühen* (BRW-06) prefills.
 */
fun brewAgainStart(last: BrewSession): BrewStart = BrewStart(
    snapshot = last.snapshot,
    steps = last.endedSteps.sortedBy { it.position }.map {
        PlannedStep(it.kind, it.plannedSeconds, it.tempC, it.preAlertSeconds)
    },
    recipePreAlertSeconds = last.preAlertSeconds,
)
