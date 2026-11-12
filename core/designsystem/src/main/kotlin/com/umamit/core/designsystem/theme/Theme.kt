package com.umamit.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.motion.UmamiMotion
import com.umamit.core.designsystem.motion.rememberSystemReduceMotion
import com.umamit.core.model.ThemeMode

/** Whether the current UmamiT scheme is the dark „Steeping" one. */
val LocalUmamiDarkTheme = staticCompositionLocalOf { false }

/**
 * The UmamiT theme (03 §3): „Washi" light or „Steeping" dark, Alegreya Sans
 * type, shapes, the extended colours and the motion specs.
 *
 * @param dynamicColor Material You colours; off by default (SET-06).
 * @param reduceMotion read from the system animator duration scale; tests pass
 *   a fixed value.
 */
@Composable
fun UmamiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = rememberSystemReduceMotion(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> SteepingColorScheme
        else -> WashiColorScheme
    }
    val motion = remember(reduceMotion) { UmamiMotion(reduceMotion) }
    CompositionLocalProvider(
        LocalUmamiDarkTheme provides darkTheme,
        LocalUmamiExtendedColors provides DefaultExtendedColors,
        LocalUmamiTextStyles provides UmamiTextStyles(),
        LocalReduceMotion provides reduceMotion,
        LocalUmamiMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UmamiTypography,
            shapes = UmamiShapes,
            content = content,
        )
    }
}

/** Whether [this] choice (SET-05) is the dark theme, given whether the phone is in its dark theme. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The app's theme for the stored *Design* choice (SET-05, D-114): [mode] decides, *System* follows the phone.
 * The timer surfaces keep [SteepingTheme] inside whatever this is.
 */
@Composable
fun UmamiAppTheme(
    mode: ThemeMode,
    systemDark: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = rememberSystemReduceMotion(),
    content: @Composable () -> Unit,
) {
    UmamiTheme(darkTheme = mode.isDark(systemDark), reduceMotion = reduceMotion, content = content)
}

/** The timer always steeps in the dark „Steeping" scheme, in both app themes (03 §3.1). */
@Composable
fun SteepingTheme(reduceMotion: Boolean = rememberSystemReduceMotion(), content: @Composable () -> Unit) {
    UmamiTheme(darkTheme = true, dynamicColor = false, reduceMotion = reduceMotion, content = content)
}

internal val LocalUmamiMotion = staticCompositionLocalOf { UmamiMotion(reduceMotion = false) }

/** Accessors for the UmamiT tokens Material 3 has no slot for. */
object UmamiTheme {
    val extendedColors: UmamiExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalUmamiExtendedColors.current

    val textStyles: UmamiTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalUmamiTextStyles.current

    val motion: UmamiMotion
        @Composable @ReadOnlyComposable
        get() = LocalUmamiMotion.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReduceMotion.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalUmamiDarkTheme.current
}
