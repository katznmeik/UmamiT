package com.umamit.core.timer.controller

/**
 * Where the alarm receiver hands an arrived alarm (04 §6.2): the
 * [BrewSessionController]'s `onAlarm`.
 */
fun interface TimerAlarmSink {
    /** The alarm of [kind] arrived; runs every time-based event due now (suspends for one controller turn). */
    suspend fun onAlarm(kind: TimerAlarmKind)
}

/**
 * The app object that owns the timer, as the alarm receiver finds it (D-057):
 * the receiver is created by the system and asks its `Application` for the
 * controller instead of being injected. The app implements this once the
 * controller can be injected, i.e. once every port of [TimerPorts] has its
 * adapter (D-056); until then the receiver drops an alarm, of which there is
 * none, since nothing can start the controller. The timer's instrumented
 * tests run the real controller in a test app that implements it.
 */
interface TimerAlarmSinkHost {
    val timerAlarmSink: TimerAlarmSink
}
