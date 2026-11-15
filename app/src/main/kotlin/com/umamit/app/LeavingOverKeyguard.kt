package com.umamit.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.umamit.app.timer.TimerRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What [rememberLeavingOverKeyguard] gives the root. */
internal class LeavingOverKeyguard(
    /**
     * Whether a close over the keyguard waits for `ON_STOP`, read live: the timer entry fading out after an unlock
     * keeps the instance of its last composition, and drops its cover all the same.
     */
    private val pendingState: State<Boolean>,
    /** The phone as last read. */
    private val keyguardLocked: Boolean,
    /** The entry below the timer is a timer route too (two timers): its close pops as ever. */
    private val timerBelow: Boolean,
    /** The timer entry popped at `ON_STOP`; null: none. */
    private val poppedEntryId: String?,
    /** Starts the locked close if the phone is locked now; false: it is not, or the close pops as ever. */
    private val leave: () -> Boolean,
) {
    /** A close over the keyguard waits for `ON_STOP`: the timer is drawn, but no longer reported as visible. */
    val pending: Boolean get() = pendingState.value

    /** Back (a swipe too) takes the locked path: while the phone is locked, or the close already waits. */
    val backTakesLockedPath: Boolean get() = (keyguardLocked || pending) && !timerBelow

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
 * [LeavingOverKeyguard.close] reads the [keyguard]; locked, and with no timer route below (two timers: the other one
 * stays over the keyguard, so the close pops as ever), it reports the timer as gone at once ([onTimerVisibleChange],
 * so the activity clears `showWhenLocked` / `turnScreenOn` in that turn) and leaves the timer drawn
 * ([LeavingTimerGuard]). The wait is kept in the [keyguard] ([KeyguardLock.closePending]), at the activity's level.
 * Then:
 * - **`ON_STOP`** (the keyguard covers the app): the timer is popped, with no transition, while the window is not
 *   drawn, so the next unlock draws the shell in its first frame; unless an alert rings now ([alertKey], read live,
 *   rule 8) or a notification asked for the timer since: then the close is cancelled instead;
 * - **a new alert or a notification's request** ([timerRequest]) before that (rule 8): the close is cancelled and
 *   the timer is reported again at once, so the flags come back in the alert's first frame; the route on top is
 *   still the timer, nothing is delayed;
 * - **the phone unlocked instead** (no `ON_STOP` comes; read at the unlock broadcast, every start and resume, a
 *   window focus gain and once more ≈ 1 s after the close): the timer is popped as unlocked, with `exitFade`; never
 *   while an alert rings, which cancels the close instead.
 *
 * The root calls it before [rememberTimerFirst], so a new alert cancels the close before anything else sees it.
 */
@Composable
internal fun rememberLeavingOverKeyguard(
    navController: NavHostController,
    keyguard: KeyguardLock,
    alertKey: StateFlow<String?>,
    timerRequest: StateFlow<TimerRequest>,
    onTimerVisibleChange: (Boolean) -> Unit,
): LeavingOverKeyguard {
    val pendingState = keyguard.closePending.collectAsState()
    val pending by pendingState
    val keyguardLocked by keyguard.locked.collectAsStateWithLifecycle()
    val alert by alertKey.collectAsStateWithLifecycle()
    val request by timerRequest.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val timerOnTop = entry?.destination?.hasRoute<TimerRoute>() == true
    val timerBelow = timerOnTop && navController.previousBackStackEntry?.destination?.hasRoute<TimerRoute>() == true
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    val close = remember(navController, keyguard, alertKey, timerRequest) {
        LockedClose(navController, keyguard, alertKey, timerRequest) { currentOnTimerVisibleChange(it) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { keyguard.read() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { keyguard.read() }
    LaunchedEffect(alert) { if (alert != null) close.cancel() }
    LaunchedEffect(request.token) { close.onRequest(request.token) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { close.onStop() }
    // Unlocked while the close waits (read as locked, the phone was unlocked before `ON_STOP`).
    LaunchedEffect(pending, keyguardLocked) { if (pending && !keyguardLocked) close.popIfUnlocked() }
    // A safety re-read, should no unlock broadcast, resume or focus gain tell of an unlock.
    LaunchedEffect(pending) {
        if (pending) {
            delay(SAFETY_READ_MS)
            keyguard.read()
            close.popIfUnlocked()
        }
    }
    // Only the timer on top can be leaving.
    LaunchedEffect(timerOnTop) { if (!timerOnTop) keyguard.setClosePending(false) }
    return LeavingOverKeyguard(
        pendingState = pendingState,
        keyguardLocked = keyguardLocked,
        timerBelow = timerBelow,
        poppedEntryId = close.poppedEntryId,
        leave = { close.leave(timerBelow) },
    )
}

/** The steps of [rememberLeavingOverKeyguard]; the wait itself is the [keyguard]'s [KeyguardLock.closePending]. */
private class LockedClose(
    private val navController: NavHostController,
    private val keyguard: KeyguardLock,
    private val alertKey: StateFlow<String?>,
    private val timerRequest: StateFlow<TimerRequest>,
    private val onTimerVisibleChange: (Boolean) -> Unit,
) {
    /** The timer entry popped at `ON_STOP`; null: none. */
    var poppedEntryId by mutableStateOf<String?>(null)
        private set

    /** The request token when the close began: a newer one asks for the timer (N1). */
    private var requestAtLeave = 0L

    private val pending: Boolean get() = keyguard.closePending.value

    /** Starts the close over the keyguard if the phone is locked now and no timer route is [timerBelow]. */
    fun leave(timerBelow: Boolean): Boolean {
        val locked = keyguard.read()
        when {
            // Unlocked: the close pops as ever, and no waiting close pops a second time.
            !locked -> keyguard.setClosePending(false)

            timerBelow || pending -> Unit

            else -> {
                requestAtLeave = timerRequest.value.token
                keyguard.setClosePending(true)
                onTimerVisibleChange(false)
            }
        }
        return locked && !timerBelow
    }

    /** The close no longer waits; the timer still on top is reported again (a new alert, rule 8). */
    fun cancel() {
        if (!pending) return
        // Reported first, so the flags are never cleared between the two steps (rule 8).
        if (navController.currentDestination?.hasRoute<TimerRoute>() == true) onTimerVisibleChange(true)
        keyguard.setClosePending(false)
    }

    /** A notification's request ([token]) newer than the close cancels it, as a new alert does. */
    fun onRequest(token: Long) {
        if (token != requestAtLeave) cancel()
    }

    /** The keyguard covers the app: the timer is popped, no transition; an alert or a request (read live) cancels. */
    fun onStop() {
        if (!pending) return
        if (alertKey.value != null || timerRequest.value.token != requestAtLeave) {
            cancel()
        } else {
            keyguard.setClosePending(false)
            poppedEntryId = navController.currentBackStackEntry?.takeIf { it.destination.hasRoute<TimerRoute>() }?.id
            navController.popBackStack<TimerRoute>(inclusive = true)
        }
    }

    /** Unlocked while the close waits: popped as unlocked, with `exitFade`; an alert ringing cancels it (rule 8). */
    fun popIfUnlocked() {
        if (!pending || keyguard.locked.value) return
        if (alertKey.value != null) {
            cancel()
        } else {
            keyguard.setClosePending(false)
            navController.popBackStack<TimerRoute>(inclusive = true)
        }
    }
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

/** The re-read of the keyguard after a close over it, should nothing else tell of an unlock (≈ 1 s). */
private const val SAFETY_READ_MS = 1_000L

/** The cover that keeps the timer inert while it leaves over the keyguard (A-17). */
internal const val LEAVING_COVER_TAG = "leaving_cover"
