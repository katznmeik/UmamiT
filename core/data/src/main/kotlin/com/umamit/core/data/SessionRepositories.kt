package com.umamit.core.data

import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * What the controller holds when the engine accepts a start ([TimerEffect.CreateSession]):
 * the snapshots of tea, recipe and vessel (05 §1.5), the planned steps, each with its own
 * pre-alert, the session's pre-alert base B ([preAlertSeconds], recipe ?: global, frozen at the
 * start; 04 §5, D-093) and the start on the wall clock in the session's zone.
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
    /**
     * [TimerEffect.CreateSession]: the `brew_session` row with its snapshots and one row per planned step, each
     * with its pre-alert.
     */
    suspend fun startSession(start: SessionStart)

    /**
     * [TimerEffect.ReplacePendingSteps] (`editSteps`, `addStep`): the rows from [fromIndex] on
     * are replaced by [steps], each with its pre-alert. The session's B stays as frozen at the start
     * (05 §3.7, D-093); the running step's new pre-alert reaches its row with the `Persist`.
     */
    suspend fun replacePendingSteps(sessionId: String, fromIndex: Int, steps: List<PlannedStep>)

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
     * The ids of the sessions still `IN_PROGRESS`: the active session's, and any whose
     * `active_brew` row is gone, e.g. after a device restore (E11, 04 §7.2).
     */
    suspend fun inProgressSessionIds(): List<String>

    /**
     * The [limit] most recent sessions that are no longer in progress, newest first, each with
     * its steps: *Zuletzt gebrüht* and *Nochmal brühen* on Start (SCR-02, BRW-11).
     */
    fun observeRecentSessions(limit: Int): Flow<List<BrewSession>>
}

/**
 * The `active_brew` rows with the brew session's step rows (05 §3.12, 04 §7.1): per [TimerSlot], the
 * timer engine's [TimerSnapshot], written and read in one transaction each. A snapshot whose
 * temperatures are multiples of 5 °C reads back equal. From umamit.db v4 (D-098) the table holds one
 * row per slot: the brew slot a brew session's, the quick slot the quick timer's.
 *
 * Each method acts on its slot only, so a transition of one timer never touches the other's row.
 */
interface ActiveBrewStore {
    /**
     * The persisted timer of [slot]; null when the slot is idle. Only a damaged row can be of the other
     * kind (05 §3.12): the caller refuses it like a snapshot `restore` refuses ([ActiveBrewSnapshot.slot]).
     */
    suspend fun read(slot: TimerSlot): TimerSnapshot?

    /**
     * [TimerEffect.Persist] of [slot]: writes [snapshot] as the slot's row and, for the brew slot, its
     * session's step rows, or clears the slot for Idle (null). The other slot's row is untouched. A
     * snapshot of the other kind is refused ([IllegalArgumentException]) and nothing is written.
     */
    suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?)

    /**
     * Clears [slot]'s row only, e.g. after `restore` refused its snapshot (D-048); its session and the
     * other slot stay.
     */
    suspend fun clear(slot: TimerSlot)
}

/** One database transaction around several repository calls (04 §2.3: data effects and `Persist` together). */
interface DatabaseTransactions {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}
