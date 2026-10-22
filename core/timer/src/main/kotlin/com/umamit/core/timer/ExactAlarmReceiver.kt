package com.umamit.core.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.umamit.core.timer.probe.ProbeTimer

/**
 * Receives the exact pre-alert and deadline alarms (04 §6.2; non-exported).
 * Today it forwards them to the probe step (M1-03a); M1-09 forwards them to
 * the BrewSessionController.
 */
class ExactAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = TimerAlarm.fromAction(intent.action) ?: return
        ProbeTimer.onAlarm(context, alarm, intent)
    }
}
