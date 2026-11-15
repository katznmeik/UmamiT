package com.umamit.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController

/** What [rememberLeavingOverKeyguard] gives the root. */
internal class LeavingOverKeyguard(
    /** A close over the keyguard waits for `ON_STOP`: the timer is drawn, but no longer reported as visible. */
    val pending: Boolean,
    /** The phone as last read. */
    private val keyguardLocked: Boolean,
    /** The timer entry popped at `ON_STOP`; null: none. */
    private val poppedEntryId: String?,
    /** Starts the locked close if the phone is locked now; false: it is not. */
    private val leave: () -> Boolean,
) {
    /** Back (a swipe too) takes the locked path: while the phone is locked, or the close already waits. */
    val backTakesLockedPath: Boolean get() = keyguardLocked || pending

    /** Whether the timer counts as visible, for the lock-screen flags: [shown], unless it is leaving. */
    fun reported(shown: Boolean): Boolean = shown && !pending

    /** The timer [entry] popped at `ON_STOP` leaves with no transition. */
    fun poppedAtStop(entry: NavBackStackEntry): Boolean = entry.id == poppedEntryId

    /** Closes the timer: over the keyguard and with no alert ([alerting]) the locked way, else with [pop]. */
    fun close(alerting: Boolean, pop: () -> Unit) {
        if (alerting || !leave()) pop()
    }
}

/**
 * The close of the timer over the keyguard (SCR-05, D-117 build note „Lock screen", A-17/A-18).
 * [LeavingOverKeyguard.close] reads the [keyguard]; locked, it reports the timer as gone at once
 * ([onTimerVisibleChange], so the activity clears `showWhenLocked` / `turnScreenOn` in that turn) and leaves the
 * timer drawn ([LeavingTimerGuard]). Then:
 * - **`ON_STOP`** (the keyguard covers the app): the timer is popped, with no transition, while the window is not
 *   drawn, so the next unlock draws the shell in its first frame;
 * - **a new alert** before that (rule 8): the close is cancelled and the timer is reported again at once, so the
 *   flags come back in the alert's first frame; the route on top is still the timer, nothing is delayed;
 * - **the phone unlocked instead** (no `ON_STOP` comes): the timer is popped as unlocked, with `exitFade`.
 *
 * The root calls it before [rememberTimerFirst], so a new alert cancels the close before anything else sees it.
 */
@Composable
internal fun rememberLeavingOverKeyguard(
    navController: NavHostController,
    keyguard: KeyguardLock,
    alert: String?,
    onTimerVisibleChange: (Boolean) -> Unit,
): LeavingOverKeyguard {
    var pending by remember { mutableStateOf(false) }
    var poppedEntryId by remember { mutableStateOf<String?>(null) }
    val keyguardLocked by keyguard.locked.collectAsStateWithLifecycle()
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { keyguard.read() }
    LaunchedEffect(alert) {
        if (alert != null && pending) {
            pending = false
            if (navController.currentDestination?.hasRoute<TimerRoute>() == true) currentOnTimerVisibleChange(true)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (pending) {
            pending = false
            poppedEntryId = navController.currentBackStackEntry?.takeIf { it.destination.hasRoute<TimerRoute>() }?.id
            navController.popBackStack<TimerRoute>(inclusive = true)
        }
    }
    // Unlocked while the close waits (read as locked, the phone was unlocked before `ON_STOP`).
    LaunchedEffect(pending, keyguardLocked) {
        if (pending && !keyguard.locked.value) {
            pending = false
            navController.popBackStack<TimerRoute>(inclusive = true)
        }
    }
    return LeavingOverKeyguard(
        pending = pending,
        keyguardLocked = keyguardLocked,
        poppedEntryId = poppedEntryId,
        leave = {
            val locked = keyguard.read()
            if (locked && !pending) {
                pending = true
                currentOnTimerVisibleChange(false)
            }
            locked
        },
    )
}

/**
 * The timer route's [content] with the locked path: locked, back (a swipe too) goes to [onClose] before the NavHost's
 * own handler, so there is no predictive preview of the shell over the lock screen (A-18); the content's own back
 * handlers (a sheet's) still come first. Unlocked, the NavHost's back as ever. While the close waits, the timer stays
 * drawn as it is and takes no tap (as A-15's closing route).
 */
@Composable
internal fun LeavingTimerGuard(leaving: LeavingOverKeyguard, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = leaving.backTakesLockedPath, onBack = onClose)
    content()
    if (leaving.pending) Box(Modifier.fillMaxSize().consumeAllPointers().testTag(LEAVING_COVER_TAG))
}

/** Takes every tap and drag, so nothing under it reacts. */
private fun Modifier.consumeAllPointers(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** The cover that keeps the timer inert while it leaves over the keyguard (A-17). */
internal const val LEAVING_COVER_TAG = "leaving_cover"
