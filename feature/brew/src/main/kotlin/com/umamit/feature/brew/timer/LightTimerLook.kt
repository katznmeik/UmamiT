package com.umamit.feature.brew.timer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.umamit.core.designsystem.theme.UmamiTheme

/*
 * The two rows of 03 §3.1 „Timer in the light theme" that the PO's design check settles (D-128 Q23, #324): the
 * ink-wash band A13 and the liquor fill. Each has the alternatives the check renders side by side; the defaults
 * are the starting values. Only the light timer reads them: the dark timer is drawn as before.
 */

/** The band A13 on the light timer. */
internal enum class LightTimerBand {
    /** A13 turned into ink on washi: its dark paper becomes the background, the bamboo dark ink. */
    Ink,

    /** No picture: a plain washi top, the band's space kept. */
    Plain,
}

/** The liquor fill on the light timer. */
internal enum class LightTimerLiquor {
    /** As today: the green liquor (`liquor.green` #C9D58F), 1.1:1 on washi at the fill's opacity. */
    Today,

    /** Deeper: `tertiary` #6F8A5A of the Washi scheme, 1.4:1 on washi at the fill's opacity; ink stays 8.7:1 on it. */
    Deeper,
}

/** The light timer's band; the design check's renders set it (#323). */
internal val LocalLightTimerBand = staticCompositionLocalOf { LightTimerBand.Ink }

/** The light timer's liquor fill; the design check's renders set it (#323). */
internal val LocalLightTimerLiquor = staticCompositionLocalOf { LightTimerLiquor.Today }

/** The liquor colour of the timer's ring: the green liquor, in the light timer per [LocalLightTimerLiquor]. */
internal val timerLiquorColor: Color
    @Composable @ReadOnlyComposable
    get() = if (!UmamiTheme.isDark && LocalLightTimerLiquor.current == LightTimerLiquor.Deeper) {
        MaterialTheme.colorScheme.tertiary
    } else {
        UmamiTheme.extendedColors.liquor.green
    }
