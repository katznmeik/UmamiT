package com.umamit.core.timer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerAlarmSinkHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the timer's alarms (04 §6.2; non-exported) and feeds each to the
 * controller's `onAlarm` in one turn, kept alive with `goAsync`. The
 * controller comes from the app ([TimerAlarmSinkHost], D-057).
 *
 * It decides nothing itself: in a process the alarm just started (E3), the
 * controller's first turn recovers the persisted state (04 §7.2), and the
 * state's own effects start the foreground service, within the exemption that
 * exact-alarm delivery grants for it (04 §6.2, D-057).
 */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = kindOf(intent) ?: return
        val sink = (context.applicationContext as? TimerAlarmSinkHost)?.timerAlarmSink
        if (sink == null) {
            Log.w(TAG, "Alarm $kind dropped: the app does not run the timer controller yet")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                sink.onAlarm(kind)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UmamiTAlarm"
        private const val ACTION_PREFIX = "com.umamit.core.timer.action.TIMER_ALARM."

        /** Runs the controller turns of delivered alarms; they outlive `onReceive` (`goAsync`). */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** The broadcast of the alarm of [kind]; its action names the kind (`dumpsys alarm` shows it as the tag). */
        fun intent(context: Context, kind: TimerAlarmKind): Intent =
            Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_PREFIX + kind.name)

        /** The kind of an alarm broadcast made by [intent]; null for any other intent. */
        fun kindOf(intent: Intent): TimerAlarmKind? =
            TimerAlarmKind.entries.firstOrNull { ACTION_PREFIX + it.name == intent.action }
    }
}
