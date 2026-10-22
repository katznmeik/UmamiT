package com.umamit.core.timer.controller

import com.umamit.core.common.MonotonicClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [SavedSheetPort] of the app (M1-16, D-061): it only remembers the
 * finished session whose „Gespeichert" sheet (SCR-06) is due and when it was
 * asked for, so the call inside the controller's lock never blocks. The shell
 * shows the sheet while [pending] holds a session and dismisses it here
 * (timeout, swipe, an alert). A request lives only for its own 10 s
 * ([remainingMs]), also while no shell shows it (the timer route covers the
 * shell, the app is in the background), so a stale sheet never comes back
 * later. A newer session replaces an older one; nothing survives the process.
 */
@Singleton
class SavedSheetRequests @Inject constructor(private val clock: MonotonicClock) : SavedSheetPort {
    private val requests = MutableStateFlow<String?>(null)

    @Volatile
    private var requestedAtMs = 0L

    /** The session whose sheet is due, or null. */
    val pending: StateFlow<String?> = requests.asStateFlow()

    override fun showSavedSheet(sessionId: String) {
        requestedAtMs = clock.elapsedMs()
        requests.value = sessionId
    }

    /** How much of [timeoutMs] is left for the sheet of [sessionId] since it was asked for; 0 when not pending. */
    fun remainingMs(sessionId: String, timeoutMs: Long): Long =
        if (requests.value != sessionId) 0 else (timeoutMs - (clock.elapsedMs() - requestedAtMs)).coerceAtLeast(0)

    /** The sheet of [sessionId] is gone; a newer request is kept. */
    fun dismiss(sessionId: String) {
        requests.compareAndSet(sessionId, null)
    }
}
