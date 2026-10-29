package com.umamit.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.ui.rememberPhotoPainter
import java.time.ZoneId

/** The catalog list (SCR-19). */
const val CATALOG_LIST_TAG = "catalog_list"

/** The list's *Ratgeber* row (SCR-19, CAT-09). */
const val CATALOG_GUIDES_ROW_TAG = "catalog_guides_row"

/**
 * Sammlung › Katalog (SCR-19) with its view model, as the content below Sammlung's segmented
 * control: the empty state with *Katalog laden* (D-073), or the teas under the header
 * „Tee-Kontor Kiel · 274 Grüntees · aktualisiert am 21. Okt." (in the *Kräutertee* view „… · 38
 * Kräutertees · …", D-093) with search, filter chips and the filter sheet (CAT-10). A tap on
 * *Katalog laden*, or on *Katalog aktualisieren* in Sammlung's ⋮ menu ([refreshRequest]), starts
 * the refresh and opens its sheet (SCR-22).
 *
 * **Alerts first (rule 8):** while an alert plays ([alerting]) no sheet is composed, so it leaves
 * in that frame without animation, and it stays closed; the refresh itself goes on (CAT-07 AC8).
 *
 * @param refreshRequest a counter from the ⋮ menu: every increase while this is shown starts the
 *   refresh; a count from before is never replayed (D-049).
 * @param onOpenGuides opens the *Ratgeber* (CAT-09) from its row.
 */
@Composable
@Suppress("LongParameterList") // the alert gate, the ways out, the shell's counters and the view model
fun CatalogRoute(
    alerting: Boolean,
    onOpenTea: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGuides: () -> Unit = {},
    scrollToTopRequest: Int = 0,
    refreshRequest: Int = 0,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(alerting) { if (alerting) sheetOpen = false }
    val startRefresh = {
        viewModel.startRefresh()
        if (!alerting) sheetOpen = true
    }
    var handledRefresh by remember { mutableIntStateOf(refreshRequest) }
    LaunchedEffect(refreshRequest) {
        if (refreshRequest > handledRefresh) {
            handledRefresh = refreshRequest
            startRefresh()
        }
    }
    CatalogContent(
        state = state,
        onLoad = startRefresh,
        onShowProgress = { sheetOpen = true },
        modifier = modifier,
        onOpenTea = onOpenTea,
        onOpenGuides = onOpenGuides,
        onFilterChange = viewModel::changeFilter,
        alerting = alerting,
        scrollToTopRequest = scrollToTopRequest,
    )
    val refresh = state.refresh
    if (sheetOpen && !alerting && refresh !is CatalogRefreshState.Idle) {
        CatalogRefreshSheet(
            refresh = refresh,
            onCancel = viewModel::cancelRefresh,
            onDismissRequest = {
                sheetOpen = false
                // A result is seen once; a running refresh goes on behind the closed sheet.
                if (refresh is CatalogRefreshState.Finished) viewModel.acknowledgeResult()
            },
        )
    }
}

/**
 * The stateless catalog content (SCR-19): nothing until the catalog was read, then the empty
 * state or the list. [onLoad] is *Katalog laden*; [onShowProgress] reopens the sheet of a
 * running refresh; [onOpenTea] opens a tea's detail (SCR-21) by its key; [onFilterChange] takes
 * a changed search, filter or sort (CAT-10); [onOpenGuides] opens the *Ratgeber* (CAT-09). The
 * filter sheet is never composed while [alerting] (rule 8). [scrollToTopRequest] is a counter;
 * every increase scrolls the list to the top. [thumbnail] loads a row's thumbnail file; tests pass placeholder images.
 */
@Composable
@Suppress("LongParameterList") // the list's state, its callbacks, the alert gate and the thumbnail loader
fun CatalogContent(
    state: CatalogUiState,
    onLoad: () -> Unit,
    onShowProgress: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTea: (String) -> Unit = {},
    onOpenGuides: () -> Unit = {},
    onFilterChange: (FilterChange) -> Unit = {},
    alerting: Boolean = false,
    scrollToTopRequest: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
    thumbnail: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
) {
    when {
        state.loading -> Spacer(modifier.fillMaxSize())

        state.isEmpty -> CatalogEmpty(state.isRefreshing, onLoad, onShowProgress, modifier)

        else -> {
            var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(alerting) { if (alerting) filterSheetOpen = false }
            CatalogList(
                state = state,
                actions = ListActions(
                    onShowProgress = onShowProgress,
                    onOpenTea = onOpenTea,
                    onOpenGuides = onOpenGuides,
                    onFilterChange = onFilterChange,
                    onOpenFilters = { if (!alerting) filterSheetOpen = true },
                ),
                scrollToTopRequest = scrollToTopRequest,
                zone = zone,
                thumbnail = thumbnail,
                modifier = modifier,
            )
            if (filterSheetOpen && !alerting) {
                CatalogFilterSheet(
                    filter = state.filter,
                    listing = state.listing,
                    onFilterChange = onFilterChange,
                    onDismissRequest = { filterSheetOpen = false },
                )
            }
        }
    }
}

/** What the list's rows and controls do. */
private class ListActions(
    val onShowProgress: () -> Unit,
    val onOpenTea: (String) -> Unit,
    val onOpenGuides: () -> Unit,
    val onFilterChange: (FilterChange) -> Unit,
    val onOpenFilters: () -> Unit,
)

@Composable
private fun CatalogEmpty(
    refreshing: Boolean,
    onLoad: () -> Unit,
    onShowProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Centred while it fits, scrollable at a large font scale.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The A8 illustration comes from the PO (docs/design/assets.md); the placeholder stands in.
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(if (refreshing) R.string.catalog_loading_body else R.string.catalog_empty_body),
                actionLabel = stringResource(if (refreshing) R.string.catalog_show_progress else R.string.catalog_load),
                onAction = if (refreshing) onShowProgress else onLoad,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList") // the list's state, its actions, the scroll counter, the zone and the thumbnail loader
private fun CatalogList(
    state: CatalogUiState,
    actions: ListActions,
    scrollToTopRequest: Int,
    zone: ZoneId,
    thumbnail: @Composable (path: String?) -> Painter?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // A counter, as in ScreenScaffold: only an increase while composed scrolls; never replayed (D-049).
    var handled by remember { mutableIntStateOf(scrollToTopRequest) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > handled) {
            handled = scrollToTopRequest
            listState.animateScrollToItem(0)
        }
    }
    val listing = state.listing
    val filter = state.filter
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(CATALOG_LIST_TAG),
        state = listState,
    ) {
        item(key = "header") { Header(listing, filter.herbal, zone) }
        if (state.isRefreshing) item(key = "refreshing") { RefreshingRow(actions.onShowProgress) }
        if (state.guideCount > 0) item(key = "guides") { GuidesRow(state.guideCount, actions.onOpenGuides) }
        item(key = "search") { CatalogSearchField(filter, actions.onFilterChange) }
        item(key = "chips") { CatalogFilterChips(filter, actions.onFilterChange, actions.onOpenFilters) }
        if (listing.sections.isEmpty()) {
            item(key = "no_match") {
                NoticeRow(stringResource(R.string.catalog_no_match), stringResource(R.string.catalog_reset_filters)) {
                    actions.onFilterChange { CatalogFilter.DEFAULT.copy(search = it.search, sort = it.sort) }
                }
            }
        }
        teaRows(listing, actions.onOpenTea, thumbnail)
        if (listing.hiddenMatches > 0) {
            item(key = "hidden") {
                NoticeRow(
                    pluralStringResource(
                        R.plurals.catalog_hidden_matches,
                        listing.hiddenMatches,
                        listing.hiddenMatches,
                    ),
                    stringResource(R.string.catalog_show_all),
                ) { actions.onFilterChange { showAll(it, listing) } }
            }
        }
    }
}

/**
 * *Alle zeigen* for the search hits the filters hide: the current kind without filters, or, when
 * every hidden hit is of the other kind (CAT-10, D-093), the other kind's view without filters.
 */
internal fun showAll(filter: CatalogFilter, listing: CatalogListing): CatalogFilter {
    val all = filter.withoutFilters()
    return if (listing.hiddenMatches == listing.hiddenInOtherView) all.withHerbal(!filter.herbal) else all
}

@Composable
private fun Header(listing: CatalogListing, herbal: Boolean, zone: ZoneId) {
    val plurals = if (herbal) R.plurals.catalog_herbal_count else R.plurals.catalog_tea_count
    val count = pluralStringResource(plurals, listing.totalCount, listing.totalCount)
    val updated = listing.lastRefreshAtMillis
    Text(
        text = if (updated != null) {
            stringResource(R.string.catalog_header, count, CatalogFormat.date(updated, zone))
        } else {
            stringResource(R.string.catalog_header_undated, count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
    )
}

/** SCR-19's *Ratgeber* row (CAT-09): opens the guides. */
@Composable
private fun GuidesRow(count: Int, onOpenGuides: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenGuides)
            .semantics(mergeDescendants = true) {}
            .testTag(CATALOG_GUIDES_ROW_TAG)
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
    ) {
        Icon(
            painter = painterResource(UmamiIcons.Journal),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.catalog_guides_row),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.catalog_guides_row_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A short notice in the list with one action: no match, or search hits the filters hide. */
@Composable
private fun NoticeRow(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun RefreshingRow(onShowProgress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowProgress)
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
    ) {
        Text(
            text = stringResource(R.string.catalog_refreshing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(UmamiSpacing.XXSmall))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** The leaf placeholder's colour by the shop's *Sorte* [teaType] (03 §3.5), for a tea without a photo. */
@Composable
internal fun placeholderColor(teaType: String?): Color {
    val colors = UmamiTheme.extendedColors.teaTypes
    val type = teaType.orEmpty().lowercase()
    return when {
        "gyokuro" in type -> colors.gyokuro
        "kabuse" in type -> colors.kabusecha
        "shincha" in type -> colors.shincha
        "sencha" in type -> colors.sencha
        "matcha" in type -> colors.matcha
        "kukicha" in type || "karigane" in type -> colors.kukichaKarigane
        "hojicha" in type || "houjicha" in type -> colors.hojicha
        "genmaicha" in type -> colors.genmaicha
        else -> colors.other
    }
}
