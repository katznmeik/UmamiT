package com.umamit.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Every text/background token pair the components use is ≥ 4.5:1 in both
 * schemes (03 §6). A pair that is not in this list must not be used for text:
 * `onSurfaceVariant` on `surfaceContainerHigh` is only 4.21:1 in the light scheme. The light timer's text and
 * controls (D-128 Q20) are checked below, with every value under a threshold documented for the design check.
 */
class TokenContrastTest {

    private class TextPair(val name: String, val text: (ColorScheme) -> Color, val background: (ColorScheme) -> Color)

    private val schemes = mapOf("Washi" to WashiColorScheme, "Steeping" to SteepingColorScheme)

    private val textPairs = listOf(
        TextPair("onSurface on background", { it.onSurface }, { it.background }),
        TextPair("onSurface on surface", { it.onSurface }, { it.surface }),
        TextPair("onSurface on surfaceContainerLow", { it.onSurface }, { it.surfaceContainerLow }),
        // ActiveBrewBar (title and step), ReliabilityBanner
        TextPair("onSurface on surfaceContainerHigh", { it.onSurface }, { it.surfaceContainerHigh }),
        // EmptyState, SectionHeader, TeaListItem, StatTriplet, KettleHint
        TextPair("onSurfaceVariant on background", { it.onSurfaceVariant }, { it.background }),
        TextPair("onSurfaceVariant on surface", { it.onSurfaceVariant }, { it.surface }),
        // Schritte-Sheet
        TextPair("onSurfaceVariant on surfaceContainerLow", { it.onSurfaceVariant }, { it.surfaceContainerLow }),
        TextPair("primary on surfaceContainerLow", { it.primary }, { it.surfaceContainerLow }),
        // ReliabilityBanner action
        TextPair("primary on surfaceContainerHigh", { it.primary }, { it.surfaceContainerHigh }),
        // PrimaryActionCard, PillButton (light)
        TextPair("onPrimary on primary", { it.onPrimary }, { it.primary }),
        // PillButton (dark)
        TextPair("onSecondaryContainer on secondaryContainer", { it.onSecondaryContainer }, { it.secondaryContainer }),
    )

    @Test
    fun `every text token pair is at least 4,5 to 1 in both schemes`() {
        schemes.forEach { (schemeName, scheme) ->
            textPairs.forEach { pair ->
                assertWithMessage("$schemeName: ${pair.name}")
                    .that(contrast(pair.text(scheme), pair.background(scheme)))
                    .isAtLeast(TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `secondary text on surfaceContainerHigh fails in the light scheme, so components do not use it`() {
        assertWithMessage("documents why the active brew bar's step uses onSurface")
            .that(contrast(WashiColorScheme.onSurfaceVariant, WashiColorScheme.surfaceContainerHigh))
            .isLessThan(TEXT_CONTRAST)
    }

    // --- The light timer (D-128 Q20, 03 §3.1 „Timer in the light theme", NFR-A11Y-02) -----------------------------
    // The light timer is the Washi scheme; these are the pairs its text and controls use, the pre-alert included.
    // Values below the thresholds are documented with their ratio for the PO's design check (#323, #324).

    private val washi = WashiColorScheme

    /** [this] at [alpha] over the opaque [background], as the screen composites it. */
    private fun Color.over(background: Color, alpha: Float = this.alpha): Color = Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )

    @Test
    fun `light timer text is at least 4,5 to 1`() {
        val background = washi.background
        val pairs = mapOf(
            "digits, title, caption (onSurface on washi)" to contrast(washi.onSurface, background),
            "secondary text (onSurfaceVariant on washi)" to contrast(washi.onSurfaceVariant, background),
            "caption on today's liquor fill" to contrast(washi.onSurface, LIQUOR_GREEN.over(background, LIQUOR_ALPHA)),
            "caption on the deeper fill (tertiary)" to
                contrast(washi.onSurface, washi.tertiary.over(background, LIQUOR_ALPHA)),
            // Pre-alert: „Gleich fertig …" in the caption colour on the liquor, as above; the digits unchanged.
            "pill label (onPrimary on primary)" to contrast(washi.onPrimary, washi.primary),
            "sheet text (onSurface on surface)" to contrast(washi.onSurface, washi.surface),
            "sheet secondary text (onSurfaceVariant on surface)" to contrast(washi.onSurfaceVariant, washi.surface),
            "← and ⋮ icon on surface at 60 % over washi" to
                contrast(washi.onSurface, washi.surface.over(background, ICON_CIRCLE_ALPHA)),
        )
        pairs.forEach { (name, ratio) -> assertWithMessage("light timer: $name").that(ratio).isAtLeast(TEXT_CONTRAST) }
    }

    @Test
    fun `light timer controls and the pre-alert are at least 3 to 1`() {
        val background = washi.background
        val pairs = mapOf(
            "ring progress (primary on washi)" to contrast(washi.primary, background),
            "pill (primary on washi)" to contrast(washi.primary, background),
            "pre-alert glow (primary on washi)" to contrast(washi.primary, background),
            "pre-alert tick (onSurfaceVariant on washi)" to contrast(washi.onSurfaceVariant, background),
            "quick timer chip border (onSurfaceVariant on washi)" to contrast(washi.onSurfaceVariant, background),
            "← and ⋮ icon over the darkest ink of the band" to
                contrast(washi.onSurface, washi.surface.over(washi.onSurface, ICON_CIRCLE_ALPHA)),
        )
        pairs.forEach { (name, ratio) -> assertWithMessage("light timer: $name").that(ratio).isAtLeast(UI_CONTRAST) }
    }

    @Test
    fun `light timer values below the thresholds are documented for the design check`() {
        val background = washi.background
        // Decorative (03 §3.1): the progress carries the information.
        assertRatio("ring track (outline on washi)", contrast(washi.outline, background), 1.30)
        // Decoration: the ring and the digits carry the state.
        assertRatio(
            "today's liquor fill on washi",
            contrast(LIQUOR_GREEN.over(background, LIQUOR_ALPHA), background),
            1.11,
        )
        assertRatio(
            "the deeper fill (tertiary) on washi",
            contrast(washi.tertiary.over(background, LIQUOR_ALPHA), background),
            1.40,
        )
        // PAUSED dims the ring on purpose (D-058); digits and caption stay at full contrast.
        assertRatio("paused ring (primary at 60 %)", contrast(washi.primary.over(background, 0.6f), background), 2.74)
        // The circle under ← and ⋮ barely shows on washi; the icon is the control (12.6:1).
        assertRatio(
            "← and ⋮ circle (surface at 60 %) against washi",
            contrast(washi.surface.over(background, ICON_CIRCLE_ALPHA), background),
            1.03,
        )
        // Why the light quick timer outlines its chips in full onSurfaceVariant, not at the dark timer's 60 %.
        assertRatio(
            "chip border at 60 % (the dark timer's value)",
            contrast(washi.onSurfaceVariant.over(background, 0.6f), background),
            2.31,
        )
        // The selected chip (Material's secondaryContainer): its label and the selected state carry it.
        assertRatio("selected chip (secondaryContainer) on washi", contrast(washi.secondaryContainer, background), 1.10)
        // The seal: a washi disc on washi, set off by its ring and a soft shadow (03 §3.1 „Seal").
        assertRatio(
            "seal ring (alert ink at 30 %) on washi",
            contrast(DefaultExtendedColors.alert.content.over(background, 0.3f), background),
            1.84,
        )
    }

    private fun assertRatio(name: String, ratio: Double, expected: Double) {
        assertWithMessage("light timer: $name").that(ratio).isWithin(RATIO_TOLERANCE).of(expected)
    }

    private companion object {
        val LIQUOR_GREEN = DefaultExtendedColors.liquor.green

        /** TimerRing's fill opacity. */
        const val LIQUOR_ALPHA = 0.32f

        /** The circle under the timer's ← and ⋮ (feature:brew `TimerTopBar`). */
        const val ICON_CIRCLE_ALPHA = 0.6f
        const val RATIO_TOLERANCE = 0.01
    }

    @Test
    fun `alert content is readable at both ends of the pulse`() {
        val alert = DefaultExtendedColors.alert
        assertWithMessage("ink on washi").that(contrast(alert.content, alert.washi)).isAtLeast(TEXT_CONTRAST)
        assertWithMessage("ink on matcha").that(contrast(alert.content, alert.matcha)).isAtLeast(TEXT_CONTRAST)
    }

    /**
     * The light timer's sage alarm (D-128 item 36) meets D-055's thresholds in both pulse phases: ink text ≥ 4.5:1,
     * the ink pill and ink circles ≥ 3:1 against the surface, their washi label and icons ≥ 4.5:1 on ink.
     */
    @Test
    fun `the light timer's sage alarm is readable at both ends of the pulse`() {
        val alert = LightTimerAlertColors
        assertThat(alert.matcha).isEqualTo(Color(0xFFAEBF8D))
        assertThat(alert.washi).isEqualTo(DefaultExtendedColors.alert.washi)
        assertThat(alert.content).isEqualTo(Color(0xFF1F271B))
        mapOf("washi" to alert.washi, "sage" to alert.matcha).forEach { (phase, surface) ->
            assertWithMessage("ink text on $phase").that(contrast(alert.content, surface)).isAtLeast(TEXT_CONTRAST)
            assertWithMessage("ink pill and circles on $phase").that(contrast(alert.content, surface))
                .isAtLeast(UI_CONTRAST)
        }
        assertWithMessage("washi label and icons on ink").that(contrast(alert.washi, alert.content))
            .isAtLeast(TEXT_CONTRAST)
        assertWithMessage("ink on sage (03 §3.1)").that(contrast(alert.content, alert.matcha)).isWithin(0.05).of(7.8)
    }

    @Test
    fun `the light timer's pulse swings 1,73 to 1, the dark timer's stays washi to matcha`() {
        assertWithMessage("washi ↔ sage").that(contrast(LightTimerAlertColors.washi, LightTimerAlertColors.matcha))
            .isWithin(RATIO_TOLERANCE).of(1.73)
        assertWithMessage("the dark timer and the app keep matcha").that(DefaultExtendedColors.alert.matcha)
            .isEqualTo(Color(0xFF6B9845))
    }
}
