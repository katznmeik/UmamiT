package com.umamit.core.timer.engine

import com.umamit.core.common.ClockReading
import java.time.Instant
import java.time.ZoneId

/**
 * When the time-based events of a state are due (04 §6.2). Each field is one
 * alarm type with a fixed request code; null means "not due in this state"
 * (cancel it). The controller schedules alarms and its in-process wake-up from
 * this set after every reduce, and recovery recomputes it from a persisted
 * state. A time-based event is accepted only when it is due at `now`.
 *
 * @property preAlertAtElapsedMs the pre-alert tick of the running step, until it played.
 * @property deadlineElapsedMs the deadline of the running step.
 * @property overtimeTimeoutAtElapsedMs the overtime timeout of the alerting step, 10 min after its deadline; for a
 *   quick timer 10 min after its alert last started ringing, none while it is held (D-098).
 * @property idleDemoteAtWallMs the idle demote of a waiting brew session (Ready, Paused, Finished) that is not
 *   demoted yet, 30 min after it became idle (TMR-14).
 * @property midnightAtWallMs the midnight timeout of a waiting brew session, demoted or not: the first local
 *   midnight in the session's zone at least 6 h after it became idle (TMR-14); an inexact alarm.
 * @property quickCancelAtWallMs the quick timer's inactivity cancel in Ready or Paused, 30 min after the
 *   last interaction (04 §10), on the wall clock like the other waiting timeouts.
 */
data class DueTimes(
    val preAlertAtElapsedMs: Long? = null,
    val deadlineElapsedMs: Long? = null,
    val overtimeTimeoutAtElapsedMs: Long? = null,
    val idleDemoteAtWallMs: Long? = null,
    val midnightAtWallMs: Long? = null,
    val quickCancelAtWallMs: Long? = null,
) {
    companion object {
        /** Nothing due: every alarm cancelled. */
        val NONE = DueTimes()
    }
}

/** Inactivity after which an unstarted or paused quick timer is cancelled (04 §10). */
internal const val QUICK_TIMER_IDLE_MS = 30 * 60_000L

/** Inactivity after which a waiting brew session is demoted (TMR-14). */
internal const val IDLE_DEMOTE_MS = 30 * 60_000L

/** The midnight timeout is the first local midnight at least this long after the session became idle (TMR-14). */
internal const val MIDNIGHT_MIN_IDLE_MS = 6 * 3_600_000L

/**
 * The first start of a local day in [zone] at or after [wallMs]. A day whose
 * midnight is skipped by a daylight-saving change starts at its first valid
 * time ([java.time.LocalDate.atStartOfDay]); the hours are real hours.
 */
internal fun firstMidnightAtOrAfter(wallMs: Long, zone: ZoneId): Long {
    val instant = Instant.ofEpochMilli(wallMs)
    val date = instant.atZone(zone).toLocalDate()
    val startOfDay = date.atStartOfDay(zone).toInstant()
    val midnight = if (startOfDay == instant) startOfDay else date.plusDays(1).atStartOfDay(zone).toInstant()
    return midnight.toEpochMilli()
}

/** True when a time-based event due at [dueAtElapsedMs] (null: not due in this state) is due at [now]. */
internal fun isDue(dueAtElapsedMs: Long?, now: ClockReading) = dueAtElapsedMs != null && now.elapsedMs >= dueAtElapsedMs

/** [isDue] for an event due on the wall clock at [dueAtWallMs]. */
internal fun isDueWall(dueAtWallMs: Long?, now: ClockReading) = dueAtWallMs != null && now.wallMs >= dueAtWallMs

internal fun dueTimesOf(state: TimerState): DueTimes = when (state) {
    is TimerState.Running -> DueTimes(
        preAlertAtElapsedMs = state.preAlertAtElapsedMs.takeUnless { state.preAlertPlayed },
        deadlineElapsedMs = state.deadlineElapsedMs,
    )

    is TimerState.Alerting -> DueTimes(overtimeTimeoutAtElapsedMs = state.overtimeTimeoutAtElapsedMs)

    is TimerState.Waiting -> when (state.session.kind) {
        SessionKind.Quick -> DueTimes(quickCancelAtWallMs = quickCancelAt(state.session))

        is SessionKind.Brew -> DueTimes(
            idleDemoteAtWallMs = (state.idleSinceWallMs + IDLE_DEMOTE_MS).takeUnless { state.session.demoted },
            midnightAtWallMs = firstMidnightAtOrAfter(state.idleSinceWallMs + MIDNIGHT_MIN_IDLE_MS, state.session.zone),
        )
    }

    TimerState.Idle -> DueTimes.NONE
}

/**
 * The time-based events of [state] whose time has come at [now], in apply
 * order: the deadline before the pre-alert (so no tick sounds at the
 * deadline), then the overtime timeout; for a waiting state the quick-timer
 * cancel, the midnight timeout, then idle demote, as recovery orders them
 * (D-053). An event that an earlier one made obsolete is ignored harmlessly.
 */
internal fun dueEventsOf(state: TimerState, now: ClockReading): List<TimerEvent> {
    val due = dueTimesOf(state)
    return listOfNotNull(
        TimerEvent.DeadlineReached.takeIf { isDue(due.deadlineElapsedMs, now) },
        TimerEvent.PreAlertReached.takeIf { isDue(due.preAlertAtElapsedMs, now) },
        TimerEvent.OvertimeTimeout.takeIf { isDue(due.overtimeTimeoutAtElapsedMs, now) },
        TimerEvent.QuickTimerTimeout.takeIf { isDueWall(due.quickCancelAtWallMs, now) },
        TimerEvent.MidnightTimeout.takeIf { isDueWall(due.midnightAtWallMs, now) },
        TimerEvent.IdleDemote.takeIf { isDueWall(due.idleDemoteAtWallMs, now) },
    )
}

/**
 * Since when a waiting state is idle: the last interaction, or the moment it
 * started waiting if that is later (a step ended by the overtime timeout, not
 * by the user). The waiting timeouts count from here, on the wall clock (D-048, D-053).
 */
internal val TimerState.Waiting.idleSinceWallMs: Long
    get() = maxOf(session.lastInteractionWallMs, waitingSinceWallMs)

private fun quickCancelAt(session: TimerSession): Long? =
    (session.lastInteractionWallMs + QUICK_TIMER_IDLE_MS).takeIf { session.kind == SessionKind.Quick }
