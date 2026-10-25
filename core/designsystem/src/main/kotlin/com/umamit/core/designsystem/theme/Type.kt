package com.umamit.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.umamit.core.designsystem.R

/**
 * Cormorant Garamond (OFL, D-027), bundled as Light/Regular/Medium instances of
 * the Google Fonts variable font, subset to Latin with German characters, ẞ and
 * macrons. The subset keeps the `lnum` and `tnum` features for the timer digits
 * (licence and credits: `core/designsystem/licenses/`,
 * `docs/design/assets/source/CREDITS.md`).
 */
val CormorantGaramond = FontFamily(
    Font(R.font.cormorant_garamond_light, FontWeight.Light),
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
)

/** Body and labels use the system sans (03 §3.2). */
private val SystemSans = FontFamily.Default

/**
 * The UI is German whatever the device locale (CLAUDE.md rule 4): German line
 * breaking and automatic hyphenation keep long compounds („Temperatur",
 * „Wasserkocher") readable at font scale 2.0 instead of breaking mid-word.
 */
private val GERMAN = LocaleList("de-DE")

/** Lining, tabular figures: every digit has the same advance, so the timer never jitters (03 §3.2). */
const val TIMER_DIGIT_FEATURES = "lnum, tnum"

/**
 * The 03 §3.2 type scale mapped onto the Material 3 roles: serif display,
 * headline and the two larger title roles; system sans for the rest, which
 * Material components use for their labels.
 */
val UmamiTypography = Typography(
    displayLarge = serif(FontWeight.Light, size = 57, lineHeight = 64),
    displayMedium = serif(FontWeight.Light, size = 45, lineHeight = 52),
    displaySmall = serif(FontWeight.Light, size = 36, lineHeight = 44),
    headlineLarge = serif(FontWeight.Normal, size = 32, lineHeight = 40),
    // „Journal", „Sammlung": 28 / 36 sp.
    headlineMedium = serif(FontWeight.Normal, size = 28, lineHeight = 36),
    headlineSmall = serif(FontWeight.Normal, size = 24, lineHeight = 32),
    // Tea names: 20–22 / 28 sp.
    titleLarge = serif(FontWeight.Medium, size = 22, lineHeight = 28),
    titleMedium = serif(FontWeight.Medium, size = 20, lineHeight = 28),
    titleSmall = sans(FontWeight.Medium, size = 14, lineHeight = 20),
    bodyLarge = sans(FontWeight.Normal, size = 16, lineHeight = 24),
    bodyMedium = sans(FontWeight.Normal, size = 14, lineHeight = 20),
    bodySmall = sans(FontWeight.Normal, size = 12, lineHeight = 16),
    labelLarge = sans(FontWeight.Medium, size = 14, lineHeight = 20),
    labelMedium = sans(FontWeight.Medium, size = 12, lineHeight = 16),
    labelSmall = sans(FontWeight.Medium, size = 11, lineHeight = 16),
)

/** Text styles Material 3 has no role for, read with `UmamiTheme.textStyles`. */
@Immutable
data class UmamiTextStyles(
    /** Timer digits: Cormorant Garamond Light, lining tabular figures, 72–96 sp (03 §3.2). */
    val timerDigits: TextStyle = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Light,
        fontSize = 80.sp,
        lineHeight = 88.sp,
        fontFeatureSettings = TIMER_DIGIT_FEATURES,
    ),
    /**
     * Overline: system sans, uppercase, +0.12 em tracking, 11 / 16 sp. The text
     * itself is uppercased with `germanUppercase()` so ß becomes ẞ.
     */
    val overline: TextStyle = TextStyle(
        fontFamily = SystemSans,
        fontWeight = FontWeight.Medium,
        localeList = GERMAN,
        hyphens = Hyphens.Auto,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12.em,
    ),
)

val LocalUmamiTextStyles = staticCompositionLocalOf { UmamiTextStyles() }

private fun serif(weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(
    localeList = GERMAN,
    hyphens = Hyphens.Auto,
    fontFamily = CormorantGaramond,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // Old-style figures would bob in titles next to sans numbers; keep them lining.
    fontFeatureSettings = "lnum",
)

private fun sans(weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(
    localeList = GERMAN,
    hyphens = Hyphens.Auto,
    fontFamily = SystemSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)
