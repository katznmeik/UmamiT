package com.umamit.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone is locked (SCR-05, D-117 build note „Lock screen", A-17/A-18): a close of the timer while it
 * is takes the locked path. [read] asks the keyguard now (`KeyguardManager.isKeyguardLocked` in the app, a fake in
 * tests) and remembers the answer in [locked]; the root reads it at every start and on each close, the activity
 * again when the phone is unlocked.
 */
internal class KeyguardLock(private val isLocked: () -> Boolean) {
    private val current = MutableStateFlow(false)

    /** The keyguard as last [read]. */
    val locked: StateFlow<Boolean> = current.asStateFlow()

    /** Asks the keyguard now, and remembers the answer in [locked]. */
    fun read(): Boolean = isLocked().also { current.value = it }

    companion object {
        /** A phone that is never locked: every close of the timer is the unlocked one. */
        val Unlocked = KeyguardLock { false }
    }
}
