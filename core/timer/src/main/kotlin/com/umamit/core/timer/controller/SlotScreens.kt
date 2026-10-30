package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the screens see of the two slots (04 §3.3, D-098): each slot's UI state, and [state], the timer in
 * front: the slot whose alert rings (the brew's first), else the slot the user started or used last in the
 * app ([focus]), else the other active one, else Idle. Written inside the controller's event lock.
 */
internal class SlotScreens {
    private val front = MutableStateFlow<TimerUiState>(TimerUiState.Idle)
    private val slots = TimerSlot.entries.associateWith { MutableStateFlow<TimerUiState>(TimerUiState.Idle) }
    private var focus = TimerSlot.BREW

    /** The timer in front of the screens. */
    val state: StateFlow<TimerUiState> = front.asStateFlow()

    /** The UI state of [slot]'s timer, Idle while the slot is idle. */
    fun of(slot: TimerSlot): StateFlow<TimerUiState> = slots.getValue(slot).asStateFlow()

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
        front.value = TimerSlot.entries.firstNotNullOfOrNull { slot -> get(slot)?.takeIf { it.ringing } }
            ?: get(focus)
            ?: get(focus.other)
            ?: TimerUiState.Idle
    }
}

/** The other slot. */
internal val TimerSlot.other: TimerSlot get() = if (this == TimerSlot.BREW) TimerSlot.QUICK else TimerSlot.BREW
