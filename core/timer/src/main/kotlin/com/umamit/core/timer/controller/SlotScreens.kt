package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the screens see of the slots (04 §3.3, D-098, D-108): each slot's UI state, and [state], the timer in
 * front: the slot whose alert rings, of two the one that rang last (its sound plays, the other's ended, D-109),
 * else the slot the user started or used last in the app ([focus]), else the first other active one, else Idle.
 * Written inside the controller's event lock.
 */
internal class SlotScreens {
    private val front = MutableStateFlow<TimerUiState>(TimerUiState.Idle)
    private val slots = TimerSlot.entries.associateWith { MutableStateFlow<TimerUiState>(TimerUiState.Idle) }
    private var focus = TimerSlot.BREW

    /** The timer in front of the screens. */
    val state: StateFlow<TimerUiState> = front.asStateFlow()

    /** The UI state of [slot]'s timer, Idle while the slot is idle. */
    fun of(slot: TimerSlot): StateFlow<TimerUiState> = slots.getValue(slot).asStateFlow()

    /** The active timers in the slot order (at most two, 04 §3.3). */
    val active: List<TimerUiState.Active> get() = TimerSlot.entries.mapNotNull(::get)

    /** [slot]'s timer as the screens see it now; null while the slot is idle. */
    operator fun get(slot: TimerSlot): TimerUiState.Active? = slots.getValue(slot).value as? TimerUiState.Active

    /** [slot]'s timer is now [ui]. */
    fun show(slot: TimerSlot, ui: TimerUiState) {
        slots.getValue(slot).value = ui
        publish()
    }

    /** The user started or used [slot]'s timer in the app: it is in front while no alert rings. */
    fun focus(slot: TimerSlot) {
        focus = slot
        publish()
    }

    private fun publish() {
        front.value = active.lastRinging()
            ?: get(focus)
            ?: TimerSlot.entries.firstNotNullOfOrNull(::get)
            ?: TimerUiState.Idle
    }
}
