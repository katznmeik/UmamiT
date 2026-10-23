package com.umamit.core.timer.engine

import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopForeground

/*
 * The foreground-service contract (04 §4, §6.1, D-053). `StartForeground`
 * means "ensure the service runs" and `StopForeground` stops it and removes
 * notifications 1 and 2. Every active state runs with the service except a
 * demoted session and a long running step. Transitions that decide the
 * service themselves: start and resume (short: ensure, long: stop), the
 * deadline (ensure before the alert), idle demote and every way to Idle
 * (stop), recovery and the overtime timeout (ensure where needed).
 */

/** True when this state runs with the service: every active state except a demoted one and a long running step. */
internal val TimerState.Active.needsService: Boolean
    get() = !session.demoted && !(this is TimerState.Running && session.isLongStep(stepIndex))

/**
 * A step that starts counting down: a short step runs with the service
 * (ensured), a long step without it (TMR-18); its deadline restarts it for
 * the alert.
 */
internal fun serviceFor(session: TimerSession, stepIndex: Int): TimerEffect =
    if (session.isLongStep(stepIndex)) StopForeground else StartForeground

/**
 * A transition from a state without the service (demoted, a long running
 * step) to one that needs it ensures the service, unless it already decides
 * the service itself: a user event on a demoted session, a pause or early end
 * of a long step.
 */
internal fun TimerTransition.withServiceBack(before: TimerState.Active): TimerTransition {
    val after = state as? TimerState.Active
    val decided = effects.any { it == StartForeground || it == StopForeground }
    val comesBack = after != null && after.needsService && !before.needsService
    if (!comesBack || decided) return this
    val sideFrom = effects.indexOfFirst { it is TimerEffect.ScheduleAlarms } + 1
    return copy(effects = effects.take(sideFrom) + StartForeground + effects.drop(sideFrom))
}
