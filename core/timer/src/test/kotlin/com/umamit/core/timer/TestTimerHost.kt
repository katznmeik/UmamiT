package com.umamit.core.timer

import android.app.Application
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.TimerAlarm
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerInputs
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import java.util.concurrent.LinkedBlockingQueue

/**
 * An app that runs the controller, for the components the system creates (receiver,
 * full-screen alert): it records what they hand the controller.
 */
class TestTimerHost :
    Application(),
    TimerHost {
    val alarms = LinkedBlockingQueue<TimerAlarm>()
    val events = LinkedBlockingQueue<Pair<TimerEvent, EventSource>>()

    /** The slot of each of [events], in the same order. */
    val slots = LinkedBlockingQueue<TimerSlot>()
    val recoveries = LinkedBlockingQueue<RecoveryTrigger>()

    override val timerInputs: TimerInputs = object : TimerInputs {
        override suspend fun onAlarm(slot: TimerSlot, kind: TimerAlarmKind) = alarms.put(TimerAlarm(slot, kind))

        override suspend fun send(slot: TimerSlot, event: TimerEvent, source: EventSource): TransitionOutcome {
            events.put(event to source)
            slots.put(slot)
            return TransitionOutcome.Accepted
        }

        override suspend fun recover(trigger: RecoveryTrigger) = recoveries.put(trigger)
    }
}
