package com.umamit.core.timer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerAlarm
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the timer's alarms (04 §6.2; non-exported) and feeds each to the
 * controller's `onAlarm` in one turn, kept alive with `goAsync`. The
 * controller comes from the app ([TimerHost], D-057).
 *
 * In a process the alarm just started (E3), the controller's first turn
 * recovers the persisted state (04 §7.2), and the state's own effects start
 * the foreground service, within the exemption that exact-alarm delivery
 * grants for it. In the same turn, the controller sends a service start
 * Android refused earlier again, built from the state of that moment
 * (D-057, D-063).
 */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = alarmOf(intent) ?: return
        val inputs = (context.applicationContext as? TimerHost)?.timerInputs
        if (inputs == null) {
            unwired(context, alarm)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                inputs.onAlarm(alarm.slot, alarm.kind)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The app schedules timer alarms but does not hand over its controller: every alert
     * after a kill would be lost. A debuggable build fails at once; a release build logs it.
     */
    private fun unwired(context: Context, alarm: TimerAlarm) {
        val message = "Alarm $alarm dropped: the app does not hand the receiver its timer controller (TimerHost)"
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) { message }
        Log.e(TAG, message)
    }

    companion object {
        private const val TAG = "UmamiTAlarm"
        private const val ACTION_PREFIX = "com.umamit.core.timer.action.TIMER_ALARM."

        /** Runs the controller turns of delivered alarms; they outlive `onReceive` (`goAsync`). */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * The broadcast of the alarm of [kind] in [slot]; its action names both (`dumpsys alarm` shows it as
         * the tag). The brew slot's is the broadcast of v0.2.2 („…TIMER_ALARM.DEADLINE"), so its alarms
         * replace and cancel those the old version set; the quick slot's adds „QUICK." (D-098).
         */
        fun intent(context: Context, slot: TimerSlot, kind: TimerAlarmKind): Intent =
            Intent(context, TimerAlarmReceiver::class.java).setAction(actionOf(slot, kind))

        /**
         * The alarm of a broadcast made by [intent]; null for any other intent. An alarm v0.2.2 set, for a
         * brew or a quick timer, reads as the brew slot's (04 §6.2): the controller handles every event due
         * in either slot.
         */
        fun alarmOf(intent: Intent): TimerAlarm? = TimerSlot.entries.firstNotNullOfOrNull { slot ->
            TimerAlarmKind.entries.firstOrNull { actionOf(slot, it) == intent.action }?.let { TimerAlarm(slot, it) }
        }

        private fun actionOf(slot: TimerSlot, kind: TimerAlarmKind): String = ACTION_PREFIX + slot.named(kind.name)
    }
}
