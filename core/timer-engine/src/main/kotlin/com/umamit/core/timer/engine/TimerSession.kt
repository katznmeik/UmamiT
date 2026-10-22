package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import java.time.ZoneId

/**
 * One step as planned by the recipe snapshot or the Schritte-Sheet (04 §1):
 * its [kind], [plannedSeconds] and optional temperature [tempC] (a multiple
 * of 5 °C, GEN-12; the engine does not use it).
 */
data class PlannedStep(val kind: StepKind, val plannedSeconds: Int, val tempC: Int? = null) {
    init {
        require(plannedSeconds > 0) { "plannedSeconds must be positive, was $plannedSeconds" }
    }
}

/** Where a step of the session plan stands. */
enum class StepStatus {
    /** Not started yet; the only status the Schritte-Sheet may replace (SCR-31). */
    PENDING,

    /** Started and not ended: the step of a Running, Paused or Alerting state. */
    RUNNING,

    /** Ended by *Abgegossen*, *Aufguss jetzt beenden*, the overtime timeout or *Sitzung beenden*. */
    DONE,

    /** Skipped in Ready (TMR-09). */
    SKIPPED,
}

/**
 * What the journal records for an ended step (04 §5.1, `session_step`).
 *
 * @property plannedSeconds planned duration at step start.
 * @property actualSeconds active time from *Start* to the end, without pauses and with overtime, rounded to 1 s.
 * @property pausedSeconds total paused time, rounded to 1 s.
 * @property overtimeSeconds deadline to *Abgegossen*, rounded to 1 s; 0 when ended early.
 * @property startedAtWallMs wall time of *Start*; null for a skipped step.
 * @property endedAtWallMs wall time of the end; null for a skipped step.
 * @property overtimeAutoAck the alert was not acknowledged and the overtime timeout ended the step.
 */
data class StepRecord(
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val pausedSeconds: Int,
    val overtimeSeconds: Int,
    val startedAtWallMs: Long?,
    val endedAtWallMs: Long?,
    val skipped: Boolean,
    val overtimeAutoAck: Boolean = false,
)

/** One step of the session plan: the [PlannedStep] values plus its [status] and, once ended, its [record]. */
data class SessionStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val status: StepStatus = StepStatus.PENDING,
    val record: StepRecord? = null,
)

/**
 * The pre-alert values a session was started with (TMR-23): the session's
 * own override from the Schritte-Sheet, the recipe's value and the global
 * setting, so the effective lead time is computable without I/O.
 * In each, 0 means off and null means "not set".
 */
data class PreAlertSettings(
    val sessionOverrideSeconds: Int? = null,
    val recipeSeconds: Int? = null,
    val globalSeconds: Int = DEFAULT_GLOBAL_SECONDS,
) {
    companion object {
        /** `preAlertSeconds` default (05 §6). */
        const val DEFAULT_GLOBAL_SECONDS = 10
    }
}

/**
 * The active session as the engine sees it: everything every non-idle state
 * shares.
 *
 * @property sessionId the `brew_session` row; null only for the quick timer.
 * @property steps the session plan in order, with each step's status.
 * @property zone the session's time zone (`brew_session.zone_id`), for local-date rules.
 * @property lastInteractionWallMs last user event (`last_interaction_wall_ms`), for the waiting timeouts (TMR-14).
 * @property isQuickTimer the quick timer (04 §10): records nothing.
 */
data class TimerSession(
    val sessionId: String?,
    val steps: List<SessionStep>,
    val preAlert: PreAlertSettings,
    val zone: ZoneId,
    val lastInteractionWallMs: Long,
    val isQuickTimer: Boolean = false,
) {
    /**
     * True when at least one step other than a rinse was completed (not
     * skipped): the session is worth saving (TMR-12 AC1).
     */
    val hasCompletedBrewStep: Boolean
        get() = steps.any { it.status == StepStatus.DONE && it.kind != StepKind.RINSE }

    /** This session after a user event at [now]. */
    internal fun touched(now: ClockReading): TimerSession = copy(lastInteractionWallMs = now.wallMs)

    /** This session with step [index] replaced by [transform] of it. */
    internal fun withStep(index: Int, transform: (SessionStep) -> SessionStep): TimerSession =
        copy(steps = steps.mapIndexed { i, step -> if (i == index) transform(step) else step })
}
