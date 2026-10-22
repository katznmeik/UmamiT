package com.umamit.core.timer.engine

/**
 * What the controller does after a transition (04 §4). A transition lists its
 * effects in execution order. [RecordStep], [CompleteSession],
 * [DiscardSession] and [Persist] of one transition belong in one Room
 * transaction, which runs before every other effect (04 §2.3, §7.1).
 */
sealed interface TimerEffect {

    /** Write the new state to `active_brew`, or clear the row when the new state is Idle. */
    data object Persist : TimerEffect

    /** (Re)schedule the exact alarms of the running step; rescheduling replaces earlier ones (04 §6.2). */
    data class ScheduleAlarms(val deadlineElapsedMs: Long, val preAlertAtElapsedMs: Long?) : TimerEffect

    /** Cancel the step's alarms. */
    data object CancelAlarms : TimerEffect

    /** Play the pre-alert tick once. */
    data object PlayPreAlert : TimerEffect

    /** Start the step alert (04 §9). */
    data object StartAlert : TimerEffect

    /** Stop the step alert. */
    data object StopAlert : TimerEffect

    /** Start (or keep) the foreground service with the Live Update (04 §6.3). */
    data object StartForeground : TimerEffect

    /** Stop the foreground service and remove its notification. */
    data object StopForeground : TimerEffect

    /** Refresh the Live Update / notification from the new state. */
    data object UpdateNotification : TimerEffect

    /** Store the ended step [stepIndex] of session [sessionId] (`session_step`, 04 §5.1). */
    data class RecordStep(val sessionId: String, val stepIndex: Int, val record: StepRecord) : TimerEffect

    /** Mark the session COMPLETED (05 §3.7: delete unstarted rows, stock, cost, opened date). */
    data class CompleteSession(val sessionId: String) : TimerEffect

    /** Delete the session and its stock deduction (TMR-13). */
    data class DiscardSession(val sessionId: String) : TimerEffect

    /** Show the „Gespeichert im Journal" sheet (SCR-06) for the session. */
    data class ShowSavedSheet(val sessionId: String) : TimerEffect
}
