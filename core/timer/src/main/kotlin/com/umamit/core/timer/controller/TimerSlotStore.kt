package com.umamit.core.timer.controller

import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerSnapshot

/**
 * The persisted timer of each slot (04 §7.1, §3.3; D-098): the engine's [TimerSnapshot] of one slot, read
 * and written in one transaction each. A transition writes only its own slot; the first turn of a process
 * reads both.
 */
internal interface TimerSlotStore {
    /** The persisted timer of [slot]; null when none is (Idle). */
    suspend fun read(slot: TimerSlot): TimerSnapshot?

    /** `Persist` of a transition in [slot]: writes [snapshot], or clears the slot for Idle (null). */
    suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?)

    /** Clears [slot], e.g. after `restore` refused its snapshot (D-048). */
    suspend fun clear(slot: TimerSlot)
}

/**
 * [TimerSlotStore] on the single `active_brew` row of umamit.db v1–v3 (05 §3.12), until umamit.db v4 keeps
 * one row per slot (v0.3.0-02). Only one timer is active across both slots, so the row holds the timer of
 * one slot, the slot of its kind ([slotOf]): [read] gives it to that slot only, and a write or a clear always
 * meets that slot's row or none.
 */
internal class SingleRowSlotStore(private val activeBrew: ActiveBrewStore) : TimerSlotStore {
    override suspend fun read(slot: TimerSlot): TimerSnapshot? = activeBrew.read()?.takeIf { slotOf(it) == slot }

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) = activeBrew.persist(snapshot)

    override suspend fun clear(slot: TimerSlot) = activeBrew.clear()
}

/** The slot of a timer of [kind] (04 §3.3): a brew session's or the quick timer's. */
internal fun slotOf(kind: SessionKind): TimerSlot = when (kind) {
    is SessionKind.Brew -> TimerSlot.BREW
    SessionKind.Quick -> TimerSlot.QUICK
}

/** The slot of a persisted timer: the quick timer's when it is one (`is_quick_timer`), else the brew's. */
internal fun slotOf(snapshot: TimerSnapshot): TimerSlot =
    if (snapshot.activeBrew.isQuickTimer) TimerSlot.QUICK else TimerSlot.BREW
