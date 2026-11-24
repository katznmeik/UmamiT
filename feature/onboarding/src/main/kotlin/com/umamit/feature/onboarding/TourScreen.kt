package com.umamit.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.launch

/** The number of tour pages (SCR-33). */
const val TOUR_PAGE_COUNT = 6

/** The illustration of a tour page: the existing asset as its own screen shows it. */
private enum class TourArt { HomeHeader, TimerBand, Catalog, Journal, Stats, Reliability }

private class TourPageTexts(val art: TourArt, @StringRes val title: Int, @StringRes val text: Int)

/** The six pages in order (SCR-33): A3, A13, A8, A6, A9, A5. */
private val TOUR_PAGES = listOf(
    TourPageTexts(TourArt.HomeHeader, R.string.tour_page1_title, R.string.tour_page1_text),
    TourPageTexts(TourArt.TimerBand, R.string.tour_page2_title, R.string.tour_page2_text),
    TourPageTexts(TourArt.Catalog, R.string.tour_page3_title, R.string.tour_page3_text),
    TourPageTexts(TourArt.Journal, R.string.tour_page4_title, R.string.tour_page4_text),
    TourPageTexts(TourArt.Stats, R.string.tour_page5_title, R.string.tour_page5_text),
    TourPageTexts(TourArt.Reliability, R.string.tour_page6_title, R.string.tour_page6_text),
)

/**
 * The tour (SCR-33, ONB-07) on its own, from *Einstellungen* › *Hilfe* › *Einführung* (SET-36): *Fertig*,
 * *Überspringen* and back all call [onClose], which returns to the settings.
 */
@Composable
fun TourRoute(onClose: () -> Unit, modifier: Modifier = Modifier) {
    TourScreen(onFinish = onClose, onSkip = onClose, modifier = modifier)
}

/**
 * The six-page pager: the illustration, a title and the text scroll inside the page, so at a large font scale
 * the dots and the buttons stay in view. [onFinish] is *Fertig* on the last page, [onSkip] *Überspringen* and back
 * on the first page; back on another page returns to the one before. The swipe is the pager's; the buttons scroll
 * with the calm tokens (reduce motion: at once, 03 §3.6).
 */
@Composable
fun TourScreen(onFinish: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val pagerState = rememberPagerState(initialPage = initialPage) { TOUR_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val motion = UmamiTheme.motion
    val goTo: (Int) -> Unit = { target ->
        scope.launch {
            if (motion.reduceMotion) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(target, animationSpec = motion.standard())
            }
        }
    }
    BackHandler { if (pagerState.currentPage == 0) onSkip() else goTo(pagerState.currentPage - 1) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.Small),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag(TourTags.SKIP)) {
                    Text(stringResource(R.string.tour_skip), style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag(TourTags.PAGER),
                beyondViewportPageCount = 0,
                key = { it },
            ) { index -> TourPage(index) }
            TourDots(pagerState)
            val last = pagerState.currentPage == TOUR_PAGE_COUNT - 1
            PillButton(
                text = stringResource(if (last) R.string.tour_done else R.string.onboarding_next),
                onClick = { if (last) onFinish() else goTo(pagerState.currentPage + 1) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = UmamiSpacing.Small, bottom = UmamiSpacing.Large)
                    .testTag(TourTags.NEXT),
            )
        }
    }
}

/** One page: the art at the top, the title (a heading for TalkBack) and the text, scrolling together. */
@Composable
private fun TourPage(index: Int) {
    val page = TOUR_PAGES[index]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UmamiSpacing.SideMargin)
            .testTag(TourTags.page(index)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TourArtImage(page.art)
        Spacer(Modifier.height(UmamiSpacing.Large))
        Text(
            text = stringResource(page.title),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.Small))
        Text(
            text = stringResource(page.text),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
    }
}

@Composable
private fun TourArtImage(art: TourArt) {
    val dark = UmamiTheme.isDark
    when (art) {
        TourArt.HomeHeader -> BandImage(
            res = if (dark) UmamiIcons.HomeHeaderDark else UmamiIcons.HomeHeaderLight,
            ratio = HOME_HEADER_RATIO,
        )

        TourArt.TimerBand -> TimerBandImage()

        TourArt.Catalog -> VignetteImage(if (dark) UmamiIcons.CatalogEmptyDark else UmamiIcons.CatalogEmptyLight)

        TourArt.Journal -> VignetteImage(if (dark) UmamiIcons.JournalEmptyDark else UmamiIcons.JournalEmptyLight)

        TourArt.Stats -> VignetteImage(if (dark) UmamiIcons.StatsEmptyDark else UmamiIcons.StatsEmptyLight)

        TourArt.Reliability -> VignetteImage(if (dark) UmamiIcons.ReliabilityDark else UmamiIcons.ReliabilityLight)
    }
}

/** A3 as on Start: edge to edge, cropped to its band, the paper matched to the theme (no text on it). */
@Composable
private fun BandImage(@DrawableRes res: Int, ratio: Float) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        contentScale = ContentScale.Crop,
    )
}

/** A6, A8, A9 and A5 as their empty states and the reliability page show them: a small motif on matched paper. */
@Composable
private fun VignetteImage(@DrawableRes res: Int) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(VIGNETTE_WIDTH).widthIn(max = VIGNETTE_MAX_WIDTH),
        contentScale = ContentScale.FillWidth,
    )
}

/**
 * A13 as the timer's band: centre-cropped, fading into the background at its lower edge. The painting is the
 * night one, so on the light theme it becomes ink on washi through a colour matrix (03 §3.1), as on the timer.
 */
@Composable
private fun TimerBandImage() {
    val background = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    val filter = if (UmamiTheme.isDark) null else remember(background, ink) { inkOnWashi(background, ink) }
    Image(
        painter = painterResource(UmamiIcons.TimerBand),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TIMER_BAND_RATIO)
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

/** The page dots, one node for TalkBack: „Seite 2 von 6" (SCR-33). */
@Composable
private fun ColumnScope.TourDots(pagerState: PagerState) {
    val description = stringResource(R.string.tour_page_of, pagerState.currentPage + 1, TOUR_PAGE_COUNT)
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = UmamiSpacing.Small)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(TourTags.DOTS),
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(TOUR_PAGE_COUNT) { index ->
            val current = index == pagerState.currentPage
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .background(
                        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private val DOT_SIZE = 8.dp
private val DOT_GAP = 8.dp

/** A3's band as on Start: 3.43 : 1 (1536 × 448). */
private const val HOME_HEADER_RATIO = 3.43f

/** A13's band, a little taller than A3's: 2 : 1 of the 1536 × 822 painting, centre-cropped. */
private const val TIMER_BAND_RATIO = 2f
private const val VIGNETTE_WIDTH = 0.7f
private val VIGNETTE_MAX_WIDTH = 280.dp
private const val BAND_FADE_START = 0.35f
private const val BAND_PAPER_LUMA = 30f
private const val BAND_LEAF_LUMA = 140f
private const val BAND_INK_STRENGTH = 0.85f
private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f
private const val CHANNEL_MAX = 255f
