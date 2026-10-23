package com.umamit.core.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.TimerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recovers the timer after a reboot and after an update of the app (04 §7.3, §7.5; #46):
 *
 * - `BOOT_COMPLETED` arrives after the first unlock (the database is credential-encrypted).
 *   The alarms are gone; recovery sets them again on the new elapsed clock, tries to start
 *   the service and replays an alert whose deadline passed less than 10 min ago (E2).
 * - `MY_PACKAGE_REPLACED`: the update killed the process and its service; the alarms
 *   survived. Recovery brings the service and its notification back at once.
 *
 * Both broadcasts come from the system, so the receiver is not exported (04 §13). Each
 * runs one controller turn ([com.umamit.core.timer.controller.TimerInputs.recover]), kept
 * alive with `goAsync`; the controller logs the request (06 §5.6). The controller comes from
 * the app ([TimerHost], D-057). An app that does not hand it over yet runs no timer, so there
 * is nothing to recover: the broadcast is only logged, and nothing crashes at boot.
 */
class TimerRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RecoveryTrigger.BOOT_COMPLETED
            Intent.ACTION_MY_PACKAGE_REPLACED -> RecoveryTrigger.PACKAGE_REPLACED
            else -> return
        }
        val inputs = (context.applicationContext as? TimerHost)?.timerInputs
        if (inputs == null) {
            Log.i(TAG, "$trigger: the app runs no timer controller yet, nothing to recover")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                inputs.recover(trigger)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "UmamiTRecovery"

        /** Runs the controller turns of the broadcasts; they outlive `onReceive` (`goAsync`). */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
