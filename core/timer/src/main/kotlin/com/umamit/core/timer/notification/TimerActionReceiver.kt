package com.umamit.core.timer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The actions of the timer's notifications and Live Update (04 §8.2; TMR-07, TMR-41;
 * non-exported): *Start*, *Pause*, *Weiter*, *Abgegossen* (*Weggegossen*, *Fertig*), *Sitzung
 * beenden* and *Abbrechen* go to the controller as user events from the notification
 * ([EventSource.NOTIFICATION]), through the app ([TimerHost], D-057), in one turn kept alive
 * with `goAsync`. The tap allows the foreground service to start from the background (the
 * notification-action exemption), so the transition's `StartForeground` succeeds.
 *
 * The delete intent of the Live Update comes here too: the dismissal is recorded and that
 * state is not posted again (04 §8.3).
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dismissed = dismissedKeyOf(intent)
        if (dismissed != null) {
            LiveUpdateDismissal.dismissedKey = dismissed
        } else {
            eventOf(intent)?.let { send(context, slotOf(intent), it) }
        }
    }

    private fun send(context: Context, slot: TimerSlot, event: TimerEvent) {
        val inputs = (context.applicationContext as? TimerHost)?.timerInputs
        if (inputs == null) {
            Log.e(TAG, "Notification action $event dropped: the app does not hand over its timer controller")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                // Within the tap's exemption the controller's turn also sends a start Android
                // refused earlier once more, built from the state of now (D-063).
                inputs.send(slot, event, EventSource.NOTIFICATION)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UmamiTAction"
        private const val ACTION_PREFIX = "com.umamit.core.timer.action.NOTIFICATION."
        private const val ACTION_DISMISSED = "com.umamit.core.timer.action.LIVE_UPDATE_DISMISSED"
        private const val EXTRA_KEY = "com.umamit.core.timer.extra.NOTIFICATION_KEY"

        /** Runs the controller turns of the actions; they outlive `onReceive` (`goAsync`). */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** The events a notification offers, by the name in their action. */
        private val EVENTS: Map<String, TimerEvent> = mapOf(
            "START" to TimerEvent.Start,
            "PAUSE" to TimerEvent.Pause,
            "RESUME" to TimerEvent.Resume,
            "POURED" to TimerEvent.Poured,
            "FINISH_SESSION" to TimerEvent.FinishSession,
            "CANCEL" to TimerEvent.Cancel,
        )

        /**
         * The broadcast of the action that sends [event] to [slot]'s timer (04 §3.3: every user event names its
         * slot); one per slot and event, so each has its own PendingIntent. The brew slot's keep the actions of
         * v0.2.2; an action without a slot reads as the brew slot's.
         */
        fun intent(context: Context, event: TimerEvent, slot: TimerSlot = TimerSlot.BREW): Intent {
            val name = EVENTS.entries.first { it.value == event }.key
            return Intent(context, TimerActionReceiver::class.java).setAction(ACTION_PREFIX + slot.named(name))
        }

        /** The delete intent of a notification that shows the state of [key]. */
        fun dismissIntent(context: Context, key: String): Intent =
            Intent(context, TimerActionReceiver::class.java).setAction(ACTION_DISMISSED).putExtra(EXTRA_KEY, key)

        /** The event of an action broadcast made by [intent]; null for any other intent. */
        fun eventOf(intent: Intent): TimerEvent? = intent.action?.takeIf { it.startsWith(ACTION_PREFIX) }
            ?.let { EVENTS[it.removePrefix(ACTION_PREFIX).removePrefix(TimerSlot.QUICK.namePrefix)] }

        /** The slot of an action broadcast made by [intent]: the quick slot's, else the brew slot's. */
        fun slotOf(intent: Intent): TimerSlot =
            if (intent.action?.startsWith(ACTION_PREFIX + TimerSlot.QUICK.namePrefix) == true) {
                TimerSlot.QUICK
            } else {
                TimerSlot.BREW
            }

        /** The state key of a dismissal made by [dismissIntent]; null for any other intent. */
        fun dismissedKeyOf(intent: Intent): String? =
            intent.takeIf { it.action == ACTION_DISMISSED }?.getStringExtra(EXTRA_KEY)

        /** The request code of [event]'s PendingIntent in [slot]: 400 + its index, 420 + it for the quick slot. */
        internal fun requestCodeOf(event: TimerEvent, slot: TimerSlot = TimerSlot.BREW): Int =
            REQUEST_CODE_BASE + (if (slot == TimerSlot.QUICK) QUICK_CODE_OFFSET else 0) + EVENTS.values.indexOf(event)

        private const val REQUEST_CODE_BASE = 400
        private const val QUICK_CODE_OFFSET = 20
    }
}
