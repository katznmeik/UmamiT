package com.umamit.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
 * @param topEntryId the id of the back stack's top entry: one that was on top before came back by a pop (`exitFade`),
 *   a new one was pushed (`fade`).
 */
@Composable
internal fun timedDarkIcons(
    darkIcons: Boolean,
    timerShown: Boolean,
    topEntryId: String?,
    immediate: Boolean,
): Boolean {
    val reduceMotion = UmamiTheme.motion.reduceMotion
    val timing = remember { IconTiming(timerShown, darkIcons) }
    // Read here, so the end of a wait recomposes; the timing itself is plain state of this composition.
    var settled by remember { mutableIntStateOf(0) }
    timing.observe(timerShown, topEntryId, reduceMotion, settled)
    if (immediate) timing.pending = false
    val icons = if (timing.pending) timing.held else darkIcons
    timing.lastIcons = icons
    LaunchedEffect(timing.changes) {
        if (timing.pending) {
            delay(timing.delayMs)
            timing.pending = false
            settled++
        }
    }
    return icons
}

/** Whether a flip waits, what it holds meanwhile, and how long: from whether the timer's change was a push or a pop. */
private class IconTiming(private var lastShown: Boolean, var lastIcons: Boolean) {
    /** The entries that were on top so far: a pop returns to one of them. */
    private val seen = mutableSetOf<String>()

    var delayMs: Long = 0L
        private set
    var changes: Int = 0
        private set
    var pending: Boolean = false
    private var lastSettled: Int = 0
    var held: Boolean = lastIcons
        private set

    /** [settled] counts the waits that ended; read by the caller so their end recomposes. */
    fun observe(timerShown: Boolean, topEntryId: String?, reduceMotion: Boolean, settled: Int) {
        lastSettled = settled
        if (timerShown != lastShown) {
            val pop = topEntryId != null && topEntryId in seen
            val fadeMillis = when {
                reduceMotion -> MotionTokens.REDUCED_FADE_MILLIS
                pop -> MotionTokens.EXIT_MILLIS
                else -> MotionTokens.STANDARD_MILLIS
            }
            delayMs = fadeMillis / 2L
            held = lastIcons
            pending = true
            changes++
            lastShown = timerShown
        }
        topEntryId?.let(seen::add)
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
            topEntryId = entry?.id,
            immediate = alert != null || requestPending,
        ),
    )
}
