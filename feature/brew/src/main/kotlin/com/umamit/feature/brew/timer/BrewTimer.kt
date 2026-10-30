package com.umamit.feature.brew.timer

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.ControllerBrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * What the timer screen (SCR-05) and the active brew bar need from the
 * [BrewSessionController]: its state and its user events (04 §4, D-056). The
 * screens call nothing else: no engine, no database.
 */
interface BrewTimer {
    /** The controller's [BrewSessionController.state]. */
    val state: StateFlow<TimerUiState>

    /**
     * The controller's [BrewSessionController.recovered]: until it is true, an `Idle` [state] may
     * be a session not read yet after process death (GEN-07, D-068). Always true by default.
     */
    val recovered: StateFlow<Boolean> get() = ALWAYS_RECOVERED

    /** A user event of [slot]'s timer from the app ([BrewSessionController.send]): the timer the screen shows. */
    suspend fun send(slot: TimerSlot, event: TimerEvent): TransitionOutcome
}

private val ALWAYS_RECOVERED: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

/** The [BrewTimer] of the app: the controller itself, which also starts brews ([BrewStarter], M1-13). */
class ControllerBrewTimer @Inject constructor(private val controller: BrewSessionController) :
    BrewTimer,
    BrewStarter by ControllerBrewStarter(controller) {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override val recovered: StateFlow<Boolean> get() = controller.recovered

    override suspend fun send(slot: TimerSlot, event: TimerEvent): TransitionOutcome = controller.send(slot, event)
}
