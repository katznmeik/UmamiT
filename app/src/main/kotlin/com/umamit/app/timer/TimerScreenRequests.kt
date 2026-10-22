package com.umamit.app.timer

import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The requests of the timer's notifications to open the timer (#47, D-064), as the activity
 * keeps them: a new token per request, pending until the navigation opened the timer.
 *
 * A pending request survives the activity's recreation. The full-screen intent reaches an
 * activity that sleeps behind the keyguard at once, before it draws; a configuration change
 * when it becomes visible (dark mode, rotation, a fold) recreates it, and Android does not
 * deliver the intent again. The new activity restores the request, shows over the lock screen
 * at once and opens the timer.
 */
class TimerScreenRequests(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val tokens = MutableStateFlow(0L)

    /** The latest request's token; 0 for none. */
    val token: StateFlow<Long> = tokens.asStateFlow()

    /** The token the navigation has not handled yet; 0 for none. */
    var pending: Long = 0L
        private set

    /** A notification asks for the timer. */
    fun request() {
        pending = newToken()
        tokens.value = pending
    }

    /** The navigation opened the timer for [token]. */
    fun handled(token: Long) {
        if (token == pending) pending = 0L
    }

    fun save(outState: Bundle) {
        if (pending != 0L) outState.putLong(KEY_PENDING, pending)
    }

    /** Restores a request still pending in [savedState]; returns whether there is one. */
    fun restore(savedState: Bundle?): Boolean {
        pending = savedState?.getLong(KEY_PENDING, 0L) ?: 0L
        if (pending != 0L) tokens.value = pending
        return pending != 0L
    }

    private companion object {
        const val KEY_PENDING = "com.umamit.app.PENDING_TIMER_REQUEST"
    }
}
