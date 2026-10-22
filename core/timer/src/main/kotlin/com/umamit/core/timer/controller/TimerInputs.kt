package com.umamit.core.timer.controller

import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome

/**
 * The [BrewSessionController]'s inputs that components the system creates
 * reach through the app ([TimerHost]): the alarm receiver (04 §6.2) and the
 * full-screen alert (04 §8.2).
 */
interface TimerInputs {
    /** The alarm of [kind] arrived; runs every time-based event due now (suspends for one controller turn). */
    suspend fun onAlarm(kind: TimerAlarmKind)

    /** A user event from the app or a notification ([source]); see [BrewSessionController.send]. */
    suspend fun send(event: TimerEvent, source: EventSource = EventSource.APP): TransitionOutcome
}

/**
 * The app object that owns the timer, as the alarm receiver and the
 * full-screen alert find it (D-057): the system creates them, and they ask
 * their `Application` for the controller instead of being injected. The app
 * implements this once the controller can be injected, i.e. once every port
 * of [TimerPorts] has its adapter (D-056). Until then the receiver drops an
 * alarm, of which there is none, since nothing can start the controller; a
 * debuggable build fails at once instead. The timer's instrumented tests run
 * the real controller in a test app that implements it.
 */
interface TimerHost {
    val timerInputs: TimerInputs
}
