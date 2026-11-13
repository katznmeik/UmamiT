package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import com.umamit.feature.stats.charts.ChartPageState
import com.umamit.feature.stats.charts.ChartSelectionHolder
import com.umamit.feature.stats.charts.ChartSelectionHost
import com.umamit.feature.stats.charts.LocalChartPage
import com.umamit.feature.stats.charts.chartViewport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs

private val BottomClearance = 72.dp

/** The segments' side padding: five labels share the row (the M3 default of 12 dp would clip „Gesamt"). */
private val SEGMENT_PADDING = 4.dp
private val TAB_HEIGHT = 48.dp

/**
 * Statistik (SCR-18; INS-07, INS-08, D-115) with its [StatsViewModel]. [onOpenTea] opens a collection tea's detail
 * (SCR-09) from a tab's content; the host decides how (the resumed screen only, rule 8's gating of the shell).
 */
@Composable
fun StatsRoute(
    onOpenSettings: () -> Unit,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    StatsContent(
        state = state,
        onRangeChange = viewModel::selectRange,
        onTabChange = viewModel::selectTab,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        scrollToTopRequest = scrollToTopRequest,
        onCustomRange = viewModel::selectCustom,
        onFoldedChange = viewModel::setFolded,
    ) { tab, _, scrollState ->
        when (tab) {
            StatsTab.OVERVIEW -> OverviewTab(
                figures = viewModel.overview.collectAsStateWithLifecycle().value,
                scrollState = scrollState,
                onOpenTea = onOpenTea,
            )

            StatsTab.RATINGS -> RatingsTab(
                figures = viewModel.ratings.collectAsStateWithLifecycle().value,
                scrollState = scrollState,
                onOpenTea = onOpenTea,
            )

            StatsTab.CONSUMPTION -> ConsumptionTab(
                figures = viewModel.consumption.collectAsStateWithLifecycle().value,
                scrollState = scrollState,
                onOpenTea = onOpenTea,
            )
        }
    }
}

/**
 * The screen for a [state]: nothing while loading, A9 alone when there is no finished session, else the header, the
 * pinned selector and tabs, and the swipeable pages. [tabContent] draws a tab's page: the tab, the [StatsRange] and
 * the [ScrollState] the page scrolls with (kept by the shell); by default the three slots without figures.
 */
@Composable
@Suppress("LongParameterList") // the state, its three callbacks and the page slot
fun StatsContent(
    state: StatsUiState,
    onRangeChange: (StatsRange) -> Unit,
    onTabChange: (StatsTab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onCustomRange: (from: YearMonth, to: YearMonth) -> Unit = { _, _ -> },
    onFoldedChange: (key: String, folded: Boolean) -> Unit = { _, _ -> },
    tabContent: @Composable (tab: StatsTab, range: StatsRange, scrollState: ScrollState) -> Unit =
        { tab, _, scrollState -> PlaceholderTab(tab, scrollState) },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                StatsUiState.Loading -> Unit

                StatsUiState.Empty -> EmptyStats(onOpenSettings)

                is StatsUiState.Data -> StatsPages(
                    state = state,
                    onRangeChange = onRangeChange,
                    onTabChange = onTabChange,
                    onOpenSettings = onOpenSettings,
                    scrollToTopRequest = scrollToTopRequest,
                    onCustomRange = onCustomRange,
                    onFoldedChange = onFoldedChange,
                    tabContent = tabContent,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderTab(tab: StatsTab, scrollState: ScrollState) {
    when (tab) {
        StatsTab.OVERVIEW -> OverviewTab(null, scrollState, onOpenTea = {})
        StatsTab.RATINGS -> RatingsTab(null, scrollState, onOpenTea = {})
        StatsTab.CONSUMPTION -> ConsumptionTab(null, scrollState, onOpenTea = {})
    }
}

/** „Deine Tee-Reise" (displayLarge) with the ⋮ menu: it scrolls away with a tab's content (SCR-18). */
@Composable
private fun StatsHeader(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XXSmall, top = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = UmamiSpacing.XSmall)
                .semantics { heading() },
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OverflowSettingsAction(onOpenSettings)
    }
}

/** No finished session at all: A9 alone, centred while it fits, scrollable at a large font scale (INS-08). */
@Composable
private fun EmptyStats(onOpenSettings: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val half = maxHeight / 2
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            StatsHeader(onOpenSettings)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = half)
                    .padding(bottom = BottomClearance),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    body = stringResource(R.string.stats_empty_body),
                    illustration = painterResource(
                        if (UmamiTheme.isDark) UmamiIcons.StatsEmptyDark else UmamiIcons.StatsEmptyLight,
                    ),
                    illustrationTint = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod") // the state, its callbacks, the re-selection counter and the page slot
private fun StatsPages(
    state: StatsUiState.Data,
    onRangeChange: (StatsRange) -> Unit,
    onTabChange: (StatsTab) -> Unit,
    onOpenSettings: () -> Unit,
    scrollToTopRequest: Int,
    onCustomRange: (YearMonth, YearMonth) -> Unit,
    onFoldedChange: (String, Boolean) -> Unit,
    tabContent: @Composable (StatsTab, StatsRange, ScrollState) -> Unit,
) {
    val header = remember { CollapsingHeader() }
    header.reduceMotion = LocalReduceMotion.current
    val scrollStates = StatsTab.entries.map { rememberScrollState() }
    // The pager is the one source of the selected tab (it survives a quick double swipe); the stored tab only
    // seeds it and is written to once a page has settled.
    val pagerState = rememberPagerState(initialPage = state.tab.ordinal) { StatsTab.entries.size }
    val scope = rememberCoroutineScope()
    // Which charts were revealed already, per tab: the pager disposes a page that is swiped away (INS-08).
    val revealed = rememberSaveable { mutableStateListOf<String>() }
    val pages = remember {
        StatsTab.entries.map { ChartPageState(it.name, revealed, active = it == state.tab) }
    }
    val selection = remember { ChartSelectionHolder() }
    var monthsSheet by rememberSaveable { mutableStateOf(false) }
    val folds = remember(state.folded, onFoldedChange) { StatsFolds(state.folded, onFoldedChange) }
    val currentOnTabChange by rememberUpdatedState(onTabChange)
    val seededTab = remember { state.tab }
    LaunchedEffect(pagerState) {
        var reported = seededTab
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            pages.forEachIndexed { index, chartPage -> chartPage.active = index == page }
            selection.clear()
            val tab = StatsTab.entries[page]
            if (tab != reported) {
                reported = tab
                currentOnTabChange(tab)
            }
            // A page that starts at its top shows the title; one that was scrolled keeps it as it is.
            if (scrollStates[page].value == 0) header.reveal()
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.isScrollInProgress || scrollStates.any { it.isScrollInProgress } }
            .filter { it }
            .collect { selection.clear() }
    }
    // A counter, as in ScreenScaffold: only an increase while composed scrolls; never replayed (D-049).
    var handled by remember { mutableIntStateOf(scrollToTopRequest) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > handled) {
            handled = scrollToTopRequest
            coroutineScope {
                launch { header.reveal() }
                launch { scrollStates[pagerState.currentPage].animateScrollTo(0) }
            }
        }
    }
    ChartSelectionHost(selection, Modifier.fillMaxSize()) {
        CollapsingColumn(
            header = header,
            title = { StatsHeader(onOpenSettings) },
            // The pinned chrome keeps its labels on one line: it scales up to 1.15, the content below up to 2.0.
            pinned = {
                CappedFontScale {
                    Column {
                        RangeSelector(state.range, state.custom, onRangeChange) {
                            monthsSheet = true
                            state.months?.let { bounds ->
                                // *Monate* applies at once: the remembered months, else the last three.
                                val span = chosenMonths(state, bounds)
                                if (state.range != StatsRange.CUSTOM || state.custom != span) {
                                    onCustomRange(span.from, span.to)
                                }
                            }
                        }
                        TabBar(pagerState) { tab ->
                            scope.launch {
                                // A jump of more than one page does not scroll through (and compose) the middle one.
                                if (abs(tab.ordinal - pagerState.currentPage) > 1) {
                                    pagerState.scrollToPage(tab.ordinal)
                                } else {
                                    pagerState.animateScrollToPage(tab.ordinal)
                                }
                            }
                        }
                    }
                }
            },
            body = {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                    key = { it },
                ) { page ->
                    CompositionLocalProvider(LocalChartPage provides pages[page], LocalStatsFolds provides folds) {
                        Box(Modifier.fillMaxSize().chartViewport(pages[page])) {
                            tabContent(StatsTab.entries[page], state.range, scrollStates[page])
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    val bounds = state.months
    if (monthsSheet && bounds != null) {
        MonthsSheet(
            bounds = bounds,
            selected = chosenMonths(state, bounds),
            onChange = { onCustomRange(it.from, it.to) },
            onDismissRequest = { monthsSheet = false },
        )
    }
}

/** The custom months within [bounds]: the remembered ones, else the last three months (R7). */
private fun chosenMonths(state: StatsUiState.Data, bounds: MonthSpan): MonthSpan {
    val to = state.custom?.to?.coerceIn(bounds.from, bounds.to) ?: bounds.to
    val from = state.custom?.from?.coerceIn(bounds.from, to) ?: maxOf(bounds.from, to.minusMonths(2))
    return MonthSpan(from, to)
}

/**
 * The segments' shares of the row: the short labels need less than „Gesamt", and *Monate* with its months
 * („Dez. 25–Aug. 26", on two lines) the most, so the row fits at the font scale cap of 1,15 on a narrow phone.
 */
private fun StatsRange.segmentWeight(withMonths: Boolean): Float = when (this) {
    StatsRange.DAYS_30, StatsRange.DAYS_90, StatsRange.MONTHS_12 -> SHORT_SEGMENT_WEIGHT
    StatsRange.ALL -> ALL_SEGMENT_WEIGHT
    StatsRange.CUSTOM -> if (withMonths) MONTHS_SEGMENT_WEIGHT else MONTHS_PLAIN_SEGMENT_WEIGHT
}

private const val SHORT_SEGMENT_WEIGHT = 0.85f
private const val ALL_SEGMENT_WEIGHT = 1.15f
private const val MONTHS_PLAIN_SEGMENT_WEIGHT = 1.1f
private const val MONTHS_SEGMENT_WEIGHT = 1.45f

/**
 * The selector: one segmented row, pinned (INS-07). TalkBack reads „Zeitraum: 90 Tage" and the selected state. The
 * fifth segment *Monate* (R7) opens the sheet through [onOpenMonths] and, with [custom] months chosen, reads
 * „Dez.–Aug." (two lines when it carries the years, so the row still fits at font 1,15).
 */
@Composable
private fun RangeSelector(
    range: StatsRange,
    custom: MonthSpan?,
    onRangeChange: (StatsRange) -> Unit,
    onOpenMonths: () -> Unit,
) {
    val ranges = StatsRange.entries
    val resources = LocalResources.current
    val span = custom?.label(resources)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
    ) {
        ranges.forEachIndexed { index, entry ->
            val isCustom = entry == StatsRange.CUSTOM
            val label = if (isCustom && span != null) span else stringResource(entry.labelRes)
            val description = if (isCustom && span != null && custom != null) {
                stringResource(
                    R.string.stats_range_months_chosen_description,
                    custom.from.fullLabel(),
                    custom.to.fullLabel(),
                )
            } else {
                stringResource(entry.descriptionRes)
            }
            SegmentedButton(
                selected = entry == range,
                onClick = { if (isCustom) onOpenMonths() else onRangeChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                modifier = Modifier.weight(entry.segmentWeight(withMonths = span != null)),
                contentPadding = PaddingValues(horizontal = SEGMENT_PADDING),
                icon = {},
            ) {
                Text(
                    text = label,
                    modifier = Modifier.semantics { contentDescription = description },
                    maxLines = if (isCustom) 2 else 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TabBar(pagerState: PagerState, onSelect: (StatsTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = pagerState.targetPage, containerColor = MaterialTheme.colorScheme.background) {
        StatsTab.entries.forEach { entry ->
            // The content slot has no 16 dp text padding, so „Bewertungen" fits a third of 360 dp at font scale 1,15.
            Tab(
                selected = entry.ordinal == pagerState.targetPage,
                onClick = { onSelect(entry) },
            ) {
                Box(Modifier.heightIn(min = TAB_HEIGHT).padding(horizontal = UmamiSpacing.XSmall), Alignment.Center) {
                    Text(
                        text = stringResource(entry.labelRes),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** The largest font scale of the pinned selector and tabs (their labels are single words in a fixed row). */
private const val CHROME_MAX_FONT_SCALE = 1.15f

@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, CHROME_MAX_FONT_SCALE)),
        content = content,
    )
}

private val StatsRange.labelRes: Int
    get() = when (this) {
        StatsRange.DAYS_30 -> R.string.stats_range_30
        StatsRange.DAYS_90 -> R.string.stats_range_90
        StatsRange.MONTHS_12 -> R.string.stats_range_12m
        StatsRange.ALL -> R.string.stats_range_all
        StatsRange.CUSTOM -> R.string.stats_range_months
    }

private val StatsRange.descriptionRes: Int
    get() = when (this) {
        StatsRange.DAYS_30 -> R.string.stats_range_30_description
        StatsRange.DAYS_90 -> R.string.stats_range_90_description
        StatsRange.MONTHS_12 -> R.string.stats_range_12m_description
        StatsRange.ALL -> R.string.stats_range_all_description
        StatsRange.CUSTOM -> R.string.stats_range_months_description
    }

private val StatsTab.labelRes: Int
    get() = when (this) {
        StatsTab.OVERVIEW -> R.string.stats_tab_overview
        StatsTab.RATINGS -> R.string.stats_tab_ratings
        StatsTab.CONSUMPTION -> R.string.stats_tab_consumption
    }
