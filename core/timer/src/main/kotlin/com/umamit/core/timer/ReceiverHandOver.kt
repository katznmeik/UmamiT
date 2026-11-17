package com.umamit.core.timer

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs the controller turns of the timer's broadcasts; they outlive `onReceive` (`goAsync`). */
private val handOverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Hands a broadcast over to the controller (D-057): keeps it alive with `goAsync`, runs [block]
 * as one turn on the receivers' shared scope and finishes the broadcast when the turn ends, also
 * when it fails. The receiver finds its `TimerHost` and handles its absence before calling this.
 */
internal fun BroadcastReceiver.handOver(block: suspend () -> Unit) {
    val pending = goAsync()
    handOverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
