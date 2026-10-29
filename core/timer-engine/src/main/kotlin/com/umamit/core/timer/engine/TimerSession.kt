package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import java.time.ZoneId

/**
 * One step as planned by the recipe snapshot or the Schritte-Sheet (04 §1):
 * its [kind], [plannedSeconds], optional temperature [tempC] (a multiple
 * of 5 °C, GEN-12; the engine does not use it) and its own pre-alert
 * [preAlertSeconds] (TMR-23, D-093): null *Automatisch*, 0 *Aus*, else the
 * lead in seconds (5–30 s in the sheet).
 */
data class PlannedStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val preAlertSeconds: Int? = null,
) {
    init {
        require(plannedSeconds > 0) { "plannedSeconds must be positive, was $plannedSeconds" }
        require((preAlertSeconds ?: 0) >= 0) { "a pre-alert is never negative, was $preAlertSeconds" }
    }
}

/**
 * Where a step of the session plan stands. The plan is always a prefix of
 * ended steps (DONE, SKIPPED), at most one STARTED step, then PENDING steps.
 */
enum class StepStatus {
    /** Not started yet; the only status the Schritte-Sheet may replace (SCR-31). */
    PENDING,

    /** Started and not ended: the step of a Running, Paused or Alerting state. */
    STARTED,

    /** Ended by *Abgegossen*, *Aufguss jetzt beenden*, the overtime timeout or *Sitzung beenden*. */
    DONE,

    /** Skipped in Ready (TMR-09). */
    SKIPPED,
}

/**
 * What the journal records for an ended step (04 §5.1, `session_step`).
 * Durations are rounded to whole seconds, half up.
 *
 * @property plannedSeconds planned duration at step start.
 * @property actualSeconds active time from *Start* to the end, without pauses and with overtime.
 * @property pausedSeconds total paused time.
 * @property overtimeSeconds deadline to the end; 0 when ended before the deadline.
 * @property startedAtWallMs wall time of *Start*; null for a skipped step.
 * @property endedAtWallMs wall time of the end; null for a skipped step.
 * @property overtimeAutoAck the alert was not acknowledged and the overtime timeout ended the step (#31).
 */
data class StepRecord(
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val pausedSeconds: Int,
    val overtimeSeconds: Int,
    val startedAtWallMs: Long?,
    val endedAtWallMs: Long?,
    val skipped: Boolean,
    val overtimeAutoAck: Boolean,
)

/**
 * One step of the session plan: the [PlannedStep] values, its own pre-alert [preAlertSeconds] (null
 * *Automatisch*, 0 *Aus*; `session_step.pre_alert_seconds`), plus its [status] and, once ended, its [record].
 */
data class SessionStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val status: StepStatus,
    val record: StepRecord?,
    val preAlertSeconds: Int? = null,
) {
    init {
        require(plannedSeconds > 0) { "plannedSeconds must be positive, was $plannedSeconds" }
        require((preAlertSeconds ?: 0) >= 0) { "a pre-alert is never negative, was $preAlertSeconds" }
    }

    /** The planned values of this step, its pre-alert included. */
    val planned: PlannedStep get() = PlannedStep(kind, plannedSeconds, tempC, preAlertSeconds)
}

/**
 * The pre-alert base of a session (TMR-23, 04 §5): [inheritedSeconds] is the
 * base B of the automatic rule, the recipe value or else the global setting,
 * frozen at the start (`active_brew.pre_alert_inherited_seconds`). Each step
 * carries its own choice ([SessionStep.preAlertSeconds], from umamit.db v2,
 * D-093), so each step's lead is computable without I/O, and a later change of
 * the recipe or the settings never changes an active session. 0 means off.
 */
data class PreAlertSettings(val inheritedSeconds: Int) {
    init {
        require(inheritedSeconds >= 0) { "pre-alert seconds are never negative" }
    }

    /**
     * N of a step of [plannedSeconds] with its own choice [stepSeconds], before the skip rules (04 §5, D-093):
     * an explicit value as set (0: *Aus*), else (*Automatisch*) N = min(B, 5 · ⌊d / 15⌋), a third of the step
     * rounded down to whole 5 s, never more than B.
     */
    internal fun leadSecondsFor(plannedSeconds: Int, stepSeconds: Int?): Int = stepSeconds
        ?: minOf(inheritedSeconds, AUTO_LEAD_UNIT_SECONDS * (plannedSeconds / AUTO_LEAD_PART_SECONDS))

    companion object {
        /** `preAlertSeconds` default (05 §6). */
        const val DEFAULT_GLOBAL_SECONDS = 10

        /** The automatic N is a whole multiple of 5 s (TMR-23, D-093). */
        private const val AUTO_LEAD_UNIT_SECONDS = 5

        /** The automatic N grows by one unit per 15 s of the step: a third of it (TMR-23, D-093). */
        private const val AUTO_LEAD_PART_SECONDS = 15

        /** The settings at the start of a session: the recipe's [recipeSeconds] (null: not set) ?: [globalSeconds]. */
        fun inherited(recipeSeconds: Int?, globalSeconds: Int) = PreAlertSettings(recipeSeconds ?: globalSeconds)
    }
}

/**
 * A step's pre-alert choice in the Schritte-Sheet (SCR-31, TMR-23), for this session only; stored as
 * [explicitSeconds] with the step (`pre_alert_seconds`: null, 0 or the lead).
 */
sealed interface PreAlertChoice {

    /** The value stored with a step: null for *Automatisch*, 0 for *Aus*, else the lead in seconds. */
    val explicitSeconds: Int?

    /**
     * *Automatisch* (shown with its lead, „Auto · 5 s", SCR-31): no explicit value; the step gets the automatic N
     * from the recipe or global value B (TMR-23, D-093).
     */
    data object Inherit : PreAlertChoice {
        override val explicitSeconds: Int? get() = null
    }

    /** *Aus*: no pre-alert for the step. */
    data object Off : PreAlertChoice {
        override val explicitSeconds: Int get() = 0
    }

    /** A lead time of [seconds] (5–30 s in the sheet), as set (E7 still applies). */
    data class Seconds(val seconds: Int) : PreAlertChoice {
        init {
            require(seconds > 0) { "a pre-alert lead time is positive, was $seconds; use Off" }
        }

        override val explicitSeconds: Int get() = seconds
    }

    companion object {
        /** The choice a step with the stored value [explicitSeconds] has (null *Automatisch*, 0 *Aus*). */
        fun of(explicitSeconds: Int?): PreAlertChoice = when (explicitSeconds) {
            null -> Inherit
            0 -> Off
            else -> Seconds(explicitSeconds)
        }
    }
}

/** What the active timer is: a brew session with a journal row, or the quick timer (04 §10). */
sealed interface SessionKind {

    /** A brew session; its steps and outcome are recorded in the `brew_session` row [sessionId]. */
    data class Brew(val sessionId: String) : SessionKind

    /** The quick timer: one infusion, nothing recorded (TMR-22). */
    data object Quick : SessionKind
}

/**
 * The active session as the engine sees it: everything every non-idle state
 * shares.
 *
 * @property kind brew session (with its row) or quick timer.
 * @property steps the session plan in order, with each step's status.
 * @property zone the session's time zone (`brew_session.zone_id`), for local-date rules.
 * @property lastInteractionWallMs last user event (`last_interaction_wall_ms`), for the waiting timeouts (TMR-14).
 * @property bootCount the boot that every `…ElapsedMs` value of the state belongs to (`boot_count`, 04 §7.2).
 * @property demoted the foreground service was stopped by `idleDemote` and a regular notification shown
 *   (`demoted`, 04 §3.2); any user event clears it (D-053).
 * @property longStepsOnly a cold or ice brew: every step is a long step (TMR-18); from `brew_session.method`.
 */
data class TimerSession(
    val kind: SessionKind,
    val steps: List<SessionStep>,
    val preAlert: PreAlertSettings,
    val zone: ZoneId,
    val lastInteractionWallMs: Long,
    val bootCount: Int,
    val demoted: Boolean,
    val longStepsOnly: Boolean,
) {
    /** The `brew_session` row to record into; null for the quick timer, which records nothing. */
    internal val brewSessionId: String?
        get() = (kind as? SessionKind.Brew)?.sessionId

    /** At least one step other than a rinse is DONE (TMR-12 AC1). */
    internal val hasCompletedBrewStep: Boolean
        get() = steps.any { it.status == StepStatus.DONE && it.kind != StepKind.RINSE }

    /** Number of started or ended steps: the prefix that `editSteps` and `addStep` never change. */
    internal val startedPrefixSize: Int
        get() = steps.indexOfFirst { it.status == StepStatus.PENDING }.let { if (it < 0) steps.size else it }

    /** This session after a user event at [now]: the idle timeouts restart and a demoted session is active again. */
    internal fun touched(now: ClockReading): TimerSession = copy(lastInteractionWallMs = now.wallMs, demoted = false)

    /** Step [index] is a long step (≥ 10 min, or any step of a cold or ice brew; TMR-18, 04 §1). */
    internal fun isLongStep(index: Int): Boolean = isLongStepOf(steps[index].plannedSeconds, longStepsOnly)

    /** This session with step [index] replaced by [transform] of it. */
    internal fun withStep(index: Int, transform: (SessionStep) -> SessionStep): TimerSession =
        copy(steps = steps.mapIndexed { i, step -> if (i == index) transform(step) else step })
}
