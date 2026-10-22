package com.umamit.core.timer.engine

/**
 * The events of 04 §3.2. User events come from the timer screen, the active
 * brew bar and the notification; time-based events ([DeadlineReached]) from
 * the controller's coroutine and the exact alarms, and carry no payload: they
 * are accepted only when [BrewTimerEngine.dueTimes] says they are due. An
 * event the current state does not allow changes nothing and returns no
 * effects, which makes every event idempotent (04 §2.4).
 */
sealed interface TimerEvent {

    /**
     * *Brühen starten* (TMR-01), only in Idle (one active timer, GEN-03; else
     * rejected with [RejectionReason.ActiveTimerExists]). The controller has
     * picked the id [sessionId] and holds the recipe snapshot, but writes the
     * `brew_session` and planned `session_step` rows only for the
     * [TimerEffect.CreateSession] of an accepted start, so a refused start
     * leaves no orphaned rows.
     */
    data class StartSession(val sessionId: String, val steps: List<PlannedStep>, val preAlert: PreAlertSettings) :
        TimerEvent {
        init {
            require(steps.isNotEmpty()) { "a session needs at least one step" }
        }
    }

    /**
     * *Schnelltimer* (TMR-22, 04 §10), only in Idle (else rejected with
     * [RejectionReason.ActiveTimerExists]): one INFUSION step of
     * [durationSeconds] with the global pre-alert [globalPreAlertSeconds];
     * nothing is recorded.
     */
    data class StartQuickTimer(val durationSeconds: Int, val globalPreAlertSeconds: Int) : TimerEvent

    /** *Start*: the water is in; the countdown begins (Ready). */
    data object Start : TimerEvent

    /** *Pause* (Running, before the deadline; TMR-03). */
    data object Pause : TimerEvent

    /** *Weiter* (Paused; TMR-03). */
    data object Resume : TimerEvent

    /** The deadline of the running step is due (Running, at or after the deadline). */
    data object DeadlineReached : TimerEvent

    /**
     * The AlertPlayer finished its alert cycle (SET-14) for the alerting step.
     * Sets [TimerState.Alerting.alertPlayed]; not a user interaction.
     */
    data object AlertCompleted : TimerEvent

    /** *Abgegossen* / *Weggegossen* (Alerting; TMR-07). */
    data object Poured : TimerEvent

    /**
     * *Aufguss jetzt beenden* (Running, Paused; TMR-05): ends the step without
     * an alert. At or after the deadline (before [DeadlineReached] was
     * processed) the step counts as poured, with overtime.
     */
    data object EndStepNow : TimerEvent

    /** Skip the step waiting in Ready (TMR-09). */
    data object SkipStep : TimerEvent

    /** *Sitzung beenden* (any active state; TMR-12). See [BrewTimerEngine.finishWouldDiscard]. */
    data object FinishSession : TimerEvent

    /** Discard the session (any active state; TMR-13). */
    data object Discard : TimerEvent

    /** *Abbrechen* of the quick timer (Ready, Running, Paused; 04 §10): ends it, nothing recorded. */
    data object Cancel : TimerEvent

    /**
     * The quick timer waited 30 min in Ready or Paused without an interaction
     * (04 §10; due at [DueTimes.quickCancelAtWallMs]): ends it like [Cancel].
     */
    data object QuickTimerTimeout : TimerEvent

    /**
     * *Weiterer Aufguss* (any active state of a brew session; TMR-10): appends
     * [BrewTimerEngine.defaultAddedStep]; from Finished the session waits in
     * Ready for it.
     */
    data object AddStep : TimerEvent

    /**
     * *Fertig* in the Schritte-Sheet (any active state of a brew session;
     * TMR-11, SCR-31): the whole plan [steps] and the session's [preAlert]
     * choice. Only the pending steps may differ from the current plan; a
     * request that changes, removes or reorders a started or ended step is
     * rejected with [RejectionReason.StartedStepChanged], one without a
     * non-rinse step with [RejectionReason.NoBrewStepLeft].
     */
    data class EditSteps(val steps: List<PlannedStep>, val preAlert: PreAlertChoice) : TimerEvent
}
