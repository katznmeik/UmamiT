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
     * leaves no orphaned rows. [longStepsOnly] is true for a cold or ice brew
     * (`brew_session.method` COLD_BREW or ICE_BREW): every step is then a long
     * step, however short (TMR-18, [BrewTimerEngine.longStepsOnly]).
     */
    data class StartSession(
        val sessionId: String,
        val steps: List<PlannedStep>,
        val preAlert: PreAlertSettings,
        val longStepsOnly: Boolean,
    ) : TimerEvent {
        init {
            require(steps.isNotEmpty()) { "a session needs at least one step" }
        }
    }

    /**
     * *Schnelltimer* (TMR-22, 04 §10), only in Idle (else rejected with
     * [RejectionReason.ActiveTimerExists]): one INFUSION step of
     * [durationSeconds] with the global pre-alert [globalPreAlertSeconds];
     * nothing is recorded. A duration outside 5 s – 24 h or a pre-alert other
     * than 0 or 5–30 s is rejected with [RejectionReason.InvalidQuickTimer].
     */
    data class StartQuickTimer(val durationSeconds: Int, val globalPreAlertSeconds: Int) : TimerEvent

    /**
     * The process started with a persisted active state (any active state;
     * 04 §7.2, §7.3, §7.5): after a reboot the elapsed values are first
     * rebased from the wall values within physical bounds, then the recovery
     * table applies. The controller sends it exactly once per process, as the
     * first event, inside the lock of the restore. Not a user interaction.
     * Until it is processed, every other event whose clock reading comes from
     * another boot is rejected with [RejectionReason.BootChanged].
     */
    data object Recover : TimerEvent

    /** *Start*: the water is in; the countdown begins (Ready). */
    data object Start : TimerEvent

    /** *Pause* (Running, before the deadline; TMR-03). */
    data object Pause : TimerEvent

    /** *Weiter* (Paused; TMR-03). */
    data object Resume : TimerEvent

    /**
     * The pre-alert of the running step is due (Running; [DueTimes.preAlertAtElapsedMs]).
     * Plays the tick ([TimerEffect.PlayPreAlert]) while at least 1 s is left
     * before the deadline; later it only marks the pre-alert as played, so a
     * tick never sounds at or after the deadline (TMR-23, D-048, D-053). Not a
     * user interaction.
     */
    data object PreAlertReached : TimerEvent

    /**
     * The deadline of the running step is due (Running, at or after the
     * deadline). At or after deadline + 10 min it applies the overtime timeout
     * instead, without an alert, as recovery does (D-053).
     */
    data object DeadlineReached : TimerEvent

    /**
     * The alert of the alerting step was not acknowledged for 10 min (Alerting;
     * [DueTimes.overtimeTimeoutAtElapsedMs]): the step counts as poured on time,
     * with the auto-ack flag; the quick timer ends (04 §3.2, §5.1). Not a user
     * interaction, and silent.
     */
    data object OvertimeTimeout : TimerEvent

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
     * A brew session waited 30 min without an interaction in Ready, Paused or
     * Finished ([DueTimes.idleDemoteAtWallMs]; TMR-14): the state stays, the
     * session is marked `demoted`, the foreground service stops and the Live
     * Update becomes a regular ongoing notification. Not a user interaction;
     * the next user event brings the service back (D-053).
     */
    data object IdleDemote : TimerEvent

    /**
     * The first local midnight at least 6 h after a waiting brew session became
     * idle has come ([DueTimes.midnightAtWallMs]; TMR-14): the session is
     * completed with [TimerEffect.NotifySessionSaved] if a non-rinse step was
     * completed (a paused step counts, ended where its pause began), and
     * discarded silently otherwise. Not a user interaction; no saved sheet.
     */
    data object MidnightTimeout : TimerEvent

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
