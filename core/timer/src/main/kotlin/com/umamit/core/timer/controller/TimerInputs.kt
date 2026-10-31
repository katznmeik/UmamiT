package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome

/**
 * The [BrewSessionController]'s inputs that components the system creates
 * reach through the app ([TimerHost]): the alarm receiver (04 §6.2), the
 * recovery receivers (04 §7.3, §7.5) and the full-screen alert (04 §8.2).
 */
interface TimerInputs {
    /**
     * The alarm of [kind] in [slot] arrived; runs every time-based event due now (suspends for one controller
     * turn). An alarm v0.2.2 left behind arrives as the brew slot's (04 §6.2, D-098).
     */
    suspend fun onAlarm(slot: TimerSlot, kind: TimerAlarmKind)

    /** A user event of [slot]'s timer from the app or a notification ([source]); see [BrewSessionController.send]. */
    suspend fun send(slot: TimerSlot, event: TimerEvent, source: EventSource = EventSource.APP): TransitionOutcome

    /**
     * The process should hold the persisted timer now ([trigger], 04 §7.2): one controller turn
     * that recovers it if this process has not yet; otherwise it changes nothing.
     */
    suspend fun recover(trigger: RecoveryTrigger)
}

/** What asks the controller to recover the persisted timer without an input of its own (04 §7.2). */
enum class RecoveryTrigger {
    /** The app's process started (04 §7.2), e.g. the user opened the app after a kill or a force stop (04 §7.4). */
    APP_START,

    /** `BOOT_COMPLETED`, delivered after the first unlock (04 §7.3, E2). */
    BOOT_COMPLETED,

    /** `MY_PACKAGE_REPLACED`: an update killed the process and its service; the alarms survived (04 §7.5). */
    PACKAGE_REPLACED,
}

/**
 * The app object that owns the timer, as the alarm and recovery receivers and
 * the full-screen alert find it (D-057): the system creates them, and they ask
 * their `Application` for the controller instead of being injected. The app
 * implements this once the controller can be injected, i.e. once every port
 * of [TimerPorts] has its adapter (D-056), and then calls
 * `timerInputs.recover(RecoveryTrigger.APP_START)` from `onCreate`, off the
 * main thread (04 §7.2, D-063). Until then the alarm receiver drops an alarm,
 * of which there is none, since nothing can start the controller; a
 * debuggable build fails at once instead. The timer's instrumented tests run
 * the real controller in a test app that implements it.
 */
interface TimerHost {
    val timerInputs: TimerInputs
}
