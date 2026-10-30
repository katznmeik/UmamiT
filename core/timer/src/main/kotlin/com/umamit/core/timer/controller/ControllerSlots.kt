package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerState

/**
 * One slot of the [BrewSessionController] (04 §3.3, D-098): the engine state of its timer, what the UI shows
 * of its session row, its alert watchdog (alarm 107 or 207) and the state recovery restored into it, which
 * the log of an alarm that started the process reports (06 §5.6). The pure engine stays a single-timer
 * state machine; a slot owns one engine state. Read and written inside the controller's event lock.
 */
internal class ControllerSlot(val id: TimerSlot, alarms: TimerAlarmPort) {
    /** The engine state of the slot's timer. */
    var timer: TimerState = TimerState.Idle

    /** What the UI and the notifications show of a brew session's row (BRW-10, BRW-11); null for the quick timer. */
    var details: SessionDetails? = null

    /** The slot's own alert watchdog (04 §6.2). */
    val watchdog = AlertWatchdog(alarms, id)

    /** The state recovery restored, until the first alarm of the process or a transition of the slot. */
    var restoredForAlarmLog: TimerState.Active? = null

    /** The slot's active timer; null while it is idle. */
    val active: TimerState.Active? get() = timer as? TimerState.Active
}

/**
 * The controller's two slots, the brew slot first: the order of recovery and of due events (04 §3.3, §7.2).
 * From v0.3.0 each admits its own timer (D-098): a brew session and a quick timer run side by side.
 */
internal class ControllerSlots(alarms: TimerAlarmPort) : Iterable<ControllerSlot> {
    private val slots = TimerSlot.entries.map { ControllerSlot(it, alarms) }

    operator fun get(id: TimerSlot): ControllerSlot = slots.single { it.id == id }

    override fun iterator(): Iterator<ControllerSlot> = slots.iterator()

    /** The brew slot's engine state. */
    val brew: TimerState get() = get(TimerSlot.BREW).timer

    /** The quick slot's engine state. */
    val quick: TimerState get() = get(TimerSlot.QUICK).timer
}

/** The slot of a timer of [kind] (04 §3.3): a brew session's or the quick timer's. */
internal fun slotOf(kind: SessionKind): TimerSlot = when (kind) {
    is SessionKind.Brew -> TimerSlot.BREW
    SessionKind.Quick -> TimerSlot.QUICK
}
