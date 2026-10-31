package com.umamit.core.model

/**
 * One `active_brew` row (05 §3.12): the active timer state as plain
 * values, written on every timer transition (04 §7.1) and read on recovery
 * (04 §7.2). Together with the session's [SessionStepRow]s it holds the timer
 * engine's state losslessly. `…ElapsedMs` values are on the monotonic clock of
 * boot [bootCount]; `…WallMs` values on the wall clock. From umamit.db v4 the
 * table holds one row per [TimerSlot], the [slot] of the row's kind (D-098).
 *
 * @property sessionId the `brew_session` row; null for the quick timer.
 * @property quickDurationSeconds the quick timer's one step; null for a brew session.
 * @property stepIndex the current step; the number of steps in FINISHED.
 * @property deadlineElapsedMs RUNNING/ALERTING.
 * @property deadlineWallMs RUNNING/ALERTING; „Fertig um 14:30" and reboot recovery.
 * @property preAlertElapsedMs RUNNING: when the pre-alert tick is due; null without one.
 * @property preAlertPlayed RUNNING/PAUSED: the tick of the current step already played.
 * @property remainingMs PAUSED.
 * @property pausedTotalMs paused time of the current step before the current phase.
 * @property pausedSinceElapsedMs PAUSED.
 * @property pausedSinceWallMs PAUSED; reboot recovery.
 * @property readySinceWallMs READY/FINISHED.
 * @property stepStartedWallMs RUNNING/PAUSED/ALERTING: wall time of *Start*.
 * @property alertPlayed ALERTING: the alert ran its full cycle; recovery replays it while false.
 * @property bootCount the boot of the elapsed values (`Settings.Global.BOOT_COUNT`).
 * @property lastInteractionWallMs last user event, for idle demote and the midnight timeout (TMR-14).
 * @property demoted the foreground service was stopped and a regular notification shown (`idleDemote`).
 * @property preAlertInheritedSeconds the recipe ?: global pre-alert, frozen at the start (0: off): the base B of
 *   the automatic rule; each step's own choice is in its [SessionStepRow] (umamit.db v2, D-093).
 * @property zoneId the session's time zone (IANA id), also for the quick timer, which has no session row.
 * @property quickPreAlertSeconds the quick timer's own pre-alert choice (umamit.db v3, TMR-22, D-097): null
 *   *Automatisch* (B = [preAlertInheritedSeconds]), 0 *Aus*, else 5–30 s; always null for a brew session, whose
 *   pre-alerts are per step. The engine's restore refuses a value on a brew session or outside 0 / 5–30 s (#116).
 * @property alertRingingSinceElapsedMs ALERTING (umamit.db v4, D-098): when the alert last started ringing
 *   (`ringingSince`, 04 §9.3); null = held (a quick alert waiting for the brew's) or not alerting.
 * @property alertRingingSinceWallMs the same on the wall clock; it rebases [alertRingingSinceElapsedMs] after a reboot.
 *   A quick alert that rang at its deadline rings since its deadline; a released one since its release (04 §3.3).
 */
data class ActiveBrewSnapshot(
    val sessionId: String?,
    val isQuickTimer: Boolean,
    val quickDurationSeconds: Int?,
    val stepIndex: Int,
    val phase: TimerPhase,
    val deadlineElapsedMs: Long?,
    val deadlineWallMs: Long?,
    val preAlertElapsedMs: Long?,
    val preAlertPlayed: Boolean,
    val remainingMs: Long?,
    val pausedTotalMs: Long,
    val pausedSinceElapsedMs: Long?,
    val pausedSinceWallMs: Long?,
    val readySinceWallMs: Long?,
    val stepStartedWallMs: Long?,
    val alertPlayed: Boolean,
    val bootCount: Int,
    val lastInteractionWallMs: Long,
    val demoted: Boolean,
    val preAlertInheritedSeconds: Int,
    val zoneId: String,
    val quickPreAlertSeconds: Int? = null,
    val alertRingingSinceElapsedMs: Long? = null,
    val alertRingingSinceWallMs: Long? = null,
) {
    /** The slot of this row's kind: [TimerSlot.QUICK] for the quick timer, else [TimerSlot.BREW] (05 §3.12). */
    val slot: TimerSlot get() = if (isQuickTimer) TimerSlot.QUICK else TimerSlot.BREW
}

/**
 * The timer's view of one `session_step` row (05 §3.7) at [position]. A
 * planned step has only its plan values; an ended step also has its recorded
 * values (04 §5.1), `actualSeconds` non-null. A skipped step records zeros
 * and no times. [preAlertSeconds] is the step's own pre-alert choice (from
 * umamit.db v2; TMR-23, D-093): null *Automatisch*, 0 *Aus*, else the lead in
 * seconds.
 */
data class SessionStepRow(
    val position: Int,
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val actualSeconds: Int?,
    val pausedSeconds: Int?,
    val overtimeSeconds: Int?,
    val startedAtWallMs: Long?,
    val endedAtWallMs: Long?,
    val skipped: Boolean,
    val overtimeAutoAck: Boolean,
    val preAlertSeconds: Int? = null,
)
