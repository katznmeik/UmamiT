package com.umamit.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
 * Whether the icons treat the timer as on top (D-117 build note A-16, amends D-069's timing): [timerShown] from the back
 * stack, but a change waits for the midpoint of the route's fade, so the icons flip when the dark timer is half in or
 * half out: 150 ms into `fade` when a route is pushed (setup → timer, or a screen above the timer), 100 ms into
 * `exitFade` when one is popped (timer → shell, or back to the timer); half those fades under reduce motion. The back
 * stack stays the one owner; only the moment changes.
 *
 * [immediate] flips at once, in that frame: an alert (`alertEnter`, rule 8), also one that comes while a flip waits,
 * and the timer opened from a notification, whose cover is dark at once.
 *
 * @param topEntryId the id of the back stack's top entry; [backStackIds] the ids of the whole back stack. A top entry
 *   that was in the back stack before the change came back by a pop; a new one was pushed.
 */
@Composable
internal fun timerShownForIcons(
    timerShown: Boolean,
    topEntryId: String?,
    backStackIds: List<String>,
    immediate: Boolean,
): Boolean {
    val motion = UmamiTheme.motion
    val timing = remember { IconTiming(timerShown, backStackIds) }
    timing.observe(timerShown, topEntryId, backStackIds, motion.reduceMotion)
    var shown by remember { mutableStateOf(timerShown) }
    LaunchedEffect(timerShown, immediate) {
        if (!immediate && shown != timerShown) delay(timing.delayMs)
        shown = timerShown
    }
    return if (immediate) timerShown else shown
}

/** The delay of the last change of the timer's place, from whether that change was a push or a pop. */
private class IconTiming(private var lastShown: Boolean, private var lastIds: List<String>) {
    var delayMs: Long = 0L
        private set

    fun observe(timerShown: Boolean, topEntryId: String?, ids: List<String>, reduceMotion: Boolean) {
        if (timerShown != lastShown) {
            val pop = topEntryId != null && topEntryId in lastIds
            val fadeMillis = when {
                reduceMotion -> MotionTokens.REDUCED_FADE_MILLIS
                pop -> MotionTokens.EXIT_MILLIS
                else -> MotionTokens.STANDARD_MILLIS
            }
            delayMs = fadeMillis / 2L
            lastShown = timerShown
        }
        lastIds = ids
    }
}
