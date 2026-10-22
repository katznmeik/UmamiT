package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import java.time.ZoneId

/**
 * One step as planned by the recipe snapshot or the Schritte-Sheet (04 §1):
 * its [kind], [plannedSeconds] and optional temperature [tempC] (a multiple
 * of 5 °C, GEN-12; the engine does not use it).
 */
data class PlannedStep(val kind: StepKind, val plannedSeconds: Int, val tempC: Int?) {
    init {
        require(plannedSeconds > 0) { "plannedSeconds must be positive, was $plannedSeconds" }
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

/** One step of the session plan: the [PlannedStep] values plus its [status] and, once ended, its [record]. */
data class SessionStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val status: StepStatus,
    val record: StepRecord?,
) {
    /** The planned values of this step. */
    val planned: PlannedStep get() = PlannedStep(kind, plannedSeconds, tempC)
}

/**
 * The pre-alert lead time of a session (TMR-23, 04 §5): the session's own
 * [sessionOverrideSeconds] from the Schritte-Sheet (null: *Wie Einstellung*),
 * else [inheritedSeconds], which is the recipe value or else the global
 * setting, frozen at the start. The effective value is thus computable without
 * I/O, and a later change of the recipe or the settings never changes an
 * active session. 0 means off.
 */
data class PreAlertSettings(val sessionOverrideSeconds: Int?, val inheritedSeconds: Int) {
    init {
        require(inheritedSeconds >= 0 && (sessionOverrideSeconds ?: 0) >= 0) { "pre-alert seconds are never negative" }
    }

    /** N of 04 §5: session override ?: recipe ?: global. */
    val effectiveSeconds: Int get() = sessionOverrideSeconds ?: inheritedSeconds

    companion object {
        /** `preAlertSeconds` default (05 §6). */
        const val DEFAULT_GLOBAL_SECONDS = 10

        /** The settings at the start of a session: the recipe's [recipeSeconds] (null: not set) ?: [globalSeconds]. */
        fun inherited(recipeSeconds: Int?, globalSeconds: Int) =
            PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = recipeSeconds ?: globalSeconds)
    }
}

/** The pre-alert selector of the Schritte-Sheet (SCR-31), for this session only. */
sealed interface PreAlertChoice {

    /** *Wie Einstellung*: no session override; the recipe or global value applies. */
    data object Inherit : PreAlertChoice

    /** *Aus*: no pre-alert in this session. */
    data object Off : PreAlertChoice

    /** A lead time of [seconds] (5–30 s in the sheet). */
    data class Seconds(val seconds: Int) : PreAlertChoice {
        init {
            require(seconds > 0) { "a pre-alert lead time is positive, was $seconds; use Off" }
        }
    }

    companion object {
        /** The choice the sheet shows for a session with [settings]. */
        fun of(settings: PreAlertSettings): PreAlertChoice = when (val override = settings.sessionOverrideSeconds) {
            null -> Inherit
            0 -> Off
            else -> Seconds(override)
        }
    }
}

/** These settings with the session override that [choice] stands for. */
internal fun PreAlertSettings.with(choice: PreAlertChoice): PreAlertSettings = copy(
    sessionOverrideSeconds = when (choice) {
        PreAlertChoice.Inherit -> null
        PreAlertChoice.Off -> 0
        is PreAlertChoice.Seconds -> choice.seconds
    },
)

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
 *   (`demoted`, 04 §3.2); set and cleared by the idle rules of #31.
 */
data class TimerSession(
    val kind: SessionKind,
    val steps: List<SessionStep>,
    val preAlert: PreAlertSettings,
    val zone: ZoneId,
    val lastInteractionWallMs: Long,
    val bootCount: Int,
    val demoted: Boolean,
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

    /** This session after a user event at [now]. */
    internal fun touched(now: ClockReading): TimerSession = copy(lastInteractionWallMs = now.wallMs)

    /** This session with step [index] replaced by [transform] of it. */
    internal fun withStep(index: Int, transform: (SessionStep) -> SessionStep): TimerSession =
        copy(steps = steps.mapIndexed { i, step -> if (i == index) transform(step) else step })
}
