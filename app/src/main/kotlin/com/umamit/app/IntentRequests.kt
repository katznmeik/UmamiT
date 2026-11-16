package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The taps on a notification that opens the app somewhere, as [MainActivity] keeps them: a new token per tap, pending
 * until the root has shown what it opens for it (or let it go, before the setup is done). A pending tap survives the
 * activity's recreation, under the `Bundle` [key]; a task reopened from the recent apps brings its first intent again,
 * which was handled then.
 *
 * [accepts] says whether an intent is such a tap; it runs only for an intent not launched from the history, and last
 * (a gate may use a ticket up).
 */
open class IntentRequests(
    private val key: String,
    private val accepts: (Intent) -> Boolean,
    private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    private val tokens = MutableStateFlow(0L)

    /** The latest tap's token while it is pending; 0 for none. */
    val token: StateFlow<Long> = tokens.asStateFlow()

    /** The token the root has not handled yet; 0 for none. */
    val pending: Long get() = tokens.value

    /** Takes the tap [intent] carries; returns whether it carried a fresh one. */
    fun offer(intent: Intent?): Boolean {
        val fresh = intent != null &&
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0 &&
            accepts(intent)
        if (fresh) tokens.value = newToken()
        return fresh
    }

    /** The root handled the tap [token]. */
    fun handled(token: Long) {
        if (tokens.value == token) tokens.value = 0L
    }

    fun save(outState: Bundle) {
        if (pending != 0L) outState.putLong(key, pending)
    }

    fun restore(savedState: Bundle?) {
        val saved = savedState?.getLong(key, 0L) ?: 0L
        if (saved != 0L) tokens.value = saved
    }
}
