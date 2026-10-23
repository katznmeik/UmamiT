package com.umamit.app.timer

import android.content.Intent
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
 * deliver the intent again. Two ways keep the request:
 * - the token is saved with the activity's state when the activity saves it after the request;
 * - an activity that was already stopped is relaunched with its *older* state, without saving
 *   again, but with the latest intent. So the token also travels in the intent, and a token
 *   newer than the last one handled is a request again.
 */
class TimerScreenRequests(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val tokens = MutableStateFlow(0L)

    /** The latest request's token; 0 for none. */
    val token: StateFlow<Long> = tokens.asStateFlow()

    /** The token the navigation has not handled yet; 0 for none. */
    var pending: Long = 0L
        private set

    /** The last token the navigation handled; 0 for none. */
    private var lastHandled = 0L

    /** A notification asks for the timer with [intent]; the intent keeps the request's token. */
    fun request(intent: Intent) {
        pending = newToken()
        intent.putExtra(EXTRA_TOKEN, pending)
        tokens.value = pending
    }

    /** The navigation opened the timer for [token]. */
    fun handled(token: Long) {
        if (token == pending) pending = 0L
        lastHandled = maxOf(lastHandled, token)
    }

    fun save(outState: Bundle) {
        if (pending != 0L) outState.putLong(KEY_PENDING, pending)
        outState.putLong(KEY_HANDLED, lastHandled)
    }

    /**
     * Restores a request still pending in [savedState], or carried by [intent] and newer than the
     * last one handled; returns whether there is one.
     */
    fun restore(savedState: Bundle?, intent: Intent?): Boolean {
        lastHandled = savedState?.getLong(KEY_HANDLED, 0L) ?: 0L
        val inIntent = intent?.getLongExtra(EXTRA_TOKEN, 0L) ?: 0L
        pending = savedState?.getLong(KEY_PENDING, 0L)?.takeIf { it != 0L }
            ?: inIntent.takeIf { it > lastHandled }
            ?: 0L
        if (pending != 0L) tokens.value = pending
        return pending != 0L
    }

    private companion object {
        const val KEY_PENDING = "com.umamit.app.PENDING_TIMER_REQUEST"
        const val KEY_HANDLED = "com.umamit.app.HANDLED_TIMER_REQUEST"
        const val EXTRA_TOKEN = "com.umamit.app.extra.TIMER_REQUEST"
    }
}
