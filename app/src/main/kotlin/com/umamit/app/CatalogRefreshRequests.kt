package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.data.catalog.CatalogRefreshIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The taps on the catalog refresh's notifications (SCR-22, 03 §2.4, D-097), as [MainActivity]
 * keeps them: a new token per tap, pending until the root has shown the refresh sheet for it (or
 * let it go, before the setup is done). A pending tap survives the activity's recreation.
 *
 * Only the notifications' own intent counts ([CatalogRefreshIntents]); it carries nothing but
 * its action, so whoever sends it can at most open the sheet. A task reopened from the recent
 * apps brings its first intent again: that tap was handled then.
 */
class CatalogRefreshRequests(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val tokens = MutableStateFlow(0L)

    /** The latest tap's token while it is pending; 0 for none. */
    val token: StateFlow<Long> = tokens.asStateFlow()

    /** The token the root has not handled yet; 0 for none. */
    val pending: Long get() = tokens.value

    /** Takes the tap [intent] carries; returns whether it carried one. */
    fun offer(intent: Intent?): Boolean {
        val fresh = CatalogRefreshIntents.isOpenRefresh(intent) &&
            requireNotNull(intent).flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        if (fresh) tokens.value = newToken()
        return fresh
    }

    /** The root handled the tap [token]. */
    fun handled(token: Long) {
        if (tokens.value == token) tokens.value = 0L
    }

    fun save(outState: Bundle) {
        if (pending != 0L) outState.putLong(KEY_PENDING, pending)
    }

    fun restore(savedState: Bundle?) {
        val saved = savedState?.getLong(KEY_PENDING, 0L) ?: 0L
        if (saved != 0L) tokens.value = saved
    }

    private companion object {
        const val KEY_PENDING = "com.umamit.app.PENDING_CATALOG_REFRESH_REQUEST"
    }
}
