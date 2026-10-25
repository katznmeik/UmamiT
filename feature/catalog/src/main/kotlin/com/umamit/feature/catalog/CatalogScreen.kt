package com.umamit.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.rememberPhotoPainter
import java.time.ZoneId

/** The catalog list (SCR-19). */
const val CATALOG_LIST_TAG = "catalog_list"

/**
 * Sammlung › Katalog (SCR-19) with its view model, as the content below Sammlung's segmented
 * control: the empty state with *Katalog laden* (D-073), or the teas by name with their price
 * per 100 g under the header „Tee-Kontor Kiel · 274 Grüntees · aktualisiert am 21. Okt.". A tap
 * on *Katalog laden* starts the refresh and opens its sheet (SCR-22).
 *
 * **Alerts first (rule 8):** while an alert plays ([alerting]) the sheet is not composed, so it
 * leaves in that frame without animation, and it stays closed; the refresh itself goes on
 * (CAT-07 AC8).
 */
@Composable
fun CatalogRoute(
    alerting: Boolean,
    onOpenTea: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(alerting) { if (alerting) sheetOpen = false }
    CatalogContent(
        state = state,
        onLoad = {
            viewModel.startRefresh()
            sheetOpen = true
        },
        onShowProgress = { sheetOpen = true },
        modifier = modifier,
        onOpenTea = onOpenTea,
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
 * running refresh; [onOpenTea] opens a tea's detail (SCR-21) by its key. [scrollToTopRequest] is
 * a counter; every increase scrolls the list to the top. [thumbnail] loads a row's thumbnail
 * file; tests pass placeholder images.
 */
@Composable
fun CatalogContent(
    state: CatalogUiState,
    onLoad: () -> Unit,
    onShowProgress: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTea: (String) -> Unit = {},
    scrollToTopRequest: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
    thumbnail: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
) {
    when {
        state.loading -> Spacer(modifier.fillMaxSize())
        state.teas.isEmpty() -> CatalogEmpty(state.isRefreshing, onLoad, onShowProgress, modifier)
        else -> CatalogList(state, onShowProgress, onOpenTea, scrollToTopRequest, zone, thumbnail, modifier)
    }
}

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
@Suppress("LongParameterList") // the list's state, its callbacks and the thumbnail loader
private fun CatalogList(
    state: CatalogUiState,
    onShowProgress: () -> Unit,
    onOpenTea: (String) -> Unit,
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
    val separator = stringResource(R.string.catalog_meta_separator)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(CATALOG_LIST_TAG),
        state = listState,
    ) {
        item(key = "header") { Header(state, zone) }
        if (state.isRefreshing) item(key = "refreshing") { RefreshingRow(onShowProgress) }
        itemsIndexed(state.teas, key = { _, tea -> tea.key }) { index, tea ->
            TeaListItem(
                title = tea.name,
                meta = listOfNotNull(tea.teaType, tea.basePriceCentsPer100g?.let { pricePer100g(it) })
                    .joinToString(separator),
                onClick = { onOpenTea(tea.key) },
                thumbnail = thumbnail(tea.thumbnailPath),
                placeholderColor = placeholderColor(tea.teaType),
                showDivider = index < state.teas.lastIndex,
            )
        }
    }
}

@Composable
private fun Header(state: CatalogUiState, zone: ZoneId) {
    val count = pluralStringResource(R.plurals.catalog_tea_count, state.teas.size, state.teas.size)
    val updated = state.lastRefreshAtMillis
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

@Composable
private fun pricePer100g(cents: Long): String =
    stringResource(R.string.catalog_price_per_100g, CatalogFormat.euros(cents))

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
