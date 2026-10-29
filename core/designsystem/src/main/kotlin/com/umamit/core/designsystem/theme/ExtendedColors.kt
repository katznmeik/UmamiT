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
) {
    /**
     * The leaf placeholder's colour for a tea of [styleKey] (08 §2 style keys) without a
     * photo: the journal's thumbnails and the timer's seal (03 §3.5, D-095).
     */
    fun forStyle(styleKey: String?): Color = when (styleKey) {
        "gyokuro" -> gyokuro
        "kabusecha" -> kabusecha
        "sencha", "tamaryokucha", "kamairicha" -> sencha
        "fukamushi_sencha" -> fukamushi
        "shincha" -> shincha
        "matcha" -> matcha
        "karigane", "bancha" -> kukichaKarigane
        "hojicha", "hojicha_powder" -> hojicha
        "genmaicha" -> genmaicha
        "longjing", "anji_bai_cha", "bi_luo_chun", "korean_green" -> chinaKoreaGreen
        else -> other
    }
}

/**
 * The alert surface (03 §3.6 `alertPulse`): it pulses between [washi] and
 * [matcha], and [content] stays readable on both (13.5:1 on washi, 4.55:1 on
 * matcha). Under reduce motion it is static [matcha]. The luminance swing
 * washi ↔ matcha is 2.97:1, so the pulse is strong, at 0.67 cycles per second.
 */
@Immutable
data class AlertColors(val washi: Color, val matcha: Color, val content: Color)

/**
 * Colour tokens Material 3 has no role for (03 §3.1, D-036), provided through
 * [LocalUmamiExtendedColors] and read with `UmamiTheme.extendedColors`. They are
 * the same in both schemes.
 */
@Immutable
data class UmamiExtendedColors(
    /**
     * Warm accent for prominent overtime, as a non-text mark only: as text it is
     * 1.97:1 on the alert's washi and 1.51:1 on its matcha (D-055).
     */
    val warmAccent: Color,
    val liquor: LiquorColors,
    val teaTypes: TeaTypeColors,
    /** The alert surface; the same in both schemes, because it must look the same from across the room. */
    val alert: AlertColors,
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
    alert = AlertColors(
        washi = Color(0xFFF3F0E9),
        matcha = Color(0xFF6B9845),
        content = Color(0xFF1F271B),
    ),
)

val LocalUmamiExtendedColors = staticCompositionLocalOf { DefaultExtendedColors }
