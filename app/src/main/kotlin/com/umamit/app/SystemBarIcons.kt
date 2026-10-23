package com.umamit.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Whether the status-bar icons and the gesture handle are dark (D-069). `enableEdgeToEdge`
 * picks them from the phone's theme, which is wrong on the always-dark timer (SCR-05) and quick
 * timer (SCR-07) in the light theme and on the light alert surface in the dark theme.
 *
 * - the timer on top: dark on the washi/matcha alert, light on the Steeping surface;
 * - the quick timer on top: light;
 * - anything else: the app theme's own (dark icons in the light theme).
 */
internal fun systemBarDarkIcons(timerOnTop: Boolean, quickTimerOnTop: Boolean, alerting: Boolean, darkTheme: Boolean) =
    when {
        timerOnTop -> alerting
        quickTimerOnTop -> false
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
    DisposableEffect(window, view, darkIcons) {
        window?.let { WindowCompat.getInsetsController(it, view) }?.run {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        onDispose {}
    }
}
