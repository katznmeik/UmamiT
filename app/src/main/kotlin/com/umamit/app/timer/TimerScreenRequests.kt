package com.umamit.app.timer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.notification.TimerScreenIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A notification's request to open the timer (#47, D-064): a new [token] per request (0: none), for [slot]'s timer
 * (04 §8.2: each timer notification names its slot, D-098); null: the one in front.
 */
data class TimerRequest(val token: Long, val slot: TimerSlot? = null)

/**
 * The requests of the timer's notifications to open the timer (#47, D-064), as the activity
 * keeps them: a new token per request, pending until the navigation opened the timer, with the
 * timer the notification names.
 *
 * A pending request survives the activity's recreation. The full-screen intent reaches an
 * activity that sleeps behind the keyguard at once, before it draws; a configuration change
 * when it becomes visible (dark mode, rotation, a fold) recreates it, and Android does not
 * deliver the intent again. Two ways keep the request:
 * - the token and its slot are saved with the activity's state when the activity saves it after the request;
 * - an activity that was already stopped is relaunched with its *older* state, without saving
 *   again, but with the latest intent. So the token also travels in the intent, beside the
 *   notification's slot, and a token newer than the last one handled is a request again.
 */
class TimerScreenRequests(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val requests = MutableStateFlow(TimerRequest(0L))

    /** The latest request; token 0 for none. */
    val request: StateFlow<TimerRequest> = requests.asStateFlow()

    /** The token the navigation has not handled yet; 0 for none. */
    var pending: Long = 0L
        private set

    /** The last token the navigation handled; 0 for none. */
    private var lastHandled = 0L

    /** A notification asks for the timer with [intent]; the intent keeps the request's token. */
    fun request(intent: Intent) {
        pending = newToken()
        intent.putExtra(EXTRA_TOKEN, pending)
        requests.value = TimerRequest(pending, TimerScreenIntents.slotOf(intent))
    }

    /** The navigation opened the timer for [token]. */
    fun handled(token: Long) {
        if (token == pending) pending = 0L
        lastHandled = maxOf(lastHandled, token)
    }

    fun save(outState: Bundle) {
        if (pending != 0L) {
            outState.putLong(KEY_PENDING, pending)
            requests.value.slot?.let { outState.putString(KEY_PENDING_SLOT, it.name) }
        }
        outState.putLong(KEY_HANDLED, lastHandled)
    }

    /**
     * Restores a request still pending in [savedState], or carried by [intent] and newer than the
     * last one handled; returns whether there is one.
     */
    fun restore(savedState: Bundle?, intent: Intent?): Boolean {
        lastHandled = savedState?.getLong(KEY_HANDLED, 0L) ?: 0L
        val saved = savedState?.getLong(KEY_PENDING, 0L)?.takeIf { it != 0L }?.let { token ->
            val slot = savedState.getString(KEY_PENDING_SLOT)
            TimerRequest(token, TimerSlot.entries.firstOrNull { it.name == slot })
        }
        val inIntent = intent?.getLongExtra(EXTRA_TOKEN, 0L)?.takeIf { it > lastHandled }?.let { token ->
            TimerRequest(token, TimerScreenIntents.slotOf(intent))
        }
        val restored = saved ?: inIntent
        pending = restored?.token ?: 0L
        if (restored != null) requests.value = restored
        return restored != null
    }

    private companion object {
        const val KEY_PENDING = "com.umamit.app.PENDING_TIMER_REQUEST"
        const val KEY_PENDING_SLOT = "com.umamit.app.PENDING_TIMER_REQUEST_SLOT"
        const val KEY_HANDLED = "com.umamit.app.HANDLED_TIMER_REQUEST"
        const val EXTRA_TOKEN = "com.umamit.app.extra.TIMER_REQUEST"
    }
}
