package com.umamit.core.data

import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * What the controller holds when the engine accepts a start ([TimerEffect.CreateSession]):
 * the snapshots of tea, recipe and vessel (05 §1.5), the planned steps, the effective
 * pre-alert (04 §5) and the start on the wall clock in the session's zone.
 */
data class SessionStart(
    val id: String,
    val snapshot: SessionSnapshot,
    val steps: List<PlannedStep>,
    val preAlertSeconds: Int,
    val startedAtMs: Long,
    val zoneId: String,
)

/**
 * The session lifecycle of 05 §3.7, one operation per data effect of the timer engine
 * (04 §4). Each operation is one transaction; the controller wraps a transition's data
 * effects and its `Persist` in one more ([DatabaseTransactions]). Temperatures are stored
 * in 5 °C steps (GEN-12).
 */
interface SessionRepository {
    /** [TimerEffect.CreateSession]: the `brew_session` row with its snapshots and one row per planned step. */
    suspend fun startSession(start: SessionStart)

    /**
     * [TimerEffect.ReplacePendingSteps] (`editSteps`, `addStep`): the rows from [fromIndex] on
     * are replaced by [steps]; [preAlertSeconds] is the session's effective pre-alert after the edit.
     */
    suspend fun replacePendingSteps(sessionId: String, fromIndex: Int, steps: List<PlannedStep>, preAlertSeconds: Int)

    /** [TimerEffect.RecordStep]: the ended step [stepIndex] gets the engine's recorded data (04 §5.1). */
    suspend fun recordStep(sessionId: String, stepIndex: Int, record: StepRecord)

    /**
     * [TimerEffect.CompleteSession] (TMR-12): deletes the never-started rows, keeps the planned
     * total, sets the cost (INV-08) and the end, deducts the leaf as a BREW stock event when
     * *Bestand abziehen* is on (SET-15) and sets the tea's opened date on the first session
     * after a new package (INV-06). A session that is no longer in progress is left unchanged.
     */
    suspend fun completeSession(sessionId: String, autoFinished: Boolean)

    /** [TimerEffect.DiscardSession] (TMR-13): deletes the session, its steps and its stock event. */
    suspend fun discardSession(sessionId: String)

    suspend fun getSession(id: String): BrewSession?

    /**
     * The [limit] most recent sessions that are no longer in progress, newest first, each with
     * its steps: *Zuletzt gebrüht* and *Nochmal brühen* on Start (SCR-02, BRW-11).
     */
    fun observeRecentSessions(limit: Int): Flow<List<BrewSession>>
}

/**
 * The single `active_brew` row with the session's step rows (05 §3.12, 04 §7.1): the timer
 * engine's [TimerSnapshot], written and read in one transaction each. A snapshot whose
 * temperatures are multiples of 5 °C reads back equal.
 */
interface ActiveBrewStore {
    /** The persisted active timer; null when none is active (Idle). */
    suspend fun read(): TimerSnapshot?

    /** [TimerEffect.Persist]: writes [snapshot], or clears the row for Idle (null). */
    suspend fun persist(snapshot: TimerSnapshot?)

    /** Clears the row, e.g. after `restore` refused an inconsistent snapshot (D-048). */
    suspend fun clear()
}

/** One database transaction around several repository calls (04 §2.3: data effects and `Persist` together). */
interface DatabaseTransactions {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}
