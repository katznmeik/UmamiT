package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerDisplay
import com.umamit.core.timer.engine.TimerState

/**
 * One step of the plan as the timer screen, the step dots, the Live Update segments and the Schritte-Sheet show
 * it, with its own pre-alert choice [preAlertSeconds] (null *Automatisch*, 0 *Aus*; TMR-23, D-093).
 */
data class TimerUiStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val status: StepStatus,
    val preAlertSeconds: Int? = null,
)

/**
 * What the timer screen, the active brew bar and the notifications show
 * (04 §4, §5, §8): the controller's `StateFlow`, updated on every accepted
 * transition. Times that move every frame are not in it: the UI computes the
 * remaining time and the overtime from [TimerUiState.Active.display] and the
 * monotonic clock (04 §2.2, §4).
 */
sealed interface TimerUiState {

    /** No session or quick timer is active. */
    data object Idle : TimerUiState

    /**
     * A session or quick timer is active.
     *
     * @property kind the brew session (with its journal row) or the quick timer.
     * @property teaName the session's tea; null for the quick timer („Schnelltimer").
     * @property stepIndex the current step; the number of steps in FINISHED.
     * @property steps the plan with each step's status.
     * @property deadlineElapsedMs RUNNING, ALERTING: the deadline on the monotonic clock.
     * @property deadlineWallMs RUNNING, ALERTING: the same deadline on the wall clock (chronometer,
     *   „Fertig um 14:30" for a long step, TMR-18).
     * @property pausedRemainingMs PAUSED: the time left.
     * @property preAlertLeadSeconds the current step's pre-alert N, played or not; null without one (TMR-23).
     * @property preAlertAtElapsedMs RUNNING: when the pre-alert is due, until it played.
     * @property preAlertPlayed RUNNING, PAUSED: the tick played („Gleich fertig …").
     * @property longStep the current step is a long step (≥ 10 min, or cold/ice brew; TMR-18).
     * @property kettleTempC the current step's temperature for the kettle hint (BRW-10); null in FINISHED.
     * @property demoted the session went quiet after 30 min (TMR-14): no service, regular notification.
     * @property finishWouldDiscard *Sitzung beenden* would discard the session; the UI asks first (TMR-12).
     * @property preAlertBaseSeconds the session's pre-alert base B (recipe ?: global, frozen at the start), from which
     *   the Schritte-Sheet shows each *Automatisch* step's lead, „Auto · 5 s" (SCR-31, TMR-23, D-093).
     * @property longStepsOnly a cold or ice brew: every step is a long step without a pre-alert (TMR-18).
     * @property iceBrew the session is an ice brew (`ICE_BREW`): a cold step's kettle hint reads „Eiswürfel",
     *   not „Kaltes Wasser" (BRW-10).
     * @property leafGrams the session's planned leaf in g, from its session row, for READY's „4,0 g Tee"
     *   (BRW-11, D-097); null without one and for the quick timer.
     * @property waterMl the session's planned water in ml, from its session row, for READY's „250 ml Wasser"
     *   (BRW-11, D-097); null without one (*Ohne Gefäß*) and for the quick timer.
     * @property alertHeld ALERTING: the quick timer's alert waits for the brew's (04 §9.3, D-098): no sound, no alert
     *   notification; it rings once the brew's alert is acknowledged.
     */
    data class Active(
        val kind: SessionKind,
        val teaName: String?,
        val phase: TimerPhase,
        val stepIndex: Int,
        val steps: List<TimerUiStep>,
        val deadlineElapsedMs: Long?,
        val deadlineWallMs: Long?,
        val pausedRemainingMs: Long?,
        val preAlertLeadSeconds: Int?,
        val preAlertAtElapsedMs: Long?,
        val preAlertPlayed: Boolean,
        val longStep: Boolean,
        val kettleTempC: Int?,
        val demoted: Boolean,
        val finishWouldDiscard: Boolean,
        val preAlertBaseSeconds: Int,
        val longStepsOnly: Boolean = false,
        val iceBrew: Boolean = false,
        val leafGrams: Double? = null,
        val waterMl: Int? = null,
        val alertHeld: Boolean = false,
    ) : TimerUiState {
        /** The step ended and the alert plays until *Abgegossen* (03 §3.8, D-051); also while it is held. */
        val alerting: Boolean get() = phase == TimerPhase.ALERTING

        /** The step ended and its alert rings, not held (04 §9.3). */
        val ringing: Boolean get() = alerting && !alertHeld

        /** The controller slot of this timer (04 §3.3): its user events go there. */
        val slot: TimerSlot get() = slotOf(kind)

        /** The journal row; null for the quick timer. */
        val sessionId: String? get() = (kind as? SessionKind.Brew)?.sessionId

        /** The current step; null in FINISHED. */
        val currentStep: TimerUiStep? get() = steps.getOrNull(stepIndex)

        /**
         * Remaining time (rounded up) and overtime (rounded down) in whole
         * seconds at [nowElapsedMs] on the monotonic clock (04 §5): READY shows
         * the planned duration, a running step past its deadline shows 0 until
         * the deadline is handled, FINISHED shows 0.
         */
        fun display(nowElapsedMs: Long): TimerDisplay = when (phase) {
            TimerPhase.READY -> TimerDisplay(currentStep?.plannedSeconds?.toLong() ?: 0, 0)
            TimerPhase.RUNNING -> TimerDisplay(ceilSeconds(checkNotNull(deadlineElapsedMs) - nowElapsedMs), 0)
            TimerPhase.PAUSED -> TimerDisplay(ceilSeconds(checkNotNull(pausedRemainingMs)), 0)
            TimerPhase.ALERTING -> TimerDisplay(0, floorSeconds(nowElapsedMs - checkNotNull(deadlineElapsedMs)))
            TimerPhase.FINISHED -> TimerDisplay(0, 0)
        }
    }
}

/**
 * The identity of the alert playing now (the step and its deadline), or null
 * when none plays. Each alert has its own: an overlay or a route opened during
 * one alert never stays over the next (rule 8, D-058).
 */
fun TimerUiState.alertKey(): String? = (this as? TimerUiState.Active)?.takeIf { it.ringing }?.let {
    "${it.sessionId ?: "quick"}:${it.stepIndex}:${it.deadlineWallMs}"
}

private const val MS_PER_SECOND = 1_000L

private fun ceilSeconds(ms: Long): Long = (ms.coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND

private fun floorSeconds(ms: Long): Long = ms.coerceAtLeast(0) / MS_PER_SECOND

/**
 * What the timer shows of a brew session's row besides its timing (the session's snapshot, never the engine's
 * state): its tea's name, whether it is an ice brew (BRW-10), and its planned leaf and water (BRW-11, D-097).
 * The quick timer has none.
 */
internal data class SessionDetails(
    val teaName: String?,
    val iceBrew: Boolean = false,
    val leafGrams: Double? = null,
    val waterMl: Int? = null,
) {
    companion object {
        fun of(snapshot: SessionSnapshot): SessionDetails = SessionDetails(
            teaName = snapshot.teaName,
            iceBrew = snapshot.method == BrewMethod.ICE_BREW,
            leafGrams = snapshot.leafGrams,
            waterMl = snapshot.waterMl,
        )
    }
}

/**
 * The UI state of engine [state] for a session with [details] (null: the quick timer, or a session whose row could
 * not be read), at [now] (the reading of its transition).
 */
internal fun uiStateOf(state: TimerState, details: SessionDetails?, now: ClockReading): TimerUiState {
    if (state !is TimerState.Active) return TimerUiState.Idle
    val stepIndex = (state as? TimerState.OnStep)?.stepIndex ?: state.session.steps.size
    val steps = state.session.steps.map {
        TimerUiStep(it.kind, it.plannedSeconds, it.tempC, it.status, it.preAlertSeconds)
    }
    return TimerUiState.Active(
        kind = state.session.kind,
        teaName = details?.teaName,
        phase = state.phase,
        stepIndex = stepIndex,
        steps = steps,
        deadlineElapsedMs = (state as? TimerState.WithDeadline)?.deadlineElapsedMs,
        deadlineWallMs = (state as? TimerState.WithDeadline)?.let {
            // A long step's end follows a wall-clock change (E1, TMR-18).
            BrewTimerEngine.longStepEndWallMs(state, now) ?: it.deadlineWallMs
        },
        pausedRemainingMs = (state as? TimerState.Paused)?.remainingMs,
        preAlertLeadSeconds = BrewTimerEngine.preAlertLeadSeconds(state),
        preAlertAtElapsedMs = BrewTimerEngine.dueTimes(state).preAlertAtElapsedMs,
        preAlertPlayed = when (state) {
            is TimerState.Running -> state.preAlertPlayed
            is TimerState.Paused -> state.preAlertPlayed
            else -> false
        },
        longStep = BrewTimerEngine.isLongStep(state),
        kettleTempC = steps.getOrNull(stepIndex)?.tempC,
        demoted = state.session.demoted,
        finishWouldDiscard = BrewTimerEngine.finishWouldDiscard(state),
        preAlertBaseSeconds = state.session.preAlert.inheritedSeconds,
        longStepsOnly = state.session.longStepsOnly,
        iceBrew = details?.iceBrew ?: false,
        leafGrams = details?.leafGrams,
        waterMl = details?.waterMl,
        alertHeld = (state as? TimerState.Alerting)?.held ?: false,
    )
}
