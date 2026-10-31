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
 * [TimerSlotStore] on the slot methods of [ActiveBrewStore] (umamit.db v4, D-098): each slot's row is read,
 * written and cleared on its own, so a transition of one timer never touches the other's row. The read row is
 * returned as stored; the controller refuses a row whose slot and kind disagree ([PersistedTimer]).
 */
internal class ActiveBrewSlotStore(private val activeBrew: ActiveBrewStore) : TimerSlotStore {
    override suspend fun read(slot: TimerSlot): TimerSnapshot? = activeBrew.read(slot)

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) = activeBrew.persist(slot, snapshot)

    override suspend fun clear(slot: TimerSlot) = activeBrew.clear(slot)
}
