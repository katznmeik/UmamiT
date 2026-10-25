package com.umamit.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.x contrast ratio of two opaque colours, written out from the
 * definition (relative luminance of sRGB), so the tests do not reuse the code
 * they check.
 */
internal fun contrast(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun Color.relativeLuminance(): Double {
    fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/** WCAG AA for body text (03 §6). */
internal const val TEXT_CONTRAST = 4.5

/** WCAG AA for controls and other non-text UI. */
internal const val UI_CONTRAST = 3.0
