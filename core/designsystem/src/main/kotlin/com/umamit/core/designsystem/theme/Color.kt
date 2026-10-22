package com.umamit.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Colour schemes (03 §3.1, D-036).
 *
 * How they were made: the full Material 3 light and dark schemes were generated
 * once from the seed colour #4A5643 with the material-color-utilities algorithm
 * (Python port `materialyoucolor` 3.0.4: `SchemeTonalSpot`, contrast 0.0, spec
 * version "2021", the variant Material Theme Builder and Compose's defaults
 * use). The values are committed here as constants; nothing generates colours
 * at runtime. Each role is marked:
 *
 * - `generated`: the generator's value, unchanged;
 * - `03 §3.1`: the spec's override, with the generated value it replaced;
 * - `follows`: a twin role that follows an override, so a pair never disagrees
 *   (`onBackground` = `onSurface`, `surfaceTint` = `primary`).
 *
 * To regenerate, run the same algorithm on the seed and replace the `generated`
 * values; the overrides stay.
 */

/** Light „Washi" scheme (03 §3.1). */
val WashiColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4A5643), // 03 §3.1 (generated 0xFF426833)
    onPrimary = Color(0xFFF6F3EC), // 03 §3.1 (generated 0xFFFFFFFF)
    primaryContainer = Color(0xFFDDE2D2), // 03 §3.1 (generated 0xFFC3EFAD)
    onPrimaryContainer = Color(0xFF1F271B), // 03 §3.1 (generated 0xFF2B4F1E)
    inversePrimary = Color(0xFFA8D293), // generated
    secondary = Color(0xFF8A7B5E), // 03 §3.1 (generated 0xFF54624C)
    onSecondary = Color(0xFFFFFFFF), // generated
    secondaryContainer = Color(0xFFECE5D6), // 03 §3.1 (generated 0xFFD8E7CC)
    onSecondaryContainer = Color(0xFF3D4B36), // generated
    tertiary = Color(0xFF6F8A5A), // 03 §3.1 (generated 0xFF386667)
    onTertiary = Color(0xFFFFFFFF), // generated
    tertiaryContainer = Color(0xFFBCEBED), // generated
    onTertiaryContainer = Color(0xFF1E4E4F), // generated
    background = Color(0xFFF3F0E9), // 03 §3.1 (generated 0xFFF8FAF0)
    onBackground = Color(0xFF2B2D27), // follows onSurface (generated 0xFF191D17)
    surface = Color(0xFFF7F5F0), // 03 §3.1 (generated 0xFFF8FAF0)
    onSurface = Color(0xFF2B2D27), // 03 §3.1 (generated 0xFF191D17)
    surfaceVariant = Color(0xFFDFE4D7), // generated
    onSurfaceVariant = Color(0xFF6B6A62), // 03 §3.1 (generated 0xFF43483F)
    surfaceTint = Color(0xFF4A5643), // follows primary (generated 0xFF426833)
    inverseSurface = Color(0xFF2E312B), // generated
    inverseOnSurface = Color(0xFFEFF2E8), // generated
    error = Color(0xFF9E3B2F), // 03 §3.1 (generated 0xFFBA1A1A)
    onError = Color(0xFFFFFFFF), // generated
    errorContainer = Color(0xFFFFDAD6), // generated
    onErrorContainer = Color(0xFF93000A), // generated
    outline = Color(0xFFD9D4C8), // 03 §3.1 (generated 0xFF73796E)
    outlineVariant = Color(0xFFC3C8BB), // generated
    scrim = Color(0xFF000000), // generated
    surfaceBright = Color(0xFFF8FAF0), // generated
    surfaceDim = Color(0xFFD8DBD1), // generated
    surfaceContainer = Color(0xFFECEFE5), // generated
    surfaceContainerHigh = Color(0xFFE7E2D8), // 03 §3.1 (generated 0xFFE7E9DF)
    surfaceContainerHighest = Color(0xFFE1E4DA), // generated
    surfaceContainerLow = Color(0xFFEFEBE3), // 03 §3.1 (generated 0xFFF2F5EA)
    surfaceContainerLowest = Color(0xFFFFFFFF), // generated
)

/** Dark „Steeping" scheme: the dark theme, and the timer in both themes (03 §3.1). */
val SteepingColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9C8A6), // 03 §3.1 (generated 0xFFA8D293)
    onPrimary = Color(0xFF1F271B), // 03 §3.1 (generated 0xFF153809)
    primaryContainer = Color(0xFF2B4F1E), // generated
    onPrimaryContainer = Color(0xFFC3EFAD), // generated
    inversePrimary = Color(0xFF426833), // generated
    secondary = Color(0xFFBCCBB0), // generated
    onSecondary = Color(0xFF273421), // generated
    secondaryContainer = Color(0xFFE5DFCF), // 03 §3.1 (generated 0xFF3D4B36)
    onSecondaryContainer = Color(0xFF2B2D27), // 03 §3.1 (generated 0xFFD8E7CC)
    tertiary = Color(0xFFA0CFD1), // generated
    onTertiary = Color(0xFF003738), // generated
    tertiaryContainer = Color(0xFF1E4E4F), // generated
    onTertiaryContainer = Color(0xFFBCEBED), // generated
    background = Color(0xFF1C231D), // 03 §3.1 (generated 0xFF11140F)
    onBackground = Color(0xFFECE8DE), // follows onSurface (generated 0xFFE1E4DA)
    surface = Color(0xFF232B24), // 03 §3.1 (generated 0xFF11140F)
    onSurface = Color(0xFFECE8DE), // 03 §3.1 (generated 0xFFE1E4DA)
    surfaceVariant = Color(0xFF43483F), // generated
    onSurfaceVariant = Color(0xFFA9A99D), // 03 §3.1 (generated 0xFFC3C8BB)
    surfaceTint = Color(0xFFB9C8A6), // follows primary (generated 0xFFA8D293)
    inverseSurface = Color(0xFFE1E4DA), // generated
    inverseOnSurface = Color(0xFF2E312B), // generated
    error = Color(0xFFFFB4AB), // generated
    onError = Color(0xFF690005), // generated
    errorContainer = Color(0xFF93000A), // generated
    onErrorContainer = Color(0xFFFFDAD6), // generated
    outline = Color(0xFF4A544A), // 03 §3.1 (generated 0xFF8D9387)
    outlineVariant = Color(0xFF43483F), // generated
    scrim = Color(0xFF000000), // generated
    surfaceBright = Color(0xFF373A33), // generated
    surfaceDim = Color(0xFF11140F), // generated
    surfaceContainer = Color(0xFF1D211A), // generated
    surfaceContainerHigh = Color(0xFF2E372F), // 03 §3.1 (generated 0xFF272B25)
    surfaceContainerHighest = Color(0xFF32362F), // generated
    surfaceContainerLow = Color(0xFF191D17), // generated
    surfaceContainerLowest = Color(0xFF0C0F0A), // generated
)
