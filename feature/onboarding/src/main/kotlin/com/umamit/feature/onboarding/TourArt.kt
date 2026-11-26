package com.umamit.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.onboarding.howto.HowToTopic
import com.umamit.feature.onboarding.howto.HowToTopicScreen
import com.umamit.feature.onboarding.mockup.HelpMockup
import com.umamit.feature.onboarding.mockup.HelpMockupId
import com.umamit.feature.onboarding.mockup.HelpMockupSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The illustration of a tour page: the existing asset as its own screen shows it. */
internal enum class TourArt { HomeHeader, TimerBand, Catalog, Journal, Stats, Reliability }

@Composable
internal fun TourArtImage(art: TourArt) {
    val dark = UmamiTheme.isDark
    when (art) {
        TourArt.HomeHeader -> BandImage(
            res = if (dark) UmamiIcons.HomeHeaderDark else UmamiIcons.HomeHeaderLight,
            ratio = HOME_HEADER_RATIO,
            bandHeight = TOUR_BAND_HEIGHT,
        )

        TourArt.TimerBand -> TimerBandImage(bandHeight = TOUR_BAND_HEIGHT)

        TourArt.Catalog -> VignetteImage(if (dark) UmamiIcons.CatalogEmptyDark else UmamiIcons.CatalogEmptyLight)

        TourArt.Journal -> VignetteImage(if (dark) UmamiIcons.JournalEmptyDark else UmamiIcons.JournalEmptyLight)

        TourArt.Stats -> VignetteImage(if (dark) UmamiIcons.StatsEmptyDark else UmamiIcons.StatsEmptyLight)

        TourArt.Reliability -> VignetteImage(if (dark) UmamiIcons.ReliabilityDark else UmamiIcons.ReliabilityLight)
    }
}

/** A band's size: a fixed [height] on the tour (SCR-33), else its aspect [ratio]. */
private fun Modifier.bandSize(height: Dp?, ratio: Float): Modifier =
    if (height != null) height(height) else aspectRatio(ratio)

/** A3 as on Start: edge to edge, cropped to its band, the paper matched to the theme (no text on it). */
@Composable
internal fun BandImage(@DrawableRes res: Int, ratio: Float, modifier: Modifier = Modifier, bandHeight: Dp? = null) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.fillMaxWidth().bandSize(bandHeight, ratio),
        contentScale = ContentScale.Crop,
    )
}

/** A6, A8, A9 and A5 as their empty states and the reliability page show them: a small motif on matched paper. */
@Composable
private fun VignetteImage(@DrawableRes res: Int) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(TOUR_BAND_HEIGHT),
        contentScale = ContentScale.Fit,
    )
}

/**
 * A13 as the timer's band: centre-cropped, fading into the background at its lower edge. The painting is the
 * night one, so on the light theme it becomes ink on washi through a colour matrix (03 §3.1), as on the timer.
 */
@Composable
internal fun TimerBandImage(modifier: Modifier = Modifier, bandHeight: Dp? = null) {
    val background = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    val filter = if (UmamiTheme.isDark) null else remember(background, ink) { inkOnWashi(background, ink) }
    Image(
        painter = painterResource(UmamiIcons.TimerBand),
        contentDescription = null,
        modifier = modifier.fillMaxWidth().bandSize(bandHeight, TIMER_BAND_RATIO)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(BAND_FADE_START to Color.Transparent, 1f to background))
            },
        contentScale = ContentScale.Crop,
        colorFilter = filter,
    )
}

/** A13's dark paper (luma ≈ 30) becomes [paper], its brightest bamboo (≈ 140) most of the way to [ink]. */
private fun inkOnWashi(paper: Color, ink: Color): ColorFilter {
    val scale = BAND_INK_STRENGTH / (BAND_LEAF_LUMA - BAND_PAPER_LUMA)

    fun row(paperChannel: Float, inkChannel: Float): FloatArray {
        val k = scale * (inkChannel - paperChannel) * CHANNEL_MAX
        return floatArrayOf(k * LUMA_R, k * LUMA_G, k * LUMA_B, 0f, paperChannel * CHANNEL_MAX - k * BAND_PAPER_LUMA)
    }
    val matrix = row(paper.red, ink.red) + row(paper.green, ink.green) + row(paper.blue, ink.blue) +
        floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

/** A3's band as on Start: 3.43 : 1 (1536 × 448). */
internal const val HOME_HEADER_RATIO = 3.43f

/** A13's band, a little taller than A3's: 2 : 1 of the 1536 × 822 painting, centre-cropped. */
private const val TIMER_BAND_RATIO = 2f

/** The illustration band of a page (SCR-33): about 96 dp. */
internal val TOUR_BAND_HEIGHT = 96.dp

private const val BAND_FADE_START = 0.35f

private const val BAND_PAPER_LUMA = 30f

private const val BAND_LEAF_LUMA = 140f

private const val BAND_INK_STRENGTH = 0.85f

private const val LUMA_R = 0.299f

private const val LUMA_G = 0.587f

private const val LUMA_B = 0.114f

private const val CHANNEL_MAX = 255f
