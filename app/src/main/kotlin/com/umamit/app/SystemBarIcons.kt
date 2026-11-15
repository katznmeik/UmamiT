package com.umamit.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.umamit.core.designsystem.motion.MotionTokens
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.delay

/**
 * Whether the status-bar icons and the gesture handle are dark (D-069). `enableEdgeToEdge`
 * picks them from the phone's theme, which is wrong on the always-dark timer (SCR-05) and quick
 * timer (SCR-07) in the light theme and on the light alert surface in the dark theme.
 *
 * - the timer on top: dark on the washi/matcha alert, light on the Steeping surface;
 * - the quick timer on top: light;
 * - the photo viewer on top (SCR-28): light, on its black;
 * - anything else: the app theme's own (dark icons in the light theme).
 */
internal fun systemBarDarkIcons(
    timerOnTop: Boolean,
    quickTimerOnTop: Boolean,
    alerting: Boolean,
    darkTheme: Boolean,
    photoViewerOnTop: Boolean = false,
) = when {
    timerOnTop -> alerting
    quickTimerOnTop || photoViewerOnTop -> false
    else -> !darkTheme
}

/**
 * Applies [darkIcons] to the window. The root is its only caller, so screens that overlap during a
 * route transition never set and restore the appearance in an interleaved order (D-069).
 */
@Composable
internal fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    // Also after a night switch in place (A-08, `configChanges="uiMode"`), whatever reset the appearance.
    val uiMode = LocalConfiguration.current.uiMode
    DisposableEffect(window, view, darkIcons, uiMode) {
        window?.let { WindowCompat.getInsetsController(it, view) }?.run {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        onDispose {}
    }
}

/**
 * The icons of [darkIcons], with the timer's own transitions flipping them at the fade's midpoint (D-117 build note
 * A-16, amends D-069's timing): when [timerShown] (from the back stack) changes, the icons keep what they showed until
 * 150 ms into `fade` on a push (setup → timer, or a screen above the timer) or 100 ms into `exitFade` on a pop (timer →
 * shell, or back to the timer); half the reduced fade under reduce motion. Every other change applies in its frame,
 * and the back stack stays the one owner; only the moment changes. Between two timer surfaces (quick timer → timer)
 * nothing changes to wait for.
 *
 * [immediate] flips at once, in that frame: an alert (`alertEnter`, rule 8), also one that comes while a flip waits,
 * and the timer opened from a notification, whose cover is dark at once.
 *
 * @param topEntry the back stack's top entry: one that was on top before came back by a pop (`exitFade`), a new one
 *   was pushed (`fade`).
 */
@Composable
internal fun timedDarkIcons(
    darkIcons: Boolean,
    timerShown: Boolean,
    topEntry: NavBackStackEntry?,
    immediate: Boolean,
): Boolean {
    val reduceMotion = UmamiTheme.motion.reduceMotion
    val timing = remember { IconTiming(timerShown, darkIcons) }
    // Decided in this composition from what the last applied one left, so the frame that sees the change already
    // holds the icons; the bookkeeping is written only once a composition is applied (SideEffect), never by one that
    // is discarded.
    // Always read, so the end of a wait recomposes whichever branch decided this composition.
    val running = timing.wait
    val wait = when {
        immediate -> null

        timerShown != timing.lastShown -> {
            val pop = topEntry != null && topEntry.id in timing.seen
            val fadeMillis = when {
                reduceMotion -> MotionTokens.REDUCED_FADE_MILLIS
                pop -> MotionTokens.EXIT_MILLIS
                else -> MotionTokens.STANDARD_MILLIS
            }
            IconWait(held = timing.lastIcons, delayMs = fadeMillis / 2L)
        }

        else -> running
    }
    val icons = wait?.held ?: darkIcons
    SideEffect {
        timing.lastShown = timerShown
        timing.lastIcons = icons
        timing.wait = wait
        timing.saw(topEntry)
    }
    LaunchedEffect(wait) {
        if (wait != null) {
            delay(wait.delayMs)
            if (timing.wait === wait) timing.wait = null
        }
    }
    return icons
}

/** A flip that waits: the icons [held] meanwhile, for [delayMs]. Compared by identity: each change is its own wait. */
private class IconWait(val held: Boolean, val delayMs: Long)

/**
 * What the last applied composition left: whether the timer was shown, the icons it returned and the wait running.
 * Written only in a `SideEffect`; [wait] is snapshot state, so the end of a wait recomposes.
 */
private class IconTiming(var lastShown: Boolean, var lastIcons: Boolean) {
    var wait: IconWait? by mutableStateOf(null)

    /** The entries that were on top and are still on the back stack: a pop returns to one of them. */
    val seen = mutableMapOf<String, Lifecycle>()

    fun saw(top: NavBackStackEntry?) {
        seen.values.removeAll { it.currentState == Lifecycle.State.DESTROYED }
        if (top != null) seen[top.id] = top.lifecycle
    }
}

/**
 * The root's icons for the [entry] on top (D-069, A-16). An [alert] and the notification's dark cover
 * ([requestPending]) flip them at once.
 */
@Composable
internal fun RootSystemBarIcons(
    entry: NavBackStackEntry?,
    timerShown: Boolean,
    alert: String?,
    requestPending: Boolean,
) {
    SystemBarIcons(
        timedDarkIcons(
            darkIcons = systemBarDarkIcons(
                timerOnTop = timerShown,
                quickTimerOnTop = entry?.destination?.hasRoute<QuickTimerRoute>() == true,
                photoViewerOnTop = entry?.destination?.hasRoute<PhotoViewerRoute>() == true,
                alerting = alert != null,
                darkTheme = UmamiTheme.isDark,
            ),
            timerShown = timerShown,
            topEntry = entry,
            immediate = alert != null || requestPending,
        ),
    )
}
