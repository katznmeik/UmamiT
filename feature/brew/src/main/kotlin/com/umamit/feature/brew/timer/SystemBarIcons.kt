package com.umamit.feature.brew.timer

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The status-bar and gesture-handle icons over a screen with its own fixed
 * background (03 §3.8, #54 S1): `enableEdgeToEdge` picks them from the phone's
 * theme, which is wrong on the always-dark timer (SCR-05, SCR-07) in the light
 * theme and on the light alert surface in the dark theme.
 *
 * @param darkIcons dark icons, for a light background (the washi/matcha alert);
 *   light icons for the dark Steeping scheme.
 *
 * The appearance from before the screen is restored when it leaves.
 */
@Composable
internal fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        onDispose {
            if (controller != null && status != null && navigation != null) {
                controller.isAppearanceLightStatusBars = status
                controller.isAppearanceLightNavigationBars = navigation
            }
        }
    }
    // After the effect above has kept the previous appearance; decided in the alert's first frame.
    DisposableEffect(window, view, darkIcons) {
        window?.let { WindowCompat.getInsetsController(it, view) }?.run {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        onDispose {}
    }
}
