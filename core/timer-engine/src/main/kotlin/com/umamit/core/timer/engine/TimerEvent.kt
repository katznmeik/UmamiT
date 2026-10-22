package com.umamit.core.timer.engine

/**
 * The events of 04 §3.2. User events come from the timer screen, the active
 * brew bar and the notification; [DeadlineReached] from the controller's
 * coroutine and the exact alarm. An event the current state does not allow is
 * ignored (no change, no effects), which makes every event idempotent (04 §2.4).
 */
sealed interface TimerEvent {

    /**
     * *Brühen starten* (TMR-01): the controller has created the IN_PROGRESS
     * session [sessionId] from a recipe snapshot with these [steps]. Only in Idle
     * (one active timer, GEN-03).
     */
    data class StartSession(
        val sessionId: String,
        val steps: List<PlannedStep>,
        val preAlert: PreAlertSettings = PreAlertSettings(),
    ) : TimerEvent {
        init {
            require(steps.isNotEmpty()) { "a session needs at least one step" }
        }
    }

    /** *Start*: the water is in; the countdown begins (Ready). */
    data object Start : TimerEvent

    /** *Pause* (Running, before the deadline; TMR-03). */
    data object Pause : TimerEvent

    /** *Weiter* (Paused; TMR-03). */
    data object Resume : TimerEvent

    /** The deadline of the running step is reached (Running, at or after the deadline). */
    data object DeadlineReached : TimerEvent

    /** *Abgegossen* / *Weggegossen* (Alerting; TMR-07). */
    data object Poured : TimerEvent

    /** *Aufguss jetzt beenden* (Running, Paused; TMR-05): ends the step early, without an alert. */
    data object EndStepNow : TimerEvent

    /** Skip the step waiting in Ready (TMR-09). */
    data object SkipStep : TimerEvent

    /** *Sitzung beenden* (any active state; TMR-12). */
    data object FinishSession : TimerEvent

    /** Discard the session (any active state; TMR-13). */
    data object Discard : TimerEvent
}
