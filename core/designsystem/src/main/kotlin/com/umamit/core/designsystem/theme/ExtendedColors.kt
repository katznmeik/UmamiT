package com.umamit.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Liquor colours for the timer's fill animation (03 §3.1, §3.6 signature moment 1). */
@Immutable
data class LiquorColors(val green: Color, val gyokuro: Color, val hojicha: Color)

/** Tea type colours for chips and chart series; always shown with a label (03 §3.1). */
@Immutable
data class TeaTypeColors(
    val gyokuro: Color,
    val kabusecha: Color,
    val sencha: Color,
    val fukamushi: Color,
    val shincha: Color,
    val matcha: Color,
    val kukichaKarigane: Color,
    val hojicha: Color,
    val genmaicha: Color,
    val chinaKoreaGreen: Color,
    val flavoured: Color,
    val other: Color,
)

/**
 * Colour tokens Material 3 has no role for (03 §3.1, D-036), provided through
 * [LocalUmamiExtendedColors] and read with `UmamiTheme.extendedColors`. They are
 * the same in both schemes.
 */
@Immutable
data class UmamiExtendedColors(
    /** Warm accent for prominent overtime. */
    val warmAccent: Color,
    val liquor: LiquorColors,
    val teaTypes: TeaTypeColors,
)

/** The 03 §3.1 values. */
val DefaultExtendedColors = UmamiExtendedColors(
    warmAccent = Color(0xFFD9A35F),
    liquor = LiquorColors(
        green = Color(0xFFC9D58F),
        gyokuro = Color(0xFFD7DF9F),
        hojicha = Color(0xFFB07A45),
    ),
    teaTypes = TeaTypeColors(
        gyokuro = Color(0xFF5F7F45),
        kabusecha = Color(0xFF6C8A4F),
        sencha = Color(0xFF7C9A5B),
        fukamushi = Color(0xFF8AA463),
        shincha = Color(0xFF9DB86E),
        matcha = Color(0xFF5F8C3E),
        kukichaKarigane = Color(0xFFA6B07A),
        hojicha = Color(0xFF8C6A48),
        genmaicha = Color(0xFFB59A62),
        chinaKoreaGreen = Color(0xFF8FA77A),
        flavoured = Color(0xFF9A7A9A),
        other = Color(0xFF8C8C80),
    ),
)

val LocalUmamiExtendedColors = staticCompositionLocalOf { DefaultExtendedColors }
