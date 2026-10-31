package com.umamit.feature.brew.timer

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [BrewTimer] whose states the test sets: each slot's ([state] of a slot) and the timer in front ([state]), as the
 * controller publishes them (04 §3.3, D-098). Events are recorded with their slot.
 */
internal class FakeBrewTimer(initial: TimerUiState = TimerUiState.Idle, recovered: Boolean = true) : BrewTimer {
    override val state = MutableStateFlow(initial)
    override val recovered = MutableStateFlow(recovered)
    private val slots = TimerSlot.entries.associateWith { MutableStateFlow<TimerUiState>(TimerUiState.Idle) }

    /** Every event the screens sent, with the slot it named. */
    val sent = mutableListOf<Pair<TimerSlot, TimerEvent>>()

    init {
        show(initial)
    }

    /** One timer, or none: [ui] is in front and in its slot, the other slot is idle. */
    fun show(ui: TimerUiState) {
        slots.forEach { (slot, flow) ->
            flow.value = if ((ui as? TimerUiState.Active)?.slot == slot) ui else TimerUiState.Idle
        }
        state.value = ui
    }

    /** Both slots: the brew's [brew], the quick timer's [quick], and [front] in front (the controller's choice). */
    fun show(brew: TimerUiState, quick: TimerUiState, front: TimerUiState) {
        slots.getValue(TimerSlot.BREW).value = brew
        slots.getValue(TimerSlot.QUICK).value = quick
        state.value = front
    }

    override fun state(slot: TimerSlot): StateFlow<TimerUiState> = slots.getValue(slot)

    override suspend fun send(slot: TimerSlot, event: TimerEvent): TransitionOutcome {
        sent += slot to event
        return TransitionOutcome.Accepted
    }
}
