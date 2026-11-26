package com.umamit.feature.onboarding

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale
import kotlin.math.abs

/** The number of tour pages (SCR-33). */
const val TOUR_PAGE_COUNT = 6

private class TourPageTexts(
    val art: TourArt,
    val mockup: HelpMockupId,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val topics: List<HowToTopic>,
)

/** The six pages in order (SCR-33): the art A3, A13, A8, A6, A9, A5, each with its mock-up and „Mehr dazu" links. */
private val TOUR_PAGES = listOf(
    TourPageTexts(
        TourArt.HomeHeader,
        HelpMockupId.Start,
        R.string.tour_page1_title,
        R.string.tour_page1_text,
        listOf(HowToTopic.Brewing, HowToTopic.ReliableAlarm),
    ),
    TourPageTexts(
        TourArt.TimerBand,
        HelpMockupId.Two,
        R.string.tour_page2_title,
        R.string.tour_page2_text,
        listOf(HowToTopic.Brewing),
    ),
    TourPageTexts(
        TourArt.Catalog,
        HelpMockupId.MyTeasMenu,
        R.string.tour_page3_title,
        R.string.tour_page3_text,
        listOf(HowToTopic.WithoutCatalog, HowToTopic.ShopLink, HowToTopic.Recipes),
    ),
    TourPageTexts(
        TourArt.Journal,
        HelpMockupId.Session,
        R.string.tour_page4_title,
        R.string.tour_page4_text,
        listOf(HowToTopic.JournalAndTaste),
    ),
    TourPageTexts(
        TourArt.Stats,
        HelpMockupId.Stats,
        R.string.tour_page5_title,
        R.string.tour_page5_text,
        listOf(HowToTopic.StockAndCost),
    ),
    TourPageTexts(
        TourArt.Reliability,
        HelpMockupId.Data,
        R.string.tour_page6_title,
        R.string.tour_page6_text,
        listOf(HowToTopic.DataAndBackup, HowToTopic.Recipes),
    ),
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
 * The six-page pager: each page's art band, mock-up, title, text and „Mehr dazu" links scroll inside the page, so at
 * a large font scale the dots and the button stay in view. [onFinish] is *Fertig* on the last page, [onSkip]
 * *Überspringen* (not on the last page) and back on the first page; back on another page returns to the one before.
 * A „Mehr dazu" link shows its Anleitung (SCR-36) in place of the pager, which keeps its page; ← and back return to
 * that page. The buttons scroll with the calm tokens (reduce motion: at once, 03 §3.6).
 */
@Composable
fun TourScreen(onFinish: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val pagerState = rememberPagerState(initialPage = initialPage) { TOUR_PAGE_COUNT }
    var openTopic by rememberSaveable { mutableStateOf<HowToTopic?>(null) }
    // One scroll position per page, above the early return: „Mehr dazu" swaps the pager out, and back returns to it.
    val scrollStates = List(TOUR_PAGE_COUNT) { rememberScrollState() }
    val topic = openTopic
    if (topic != null) {
        BackHandler { openTopic = null }
        HowToTopicScreen(topic = topic, onBack = { openTopic = null }, modifier = modifier)
        return
    }
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
            val last = pagerState.currentPage == TOUR_PAGE_COUNT - 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = UmamiSpacing.MinTouchTarget)
                    .padding(horizontal = UmamiSpacing.Small),
                horizontalArrangement = Arrangement.End,
            ) {
                // Hidden on the last page (D-137 Q11); the row keeps its height so the page does not jump.
                if (!last) {
                    TextButton(onClick = onSkip, modifier = Modifier.testTag(TourTags.SKIP)) {
                        Text(stringResource(R.string.tour_skip), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag(TourTags.PAGER),
                beyondViewportPageCount = 0,
                key = { it },
            ) { index -> TourPage(index, scrollStates[index], onOpenTopic = { openTopic = it }) }
            TourDots(pagerState)
            PillButton(
                text = stringResource(if (last) R.string.tour_done else R.string.onboarding_next),
                onClick = { if (last) onFinish() else goTo(pagerState.currentPage + 1) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = UmamiSpacing.Small, bottom = UmamiSpacing.Large)
                    .width(rememberTourButtonWidth())
                    .testTag(TourTags.NEXT),
            )
        }
    }
}

/**
 * One width for *Weiter* and *Fertig* (*Next* and *Done*): the longer label at the current font scale plus the pill's
 * padding, in either app language, so only the text changes between the pages (D-137 Q12).
 */
// The app ships as an APK from GitHub Releases, not as a bundle split by language, so reading both languages is safe.
@SuppressLint("AppBundleLocaleChanges")
@Composable
private fun rememberTourButtonWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val context = LocalContext.current
    val current = LocalConfiguration.current
    val labels = remember(context, current) {
        listOf(Locale.GERMAN, Locale.ENGLISH).flatMap { locale ->
            val configuration = Configuration(current).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(configuration)
            listOf(R.string.onboarding_next, R.string.tour_done).map { localized.getString(it) }
        }
    }
    return remember(measurer, style, density, labels) {
        val widest = labels.maxOf { measurer.measure(it, style).size.width }
        with(density) { widest.toDp() } + UmamiSpacing.Large * 2
    }
}

/**
 * One page: the art band edge to edge, the mock-up, the title (a heading for TalkBack), the text and the links,
 * scrolling together.
 */
@Composable
private fun TourPage(index: Int, scrollState: ScrollState, onOpenTopic: (HowToTopic) -> Unit) {
    val page = TOUR_PAGES[index]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .testTag(TourTags.page(index)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TourArtImage(page.art)
        Spacer(Modifier.height(UmamiSpacing.Medium))
        HelpMockup(id = page.mockup, size = HelpMockupSize.Tour)
        Spacer(Modifier.height(UmamiSpacing.Medium))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
            page.topics.forEach { topic -> MoreLink(topic, onClick = { onOpenTopic(topic) }) }
        }
        Spacer(Modifier.height(UmamiSpacing.Medium))
    }
}

/** „Mehr dazu: <Thema>": a text link to one Anleitung (SCR-36), at least 48 dp high. */
@Composable
private fun MoreLink(topic: HowToTopic, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(TourTags.more(topic)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tour_more, stringResource(topic.title)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The page dots, one node for TalkBack: „Seite 2 von 6" (SCR-33). They keep one size; the colour blends from the
 * quiet to the current colour with the swipe, by `currentPage + currentPageOffsetFraction`, read in the draw phase.
 */
@Composable
private fun ColumnScope.TourDots(pagerState: PagerState) {
    val description = stringResource(R.string.tour_page_of, pagerState.currentPage + 1, TOUR_PAGE_COUNT)
    val quiet = MaterialTheme.colorScheme.outlineVariant
    val current = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = UmamiSpacing.Small)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(TourTags.DOTS),
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(TOUR_PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .drawBehind {
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val weight = (1f - abs(position - index)).coerceIn(0f, 1f)
                        drawCircle(lerp(quiet, current, weight))
                    },
            )
        }
    }
}

private val DOT_SIZE = 8.dp
private val DOT_GAP = 8.dp
