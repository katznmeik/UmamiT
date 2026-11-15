package com.umamit.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone is locked (SCR-05, D-117 build note „Lock screen", A-17/A-18): a close of the timer while it
 * is takes the locked path. [read] asks the keyguard now (`KeyguardManager.isKeyguardLocked` in the app, a fake in
 * tests) and remembers the answer in [locked]; the root reads it at every start and resume and on each close, the
 * activity again when the phone is unlocked and when its window gains focus.
 *
 * It also holds whether such a close waits for `ON_STOP` ([closePending]), at the activity's level: the activity's
 * lock-screen flags read it in the same turn as the timer's state, so an alert that starts while the close waits
 * sets them again with the alert's own state, not a Compose frame later (rule 8, [timerVisibleForFlags]).
 */
internal class KeyguardLock(private val isLocked: () -> Boolean) {
    private val current = MutableStateFlow(false)
    private val closing = MutableStateFlow(false)

    /** The keyguard as last [read]. */
    val locked: StateFlow<Boolean> = current.asStateFlow()

    /** A close of the timer over the keyguard waits for `ON_STOP`: the timer is drawn, but not reported as visible. */
    val closePending: StateFlow<Boolean> = closing.asStateFlow()

    /** Asks the keyguard now, and remembers the answer in [locked]. */
    fun read(): Boolean = isLocked().also { current.value = it }

    /** Starts ([pending]) or ends the wait of a close over the keyguard. */
    fun setClosePending(pending: Boolean) {
        closing.value = pending
    }

    companion object {
        /** A phone that is never locked: every close of the timer is the unlocked one, and no close ever waits. */
        val Unlocked = KeyguardLock { false }
    }
}

/**
 * Whether the activity counts the timer as visible for its lock-screen flags: as the root [reported], or while a
 * close over the keyguard still waits ([closePending]) and an alert rings ([alerting]): the timer is still on top,
 * so the flags come back in the alert's own turn (rule 8), before the root cancels the close.
 */
internal fun timerVisibleForFlags(reported: Boolean, closePending: Boolean, alerting: Boolean): Boolean =
    reported || (closePending && alerting)
