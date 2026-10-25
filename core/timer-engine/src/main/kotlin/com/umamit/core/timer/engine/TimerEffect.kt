package com.umamit.core.timer.engine

/**
 * What the controller does after an accepted transition (04 §4), in list
 * order. Every accepted transition lists its data effects first
 * ([CreateSession], [ReplacePendingSteps], [RecordStep], [CompleteSession],
 * [DiscardSession]), then
 * exactly one [Persist] (all of these in one Room transaction, 04 §2.3, §7.1),
 * then exactly one [ScheduleAlarms], then the side effects.
 */
sealed interface TimerEffect {

    /** Create the `brew_session` row and its planned `session_step` rows (05 §3.7) for an accepted start. */
    data class CreateSession(val sessionId: String) : TimerEffect

    /**
     * Replace the unstarted `session_step` rows of session [sessionId] (05 §3.7):
     * delete the rows from position [fromIndex] on and insert [steps] there.
     * Started and ended steps always form a prefix, so the rows before
     * [fromIndex] are never touched (`addStep`, `editSteps`).
     */
    data class ReplacePendingSteps(val sessionId: String, val fromIndex: Int, val steps: List<PlannedStep>) :
        TimerEffect

    /** Store the ended step [stepIndex] of session [sessionId] (`session_step`, 04 §5.1). */
    data class RecordStep(val sessionId: String, val stepIndex: Int, val record: StepRecord) : TimerEffect

    /**
     * Mark the session COMPLETED (05 §3.7: delete unstarted rows, stock, cost, opened date).
     * [autoFinished] is true when the midnight timeout completed it rather than the user
     * (`brew_session.auto_finished`, TMR-14, D-054).
     */
    data class CompleteSession(val sessionId: String, val autoFinished: Boolean) : TimerEffect

    /** Delete the session and its stock deduction (TMR-13). */
    data class DiscardSession(val sessionId: String) : TimerEffect

    /** Write the new state to `active_brew`, or clear the row when the new state is Idle. */
    data object Persist : TimerEffect

    /**
     * The complete set of [due] times of the new state, from
     * [BrewTimerEngine.dueTimes]. The controller reconciles each alarm type
     * (fixed request code): schedule it when its time is present, cancel it
     * when absent (04 §6.2). [DueTimes.NONE] cancels all.
     */
    data class ScheduleAlarms(val due: DueTimes) : TimerEffect

    /** Play the pre-alert tick (TMR-23); at most once per step, at least 1 s before the deadline. */
    data object PlayPreAlert : TimerEffect

    /**
     * Ensure the alert of the alerting step is playing (04 §9), idempotent: if
     * it already plays for that step, nothing restarts (a deadline event and a
     * `recover` may both ask). The AlertPlayer answers with [TimerEvent.AlertCompleted].
     */
    data object StartAlert : TimerEffect

    /** Stop the step alert. */
    data object StopAlert : TimerEffect

    /**
     * Ensure the foreground service runs (idempotent; 04 §6.3), with the
     * notification of the new state as its foreground notification: the alert
     * (ID 2) while Alerting, else notification 1 (D-044, D-054). A start from
     * the background may be refused; the controller then keeps the regular
     * ongoing notification until the next interaction or alarm.
     */
    data object StartForeground : TimerEffect

    /**
     * Stop the foreground service if it runs and remove notifications 1 and 2
     * (Live Update or regular ongoing notification, alert), also when they
     * were posted without the service (04 §8, D-053).
     */
    data object StopForeground : TimerEffect

    /**
     * Post the notification of the new state (04 §4, §8.2; D-044, D-054).
     * Never emitted for Idle.
     * - **Alerting:** refresh the alert (ID 2, `steep_finished`), with the
     *   full-screen intent only when [StartAlert] is in the same transition,
     *   and cancel notification 1. Notification 1 is never posted while
     *   alerting: Android 16 would group the two and silence the alert.
     * - **Every other active state:** notification 1, the Live Update while
     *   the service runs; a regular, silent ongoing notification while the
     *   session is demoted or a long step runs.
     */
    data object UpdateNotification : TimerEffect

    /** Show the „Gespeichert im Journal" sheet (SCR-06) for the session. */
    data class ShowSavedSheet(val sessionId: String) : TimerEffect

    /**
     * Post the notification „Sitzung gespeichert" (ID 3, channel `sessions`,
     * 04 §8) for a session the midnight timeout completed (TMR-14).
     */
    data class NotifySessionSaved(val sessionId: String) : TimerEffect
}
