package com.umamit.feature.brew.timer

import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.ControllerBrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * What the timer screen (SCR-05) and the active brew bar need from the
 * [BrewSessionController]: its state and its user events (04 §4, D-056). The
 * screens call nothing else: no engine, no database.
 */
interface BrewTimer {
    /** The controller's [BrewSessionController.state]. */
    val state: StateFlow<TimerUiState>

    /** A user event from the app ([BrewSessionController.send]). */
    suspend fun send(event: TimerEvent): TransitionOutcome
}

/** The [BrewTimer] of the app: the controller itself, which also starts brews ([BrewStarter], M1-13). */
class ControllerBrewTimer @Inject constructor(private val controller: BrewSessionController) :
    BrewTimer,
    BrewStarter by ControllerBrewStarter(controller) {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override suspend fun send(event: TimerEvent): TransitionOutcome = controller.send(event)
}
