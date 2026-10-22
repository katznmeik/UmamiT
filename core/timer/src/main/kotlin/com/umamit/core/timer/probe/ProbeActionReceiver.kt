package com.umamit.core.timer.probe

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification actions of the probe step (*Abbrechen*, *Abgegossen*); non-exported. */
class ProbeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) ProbeTimer.stop(context)
    }

    internal companion object {
        const val ACTION_STOP = "com.umamit.core.timer.action.PROBE_STOP"

        fun stopIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ProbeActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
