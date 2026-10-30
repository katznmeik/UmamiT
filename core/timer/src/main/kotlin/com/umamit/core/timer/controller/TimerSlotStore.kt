package com.umamit.core.timer.controller

import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.model.TimerSlot
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
 * [TimerSlotStore] on the single-timer methods of [ActiveBrewStore], which umamit.db v4 keeps for a
 * controller that runs one timer at a time (D-098, #126): the table holds the one active timer, in the slot
 * of its kind, and a write clears the other slot, as v3 replaced its single row. Only one timer is active
 * across both slots until v0.3.0-03, so [read] gives the row to the slot of its kind only, and a write or a
 * clear always meets that slot's row or none. v0.3.0-03 moves this to the store's slot methods.
 */
internal class SingleTimerSlotStore(private val activeBrew: ActiveBrewStore) : TimerSlotStore {
    override suspend fun read(slot: TimerSlot): TimerSnapshot? =
        activeBrew.read()?.takeIf { it.activeBrew.slot == slot }

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) = activeBrew.persist(snapshot)

    override suspend fun clear(slot: TimerSlot) = activeBrew.clear()
}
