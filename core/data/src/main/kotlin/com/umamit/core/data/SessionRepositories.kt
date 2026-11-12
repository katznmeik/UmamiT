package com.umamit.core.data

import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.BrewSession
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.TasteAxis
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
     * [TimerEffect.CompleteSession] (TMR-12): keeps the never-started rows (no `actual_seconds`;
     * the journal shows them „nicht gebrüht", #152, Q-19) and the planned total, sets the cost (INV-08) and the
     * end, deducts the leaf as a BREW stock event when *Bestand abziehen* is on (SET-15) and sets the tea's opened
     * date on the first session after a new package (INV-06). A session that is no longer in progress is left
     * unchanged.
     */
    suspend fun completeSession(sessionId: String, autoFinished: Boolean)

    /**
     * What the completion of [sessionId] did to its tea's opened date in this process (SCR-06, INV-06): the
     * change when it set the date, for the „Gespeichert" sheet's *Rückgängig*. Null when it set nothing, when
     * the session isn't completed, or when the date has changed since (an undo, the tea editor).
     */
    suspend fun openedDateChange(sessionId: String): OpenedDateChange?

    /**
     * *Rückgängig* on the „Gespeichert" sheet (SCR-06, INV-06): the tea's opened date goes back to the value
     * before [change], none or a date. A date changed since is left alone. Offered once.
     */
    suspend fun undoOpenedDate(change: OpenedDateChange)

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

/** What a tasting write did (05 §3.7 „Tasting writes"). Anything but [SAVED] wrote nothing. */
enum class TastingWrite {
    /** Written, with the session's `updated_at`; an unchanged value is written again. */
    SAVED,

    /** No such session, or it is not `COMPLETED` (one in progress, D-112): nothing changes. */
    NOT_COMPLETED,

    /** A value out of its bounds (a rating off the half steps, a taste value beyond 0–5, notes over 5000). */
    INVALID,

    /** A 21st descriptor (JRN-04): „Höchstens 20 Beschreibungen". */
    DESCRIPTOR_LIMIT,
}

/**
 * The tasting inputs of the session page and the „Gespeichert" sheet (05 §3.7, JRN-02–JRN-05, D-112): one small
 * write per input, each its own transaction on a `COMPLETED` session only. Every input is optional: each can be
 * cleared again. None touches steps, stock, cost or `active_brew`, and none runs inside a timer transition.
 */
interface SessionTasting {
    /** The rating, 0,5–5,0 in half steps (JRN-02), or null to remove it. */
    suspend fun setRating(sessionId: String, rating: Double?): TastingWrite

    /** One taste value, 0–5 (JRN-03), or null to unset that axis; the other five stay. */
    suspend fun setTaste(sessionId: String, axis: TasteAxis, value: Int?): TastingWrite

    /** *Zurücksetzen* (JRN-03): all six taste values unset. */
    suspend fun clearTaste(sessionId: String): TastingWrite

    /** The notes (JRN-05): trimmed, blank stored as null, at most 5000 characters after trimming. */
    suspend fun setNotes(sessionId: String, notes: String?): TastingWrite

    /**
     * Ticks or unticks descriptor [key] (JRN-04, 05 §3.8). Ticking a ticked key and unticking an absent one write
     * nothing new; a 21st key is refused ([TastingWrite.DESCRIPTOR_LIMIT]), checked in the write's transaction.
     */
    suspend fun setDescriptor(sessionId: String, key: String, ticked: Boolean): TastingWrite
}

/** What [RecipeOffers.accept] did. Anything but [ACCEPTED] wrote nothing. */
enum class RecipeOfferAccept {
    /** The proposal replaced the recipe's steps and the session is marked (`times_applied`). */
    ACCEPTED,

    /** The offer does not apply at that moment (accepted already, the recipe changed or gone, no difference). */
    NOT_APPLICABLE,
}

/**
 * *Zeiten ins Rezept übernehmen* (PRF-07, 05 §3.7 „Accept the recipe offer", D-112) on the „Gespeichert" sheet and the
 * session page. The offer is derived when read (`RecipeOffer` in core:domain); `times_offered` is never written.
 * Session completion and the timer are not involved.
 */
interface RecipeOffers {
    /** The steps the recipe of [sessionId] would get when the offer applies now; null when it does not. */
    suspend fun proposal(sessionId: String): List<RecipeStep>?

    /**
     * In one transaction: the recipe's steps replaced by the proposal, as *Im Rezept speichern* does (with each
     * step's pre-alert), and `times_applied` set. Refused, changing nothing, when the offer no longer applies.
     */
    suspend fun accept(sessionId: String): RecipeOfferAccept
}

/**
 * One brewed step's new recorded values in the session editor (SCR-17, JRN-11): its *tatsächlich* time
 * ([actualSeconds], [SessionEditLimits.STEP_SECONDS]) and its temperature ([tempC], snapped to 5 °C, GEN-12; null
 * keeps the recorded one, so a time-only edit writes no temperature). The step at [position] must have been brewed
 * (an actual time, not skipped).
 */
data class StepEdit(val position: Int, val actualSeconds: Int, val tempC: Int? = null)

/**
 * What the session editor changes (05 §3.7 „Edit", JRN-11, D-112): each field null (or a step not listed) stays as
 * recorded; a value equal to the stored one changes nothing either.
 *
 * @property startedAtMs the new start; the session, its end, its steps and its BREW event move by the same amount.
 * @property vessel the new vessel, with its name as the snapshot; [VesselChange.vesselId] null is *Kein Gefäß*.
 * @property leafGrams the new leaf in g, 0–100 (0 deletes the BREW event); sets the deduction and the cost.
 * @property waterMl the new water in ml ([SessionEditLimits]).
 */
data class SessionEdit(
    val startedAtMs: Long? = null,
    val vessel: VesselChange? = null,
    val leafGrams: Double? = null,
    val waterMl: Int? = null,
    val steps: List<StepEdit> = emptyList(),
)

/** A new vessel for [SessionEdit.vessel]: [vesselId] null for *Kein Gefäß*. */
data class VesselChange(val vesselId: String?)

/** The bounds [SessionEditor.editSession] accepts (05 §3.7 „Edit"; the Schritte-Sheet's, PRF-04, 05 §3.2). */
object SessionEditLimits {
    /** A recorded step time: at least 1 s, at most the longest step the Schritte-Sheet allows (24 h). */
    val STEP_SECONDS = 1..86_400

    /** A step temperature, in 5 °C steps (the Schritte-Sheet's range). */
    val TEMP_C = 5..100

    /** Water in ml (the setup's bounds, 05 §3.2). */
    val WATER_ML = 10..3000

    /** Leaf in g; the editor itself offers 0,1–100 g (the setup's), the data layer also takes 0. */
    const val MAX_LEAF_GRAMS = 100.0
}

/** What [SessionEditor.editSession] did. Anything but [SAVED] wrote nothing. */
enum class SessionEditResult {
    SAVED,

    /** No such session, or it is not `COMPLETED`: nothing changes (JRN-11 AC2). */
    NOT_COMPLETED,

    /** The new start would put the session's end after now: „Die Sitzung kann nicht in der Zukunft liegen.". */
    IN_FUTURE,

    /** A value out of its bounds, a step that was not brewed, or a vessel that is gone. */
    INVALID,
}

/**
 * What the editor opens with (SCR-17): the session and whether it has a BREW stock event, so the editor knows
 * whether a leaf change adjusts the stock („Dein Vorrat wird um 2 g angepasst.").
 */
data class EditableSession(val session: BrewSession, val hasStockDeduction: Boolean)

/**
 * `EditSession` (05 §3.7 „Edit", JRN-11, D-112): the session editor's one write, a transaction on a `COMPLETED`
 * session. A session in progress and the `active_brew` rows are never touched (rule 8, JRN-11 AC2); the tea, the
 * recipe link, the method and the steps' number and plan stay; the tea's opened date is not recalculated.
 */
interface SessionEditor {
    /** The completed session [sessionId] for the editor; null when it is gone or not `COMPLETED`. */
    suspend fun editable(sessionId: String): EditableSession?

    /**
     * Applies [edit] in one transaction: the start moved by Δ (refused into the future), the vessel with its name
     * snapshot, the leaf (the BREW event's deduction, deleted at 0, none created; the cost by the finish rule, scaled
     * for a tea that is gone), the water, and each listed step's recorded time (overtime and end follow, the plan
     * stays) and temperature. Sets the session's `updated_at`.
     */
    suspend fun editSession(sessionId: String, edit: SessionEdit): SessionEditResult
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
