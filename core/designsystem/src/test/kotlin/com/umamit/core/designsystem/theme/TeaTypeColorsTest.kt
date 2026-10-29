package com.umamit.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The leaf placeholder's colour per tea style (03 §3.1 tea-type colours, 08 §2 style keys). */
class TeaTypeColorsTest {

    private val colors = DefaultExtendedColors.teaTypes

    @Test
    fun `each style key has its type colour`() {
        val expected = mapOf(
            "gyokuro" to Color(0xFF5F7F45),
            "kabusecha" to Color(0xFF6C8A4F),
            "sencha" to Color(0xFF7C9A5B),
            "kamairicha" to Color(0xFF7C9A5B),
            "fukamushi_sencha" to Color(0xFF8AA463),
            "shincha" to Color(0xFF9DB86E),
            "matcha" to Color(0xFF5F8C3E),
            "bancha" to Color(0xFFA6B07A),
            "hojicha_powder" to Color(0xFF8C6A48),
            "genmaicha" to Color(0xFFB59A62),
            "longjing" to Color(0xFF8FA77A),
        )
        expected.forEach { (style, color) -> assertThat(colors.forStyle(style)).isEqualTo(color) }
    }

    @Test
    fun `no style or an unknown one is the neutral colour`() {
        assertThat(colors.forStyle(null)).isEqualTo(Color(0xFF8C8C80))
        assertThat(colors.forStyle("rooibos")).isEqualTo(Color(0xFF8C8C80))
    }
}
