package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.data.catalog.CatalogRefreshIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * The taps on the catalog refresh's notifications (SCR-22, 03 §2.4, D-097), as [MainActivity]
 * keeps them: a new token per tap, pending until the root has shown the refresh sheet for it (or
 * let it go, before the setup is done). A pending tap survives the activity's recreation.
 *
 * Only this app's own intents count (#113, #121): MainActivity is exported as the launcher, so
 * any app can send it the action. The notifications' tap goes to the non-exported
 * [CatalogRefreshTapActivity] first, which lets it through the [gate] in this process; the action
 * counts only with the ticket passed last, once. A task reopened from the recent apps brings its
 * first intent again: that tap was handled then.
 */
class CatalogRefreshRequests(
    private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val gate: CatalogRefreshTapGate = CatalogRefreshTapGate,
) {
    private val tokens = MutableStateFlow(0L)

    /** The latest tap's token while it is pending; 0 for none. */
    val token: StateFlow<Long> = tokens.asStateFlow()

    /** The token the root has not handled yet; 0 for none. */
    val pending: Long get() = tokens.value

    /** Takes the tap [intent] carries; returns whether it carried one the trampoline let through. */
    fun offer(intent: Intent?): Boolean {
        val fresh = CatalogRefreshIntents.isOpenRefresh(intent) &&
            requireNotNull(intent).flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
            gate.take(ticketOf(intent))
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

    private fun ticketOf(intent: Intent): Long = try {
        intent.getLongExtra(EXTRA_TICKET, NO_TICKET)
    } catch (
        // A malformed parcel from another app (BadParcelableException, …) carries no ticket.
        @Suppress("TooGenericExceptionCaught") _: RuntimeException,
    ) {
        NO_TICKET
    }

    companion object {
        /** The ticket [CatalogRefreshTapActivity] passed for the tap. */
        const val EXTRA_TICKET = "com.umamit.app.extra.CATALOG_REFRESH_TICKET"

        private const val NO_TICKET = 0L
        private const val KEY_PENDING = "com.umamit.app.PENDING_CATALOG_REFRESH_REQUEST"
    }
}

/**
 * The notifications' taps [CatalogRefreshTapActivity] let through, in this process (#121). The
 * trampoline and MainActivity always run in the same process, also when the tap starts a new one,
 * so nothing has to outlive it. Only the latest ticket counts, and only once; it is random, so no
 * other app can guess it.
 */
object CatalogRefreshTapGate {
    private val latest = AtomicLong(0L)
    private val random = SecureRandom()

    /** A new ticket for one tap. */
    fun pass(): Long {
        var ticket = 0L
        while (ticket == 0L) ticket = random.nextLong()
        latest.set(ticket)
        return ticket
    }

    /** Whether [ticket] is the one passed last; it is used up at once. */
    fun take(ticket: Long): Boolean = ticket != 0L && latest.compareAndSet(ticket, 0L)
}
