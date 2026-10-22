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
     * Counting down to [deadlineElapsedMs] (04 §2: remaining = deadline − now).
     *
     * @property deadlineWallMs the same deadline on the wall clock, for „Fertig um 14:30" and reboot recovery.
     * @property preAlertAtElapsedMs when the pre-alert tick is due; null when this step has none (TMR-23, #31).
     * @property preAlertPlayed the tick already played for this step: it plays at most once (E6).
     */
    data class Running(
        override val session: TimerSession,
        override val stepIndex: Int,
        override val stepStartedWallMs: Long,
        override val pausedTotalMs: Long,
        val deadlineElapsedMs: Long,
        val deadlineWallMs: Long,
        val preAlertAtElapsedMs: Long?,
        val preAlertPlayed: Boolean,
    ) : StepStarted {
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
     */
    data class Alerting(
        override val session: TimerSession,
        override val stepIndex: Int,
        override val stepStartedWallMs: Long,
        override val pausedTotalMs: Long,
        val deadlineElapsedMs: Long,
        val deadlineWallMs: Long,
        val alertPlayed: Boolean,
    ) : StepStarted {
        override val phase: TimerPhase get() = TimerPhase.ALERTING
    }

    /** No pending step left; waiting for *Sitzung beenden* or *Weiterer Aufguss*. */
    data class Finished(override val session: TimerSession, val readySinceWallMs: Long) : Waiting {
        override val phase: TimerPhase get() = TimerPhase.FINISHED
        override val waitingSinceWallMs: Long get() = readySinceWallMs
    }
}
