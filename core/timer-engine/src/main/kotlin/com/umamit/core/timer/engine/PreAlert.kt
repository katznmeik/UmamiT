package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.TimerEffect.PlayPreAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification

/*
 * The pre-alert tick (TMR-23, 04 §5) and the long-step threshold (TMR-18).
 * The tick is at most once per step: `preAlertPlayed` is set in the
 * transition that plays it, and a played tick never comes back (E6).
 */

/**
 * Planned duration from which a hot step is a long step (`LONG_STEP_THRESHOLD`, 04 §1): 60 min from v0.3.0
 * (D-098, #122; 10 min until v0.2.2). Not the duration rounding of PRF-04, which keeps its 10-min boundary
 * ([ROUND_TO_MINUTES_FROM_SECONDS]).
 */
internal const val LONG_STEP_SECONDS = 3_600

/** A step needs at least N + 5 s for a pre-alert (E7, D-035). */
private const val PRE_ALERT_MIN_SPARE_SECONDS = 5

/** A rinse shorter than this has no pre-alert (TMR-23). */
private const val RINSE_MIN_SECONDS = 15

/** A late pre-alert still plays while at least this much is left before the deadline (D-053). */
internal const val PRE_ALERT_MIN_LEFT_MS = 1_000L

/**
 * The pre-alert lead time of step [index] in milliseconds, or null when the
 * step has none: N = 0, a step shorter than N + 5 s, a rinse shorter than
 * 15 s, or a long step (TMR-23, 04 §5). N is the step's own: the explicit
 * value, else the automatic rule (D-093).
 */
internal fun TimerSession.preAlertLeadMs(index: Int): Long? =
    preAlertLeadSecondsOf(steps[index].planned, preAlert, isLongStep(index))?.let { it * MS_PER_SECOND }

/**
 * The lead in seconds of [step] with the session base [settings], or null when it has none (TMR-23, 04 §5): the
 * one rule for a step of a session and for a planned step the Schritte-Sheet or the setup shows (D-093, #92).
 */
internal fun preAlertLeadSecondsOf(step: PlannedStep, settings: PreAlertSettings, longStep: Boolean): Int? {
    val n = settings.leadSecondsFor(step.plannedSeconds, step.preAlertSeconds)
    val none = n == 0 ||
        step.plannedSeconds < n + PRE_ALERT_MIN_SPARE_SECONDS ||
        (step.kind == StepKind.RINSE && step.plannedSeconds < RINSE_MIN_SECONDS) ||
        longStep
    return if (none) null else n
}

/**
 * A step of [plannedSeconds] is a long step (≥ 60 min), or every step is in a cold or ice brew (TMR-18, 04 §1,
 * D-098): the one rule of the engine, the Schritte-Sheet and the setup.
 */
internal fun isLongStepOf(plannedSeconds: Int, longStepsOnly: Boolean): Boolean =
    longStepsOnly || plannedSeconds >= LONG_STEP_SECONDS

/**
 * When the pre-alert of step [index] of [session] is due for a step that
 * counts down to [deadlineElapsedMs] from [now]; null when the step has none,
 * when it already played, or when its time is not after now (skipped, E6).
 */
internal fun preAlertAt(
    session: TimerSession,
    index: Int,
    deadlineElapsedMs: Long,
    played: Boolean,
    now: ClockReading,
): Long? {
    val at = session.preAlertLeadMs(index)?.let { deadlineElapsedMs - it }
    return at?.takeIf { !played && it > now.elapsedMs }
}

/**
 * `preAlertReached` (04 §3.2): plays the tick while at least 1 s is left,
 * otherwise only consumes the due time, so no tick sounds at or after the
 * deadline (D-048). Either way the pre-alert counts as played. The tick plays
 * in the service (04 §9), so a tick ensures it first, as the deadline does
 * before the alert: after a refused restart at recovery the exact pre-alert
 * alarm may start it (D-054).
 */
internal fun preAlertReached(state: TimerState.Running, now: ClockReading): TimerTransition {
    val plays = state.deadlineElapsedMs - now.elapsedMs >= PRE_ALERT_MIN_LEFT_MS
    val tick = if (plays) listOf(StartForeground, PlayPreAlert) else emptyList()
    return accepted(state.copy(preAlertAtElapsedMs = null, preAlertPlayed = true), side = tick + UpdateNotification)
}
