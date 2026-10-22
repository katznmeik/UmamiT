package com.umamit.feature.brew.timer

import com.umamit.core.timer.controller.BrewSessionController
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

/** The [BrewTimer] of the app: the controller itself. */
class ControllerBrewTimer @Inject constructor(private val controller: BrewSessionController) : BrewTimer {
    override val state: StateFlow<TimerUiState> get() = controller.state

    override suspend fun send(event: TimerEvent): TransitionOutcome = controller.send(event)
}

/**
 * The live reliability checks of the timer (TMR-31): notifications, Live
 * Updates, full-screen alerts, exact alarms and the battery exemption. The app
 * implements it with the probe's status checks (#14); the banner's full wiring
 * on Start and in the settings comes with M1-15.
 */
interface TimerReliability {
    /** Whether a check fails now; read again whenever the screen resumes. */
    fun hasProblem(): Boolean

    /** *Beheben*: opens the system page of the first failing check. */
    fun fix()
}
