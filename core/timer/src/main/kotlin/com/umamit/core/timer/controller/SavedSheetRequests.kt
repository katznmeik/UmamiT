package com.umamit.core.timer.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [SavedSheetPort] of the app (M1-16, D-061): it only remembers the
 * finished session whose „Gespeichert" sheet (SCR-06) is due, so the call
 * inside the controller's lock never blocks. The shell shows the sheet while
 * [pending] holds a session and dismisses it here (timeout, swipe, an alert).
 * A newer session replaces an older one; nothing survives the process.
 */
@Singleton
class SavedSheetRequests @Inject constructor() : SavedSheetPort {
    private val requests = MutableStateFlow<String?>(null)

    /** The session whose sheet is due, or null. */
    val pending: StateFlow<String?> = requests.asStateFlow()

    override fun showSavedSheet(sessionId: String) {
        requests.value = sessionId
    }

    /** The sheet of [sessionId] is gone; a newer request is kept. */
    fun dismiss(sessionId: String) {
        requests.compareAndSet(sessionId, null)
    }
}
