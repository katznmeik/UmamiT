package com.umamit.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.CollectionSort
import com.umamit.core.ui.rememberPhotoPainter

/** The list of *Meine Tees* (SCR-08). */
const val MY_TEAS_LIST_TAG = "my_teas_list"

/** The row of tea [id] in *Meine Tees*. */
fun myTeaRowTag(id: String) = "my_tea_$id"

/** Room below the last row, so it clears the shell's snackbar „… archiviert" once it is scrolled up (F13). */
private val BottomClearance = 72.dp

/**
 * What *Meine Tees* does on a tap; the app wires the navigation (06 §3: features don't depend on each other).
 * Adding a tea is Sammlung's ⋮ menu since rc.2 (D-102); [onAddFromCatalog] is the empty state's *Im Katalog stöbern*.
 */
class MyTeasActions(
    val onOpenTea: (teaId: String) -> Unit = {},
    val onFilterChange: (FilterChange) -> Unit = {},
    val onResetFilters: () -> Unit = {},
    val onSortChange: (CollectionSort) -> Unit = {},
    val onRestore: (teaId: String) -> Unit = {},
    val onAddFromCatalog: () -> Unit = {},
)

/** A change of the search or the chips (TEA-09, TEA-10), applied to the current [CollectionFilter]. */
typealias FilterChange = (CollectionFilter) -> CollectionFilter

/**
 * *Meine Tees* (SCR-08) with its view model, below Sammlung's segmented control. Adding a tea is Sammlung's ⋮ menu
 * (D-102), wired by the app with [CollectionScreen].
 *
 * @param onOpenTea a row's tap: the tea's detail (SCR-09).
 * @param onAddFromCatalog the empty state's *Im Katalog stöbern*: Sammlung's *Katalog* segment (SCR-19).
 * @param alertKey the alert playing now, or null: the chips' menus give way to another (rule 8).
 * @param scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 */
@Composable
fun MyTeasRoute(
    onOpenTea: (teaId: String) -> Unit,
    onAddFromCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
    scrollToTopRequest: Int = 0,
    viewModel: MyTeasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyTeasContent(
        state = state,
        actions = MyTeasActions(
            onOpenTea = onOpenTea,
            onFilterChange = viewModel::updateFilter,
            onResetFilters = viewModel::resetFilters,
            onSortChange = viewModel::setSort,
            onRestore = viewModel::restore,
            onAddFromCatalog = onAddFromCatalog,
        ),
        modifier = modifier,
        alertKey = alertKey,
        scrollToTopRequest = scrollToTopRequest,
    )
}

/**
 * *Meine Tees* without state (SCR-08; TEA-08–TEA-11, D-100): the search „Deine Tees durchsuchen …", the filter
 * chips and the sort, and the rows. No tea yet: A7 „Noch keine Tees" · *Im Katalog stöbern*; nothing that matches:
 * A10 „Kein Tee passt zu deiner Suche.". Nothing floats over the rows: adding a tea is Sammlung's ⋮ menu (D-102).
 * [thumbnail] loads a row's cover file; tests pass placeholder images.
 */
@Composable
fun MyTeasContent(
    state: MyTeasUiState,
    actions: MyTeasActions,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
    scrollToTopRequest: Int = 0,
    thumbnail: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.empty == MyTeasEmpty.NO_TEAS && !state.showControls -> NoTeasAlone(actions.onAddFromCatalog)
            else -> MyTeasList(state, actions, alertKey, scrollToTopRequest, thumbnail)
        }
    }
}

@Composable
private fun MyTeasList(
    state: MyTeasUiState,
    actions: MyTeasActions,
    alertKey: String?,
    scrollToTopRequest: Int,
    thumbnail: @Composable (path: String?) -> Painter?,
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MY_TEAS_LIST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = BottomClearance),
    ) {
        item(key = "search") { MyTeasSearchField(state.filter, actions.onFilterChange) }
        item(key = "chips") { MyTeasChips(state, actions, alertKey) }
        when (state.empty) {
            MyTeasEmpty.NO_TEAS -> item(key = "no_teas") { NoTeas(actions.onAddFromCatalog) }

            MyTeasEmpty.NO_MATCH -> item(key = "no_match") {
                EmptyState(
                    title = stringResource(R.string.my_teas_no_match_title),
                    body = null,
                    actionLabel = stringResource(R.string.my_teas_reset_filters),
                    onAction = actions.onResetFilters,
                    illustration = noResultsIllustration(),
                    illustrationTint = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            null -> items(state.rows, key = { it.id }) { row ->
                MyTeaItem(
                    row = row,
                    onClick = { actions.onOpenTea(row.id) },
                    onRestore = { actions.onRestore(row.id) },
                    thumbnail = thumbnail(row.photoPath),
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = UmamiTheme.motion.standard(),
                    ),
                )
            }
        }
    }
}

/** The collection holds no tea at all: A7 alone, centred while it fits, scrollable at a large font scale. */
@Composable
private fun NoTeasAlone(onBrowseCatalog: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(bottom = BottomClearance),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoTeas(onBrowseCatalog)
        }
    }
}

@Composable
private fun NoTeas(onBrowseCatalog: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.my_teas_empty_title),
        body = stringResource(R.string.my_teas_empty_body),
        actionLabel = stringResource(R.string.my_teas_empty_action),
        onAction = onBrowseCatalog,
        illustration = painterResource(if (UmamiTheme.isDark) UmamiIcons.TeasEmptyDark else UmamiIcons.TeasEmptyLight),
        illustrationTint = null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun noResultsIllustration(): Painter =
    painterResource(if (UmamiTheme.isDark) UmamiIcons.NoResultsDark else UmamiIcons.NoResultsLight)
