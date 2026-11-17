package com.umamit.feature.brew.timer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.umamit.core.designsystem.theme.LocalUmamiDarkTheme
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * The app in [darkTheme] with the timer still drawn dark inside it, as before D-128 Q20: pins the existing
 * light-app timer goldens until #325 (v0.8.0-10) re-records them in the approved light look. Remove it there.
 */
@Composable
internal fun UmamiThemeWithDarkTimer(
    reduceMotion: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    UmamiTheme(darkTheme = darkTheme, reduceMotion = reduceMotion) {
        CompositionLocalProvider(LocalUmamiDarkTheme provides true, content = content)
    }
}
