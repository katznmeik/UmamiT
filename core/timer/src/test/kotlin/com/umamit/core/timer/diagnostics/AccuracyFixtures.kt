package com.umamit.core.timer.diagnostics

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerAlarmKind

// The cues of the accuracy report's tests (AccuracyReportTest, TwoBrewsAccuracyReportTest): a step starts at
// 10:00:00.000 (mono 0).

/** A step whose events were handled [preAlertMs] and [alertMs] late, the sounds started then or as given. */
internal fun step(
    step: Int,
    preAlertMs: Long,
    alertMs: Long,
    preAlertPlayedMs: Long = preAlertMs,
    alertPlayedMs: Long = alertMs,
) = StepFireTimes(
    step = step,
    preAlert = CueTimes(
        handled = fire(TimerAlarmKind.PRE_ALERT, "WAKE_UP", "10:00:50", 50_000, preAlertMs),
        played = fire(TimerAlarmKind.PRE_ALERT, FireTime.PLAYER, "10:00:50", 50_000, preAlertPlayedMs),
    ),
    alert = CueTimes(
        handled = fire(TimerAlarmKind.DEADLINE, "ALARM", "10:01:00", 60_000, alertMs),
        played = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:01:00", 60_000, alertPlayedMs),
    ),
)

/**
 * The quick timer that ran alongside brew step [step] (D-098): its 25 s with a 10 s pre-alert from the step's
 * start, so its cues come before the brew's, handled [preAlertMs] and [alertMs] late, the sounds then or as given.
 */
internal fun quick(
    step: Int,
    preAlertMs: Long,
    alertMs: Long,
    preAlertPlayedMs: Long = preAlertMs,
    alertPlayedMs: Long = alertMs,
) = StepFireTimes(
    step = step,
    preAlert = CueTimes(
        handled = fire(TimerAlarmKind.PRE_ALERT, "ALARM", "10:00:15", 15_000, preAlertMs, TimerSlot.QUICK),
        played = fire(
            TimerAlarmKind.PRE_ALERT,
            FireTime.PLAYER,
            "10:00:15",
            15_000,
            preAlertPlayedMs,
            TimerSlot.QUICK,
        ),
    ),
    alert = CueTimes(
        handled = fire(TimerAlarmKind.DEADLINE, "WAKE_UP", "10:00:25", 25_000, alertMs, TimerSlot.QUICK),
        played = fire(TimerAlarmKind.DEADLINE, FireTime.PLAYER, "10:00:25", 25_000, alertPlayedMs, TimerSlot.QUICK),
    ),
)

/**
 * The second brew of two-brews step [step] (D-108): 66 s with a 10 s pre-alert from the step's start, so its
 * pre-alert comes at 56 s, handled and played [preAlertMs] late, and its deadline at 66 s, handled 15 ms late while
 * the first brew's alert rings, held [heldMs] until *Abgegossen* and sounding [releaseMs] after its release.
 */
internal fun second(step: Int, preAlertMs: Long, heldMs: Long, releaseMs: Long) = SecondBrewStepFireTimes(
    step = step,
    preAlert = CueTimes(
        handled = fire(TimerAlarmKind.PRE_ALERT, "ALARM", "10:00:56", 56_000, preAlertMs, TimerSlot.BREW_2),
        played = fire(TimerAlarmKind.PRE_ALERT, FireTime.PLAYER, "10:00:56", 56_000, preAlertMs, TimerSlot.BREW_2),
    ),
    alert = HeldCueTimes(
        handled = fire(TimerAlarmKind.DEADLINE, "WAKE_UP", "10:01:06", 66_000, 15, TimerSlot.BREW_2),
        released = released(66_000 + heldMs, releaseMs, heldMs),
    ),
)

/** The sound of the second brew's alert released at [releaseMono] after a hold of [heldMs], [deltaMs] late. */
private fun released(releaseMono: Long, deltaMs: Long, heldMs: Long): FireTime {
    val wall = { mono: Long -> "10:%02d:%02d.%03d".format(mono / 60_000, mono / 1_000 % 60, mono % 1_000) }
    return FireTime(
        kind = TimerAlarmKind.DEADLINE,
        source = FireTime.PLAYER,
        scheduledWall = wall(releaseMono),
        scheduledMonoMs = releaseMono,
        actualWall = wall(releaseMono + deltaMs),
        actualMonoMs = releaseMono + deltaMs,
        deltaMs = deltaMs,
        slot = TimerSlot.BREW_2,
        heldMs = heldMs,
    )
}

internal fun fire(
    kind: TimerAlarmKind,
    source: String,
    wall: String,
    mono: Long,
    deltaMs: Long,
    slot: TimerSlot = TimerSlot.BREW,
) = FireTime(
    kind = kind,
    source = source,
    scheduledWall = "$wall.000",
    scheduledMonoMs = mono,
    actualWall = "$wall.${deltaMs.toString().padStart(3, '0').takeLast(3)}",
    actualMonoMs = mono + deltaMs,
    deltaMs = deltaMs,
    slot = slot,
)
