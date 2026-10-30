package com.umamit.core.timer.engine

import com.umamit.core.model.TimerPhase

/**
 * The states of 04 §3.1. Every active state carries the [TimerSession] and
 * maps to one `TimerPhase` of the `active_brew` row (05 §3.12); all times are
 * plain values from the monotonic (`…ElapsedMs`) or wall (`…WallMs`) clock.
 */
sealed interface TimerState {

    /** No active session. */
    data object Idle : TimerState

    /** A session is active. */
    sealed interface Active : TimerState {
        val session: TimerSession
        val phase: TimerPhase
    }

    /** A state with a current step at [stepIndex] in [TimerSession.steps]. */
    sealed interface OnStep : Active {
        val stepIndex: Int
    }

    /** A state whose current step has started (status STARTED): its timing so far. */
    sealed interface StepStarted : OnStep {
        /** Wall time of *Start* (`step_started_wall_ms`). */
        val stepStartedWallMs: Long

        /** Paused time of this step before the current phase (`paused_total_ms`). */
        val pausedTotalMs: Long
    }

    /**
     * A state that waits for the user (Ready, Paused, Finished): idle demote
     * and the midnight timeout apply (TMR-14). [waitingSinceWallMs] is when it
     * started waiting.
     */
    sealed interface Waiting : Active {
        val waitingSinceWallMs: Long
    }

    /** Waiting for *Start*; never starts by itself (TMR-08). */
    data class Ready(override val session: TimerSession, override val stepIndex: Int, val readySinceWallMs: Long) :
        OnStep,
        Waiting {
        override val phase: TimerPhase get() = TimerPhase.READY
        override val waitingSinceWallMs: Long get() = readySinceWallMs
    }

    /**
     * A started step with a deadline that counts (Running, Alerting): the
     * overtime timeout and a late end are measured from it.
     */
    sealed interface WithDeadline : StepStarted {
        /** The step's deadline on the monotonic clock. */
        val deadlineElapsedMs: Long

        /** The same deadline on the wall clock, for „Fertig um 14:30" and reboot recovery. */
        val deadlineWallMs: Long
    }

    /**
     * Counting down to [deadlineElapsedMs] (04 §2: remaining = deadline − now).
     *
     * @property preAlertAtElapsedMs when the pre-alert tick is due; null when this step has none (TMR-23, #31).
     * @property preAlertPlayed the tick already played for this step: it plays at most once (E6).
     */
    data class Running(
        override val session: TimerSession,
        override val stepIndex: Int,
        override val stepStartedWallMs: Long,
        override val pausedTotalMs: Long,
        override val deadlineElapsedMs: Long,
        override val deadlineWallMs: Long,
        val preAlertAtElapsedMs: Long?,
        val preAlertPlayed: Boolean,
    ) : WithDeadline {
        override val phase: TimerPhase get() = TimerPhase.RUNNING
    }

    /** Frozen with [remainingMs] left since [pausedSinceElapsedMs]. */
    data class Paused(
        override val session: TimerSession,
        override val stepIndex: Int,
        override val stepStartedWallMs: Long,
        override val pausedTotalMs: Long,
        val remainingMs: Long,
        val pausedSinceElapsedMs: Long,
        val pausedSinceWallMs: Long,
        val preAlertPlayed: Boolean,
    ) : StepStarted,
        Waiting {
        override val phase: TimerPhase get() = TimerPhase.PAUSED
        override val waitingSinceWallMs: Long get() = pausedSinceWallMs
    }

    /**
     * The deadline passed; the alert plays until *Abgegossen*. Overtime = now − [deadlineElapsedMs].
     *
     * @property alertPlayed the alert ran its full cycle ([TimerEvent.AlertCompleted], `alert_played`).
     *   The alert is at-least-once: recovery replays `StartAlert` while this is false (04 §7.2).
     * @property ringingSinceElapsedMs when the alert last started ringing (`ringingSince`, 04 §3.3, §9.3; D-098);
     *   null while a quick timer's alert is held because the brew alerts ([held]). A brew alert is never held
     *   and rings since its deadline; so does a quick alert that rang at its deadline. A held quick alert rings
     *   from its release ([TimerEvent.AlertRingStart]), and its silent end counts from then.
     * @property ringingSinceWallMs the same on the wall clock, for reboot recovery; null with it.
     */
    data class Alerting(
        override val session: TimerSession,
        override val stepIndex: Int,
        override val stepStartedWallMs: Long,
        override val pausedTotalMs: Long,
        override val deadlineElapsedMs: Long,
        override val deadlineWallMs: Long,
        val alertPlayed: Boolean,
        val ringingSinceElapsedMs: Long?,
        val ringingSinceWallMs: Long?,
    ) : WithDeadline {
        override val phase: TimerPhase get() = TimerPhase.ALERTING

        /** A quick alert waiting for the brew's: no sound, no alert notification, no silent end (04 §9.3). */
        val held: Boolean get() = ringingSinceElapsedMs == null
    }

    /** No pending step left; waiting for *Sitzung beenden* or *Weiterer Aufguss*. */
    data class Finished(override val session: TimerSession, val readySinceWallMs: Long) : Waiting {
        override val phase: TimerPhase get() = TimerPhase.FINISHED
        override val waitingSinceWallMs: Long get() = readySinceWallMs
    }
}
