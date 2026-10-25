package com.umamit.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Every text/background token pair the components use is ≥ 4.5:1 in both
 * schemes (03 §6). A pair that is not in this list must not be used for text:
 * `onSurfaceVariant` on `surfaceContainerHigh` is only 4.21:1 in the light scheme.
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

    @Test
    fun `alert content is readable at both ends of the pulse`() {
        val alert = DefaultExtendedColors.alert
        assertWithMessage("ink on washi").that(contrast(alert.content, alert.washi)).isAtLeast(TEXT_CONTRAST)
        assertWithMessage("ink on matcha").that(contrast(alert.content, alert.matcha)).isAtLeast(TEXT_CONTRAST)
    }
}
