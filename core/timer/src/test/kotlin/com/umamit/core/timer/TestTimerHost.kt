package com.umamit.core.timer

import android.app.Application
import com.umamit.core.timer.controller.EventSource
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
    val alarms = LinkedBlockingQueue<TimerAlarmKind>()
    val events = LinkedBlockingQueue<Pair<TimerEvent, EventSource>>()

    override val timerInputs: TimerInputs = object : TimerInputs {
        override suspend fun onAlarm(kind: TimerAlarmKind) = alarms.put(kind)

        override suspend fun send(event: TimerEvent, source: EventSource): TransitionOutcome {
            events.put(event to source)
            return TransitionOutcome.Accepted
        }
    }
}
