package com.umamit.feature.catalog

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.FlavourFilter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** A change of the list's search, filters or sort (CAT-10): applied to the current [CatalogFilter]. */
typealias FilterChange = (CatalogFilter) -> CatalogFilter

/** The chip row's item key in the catalog list. */
internal const val CHIPS_KEY = "chips"

/** The saved key of the view without a kind (D-114); no kind's key. */
private const val NO_KIND = "-"

/** The catalog's search field (SCR-19). */
const val CATALOG_SEARCH_TAG = "catalog_search"

/**
 * The search over name, secondary name, description, cultivar and region (CAT-10). The field
 * keeps its own text, so typing never waits for the list; it starts from the filter's, so it
 * resets with the filters after a process death (D-083, D-090).
 */
@Composable
internal fun CatalogSearchField(filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    var text by remember { mutableStateOf(filter.search) }
    val focus = LocalFocusManager.current
    val change = { value: String ->
        text = value
        onFilterChange { it.copy(search = value) }
    }
    OutlinedTextField(
        value = text,
        onValueChange = change,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall)
            .testTag(CATALOG_SEARCH_TAG),
        label = { Text(stringResource(R.string.catalog_search_label)) },
        placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
        leadingIcon = { Icon(painterResource(UmamiIcons.Search), contentDescription = null) },
        trailingIcon = if (text.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { change("") }) {
                    Icon(painterResource(UmamiIcons.Close), stringResource(R.string.catalog_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

/**
 * The frequent filters as chips (SCR-19, D-076): *Filter* opens the sheet with the rest, then
 * *Gemerkt* (v0.2.2, CAT-18: the marked teas, the other chips hidden while it is on),
 * *Kräutertee* (v0.2.1, D-093: the Kräutertees instead of the green teas, and back), „Pur" (not
 * in the Kräutertee view, Q14), „Vorrätig" (both on in the default view, D-073), „Bio", „Neu"
 * and „Im Besitz". The row scrolls to the selected kind's chip whenever it changes (#206): nine kinds are wider
 * than the screen, and *Alle zeigen* may pick one off it. A row that comes back into the list keeps its position.
 */
@Composable
internal fun CatalogFilterChips(
    filter: CatalogFilter,
    onFilterChange: (FilterChange) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val switches = switchOptions(filter)
    val scroll = rememberScrollState()
    // Each kind chip's start and width in the row's content (px), to scroll the selected one into view (#206).
    val kindChips = remember { mutableStateMapOf<CatalogKind, IntRange>() }
    val side = with(LocalDensity.current) { UmamiSpacing.SideMargin.roundToPx() }
    val motion = UmamiTheme.motion
    // The kind last brought into view; saved, so a row that scrolls back into the list keeps the user's own
    // position (motion review v0.5.0 S2): the row reveals the kind only when it really changes.
    var revealedKind by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(filter.kind, filter.marked) {
        if (filter.marked) {
            revealedKind = null // the row is rebuilt when *Gemerkt* ends: reveal again then
            return@LaunchedEffect
        }
        val kind = filter.kind
        val shownKey = kind?.key ?: NO_KIND
        if (revealedKind == shownKey) return@LaunchedEffect
        val instant = revealedKind == null // first shown, or rebuilt after *Gemerkt*: no travel
        val spec: AnimationSpec<Float> = if (instant) snap() else motion.standard()
        withFrameNanos { } // this frame's spans, after a *Filter* label that changed with the kind
        if (kind == null) {
            // No kind (D-114): the row goes back to its start, *Filter* first.
            if (scroll.value != 0) scroll.animateScrollTo(0, spec)
        } else {
            scroll.reveal(snapshotFlow { kindChips[kind] }.filterNotNull().first(), side, spec)
        }
        revealedKind = shownKey
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // *Gemerkt* (CAT-18): on, only the search applies, so every other chip is hidden, *Filter* too.
        if (filter.marked) {
            MarkedChip(filter, onFilterChange)
            return@Row
        }
        val changed = filter.changedFilters
        CatalogChip(
            label = if (changed > 0) {
                stringResource(R.string.catalog_filter_button_count, changed)
            } else {
                stringResource(R.string.catalog_filter_button)
            },
            selected = changed > 0,
            onClick = onOpenFilters,
            leadingIcon = {
                Icon(
                    painterResource(UmamiIcons.Tune),
                    contentDescription = null,
                    modifier = Modifier.size(ChipIconSize),
                )
            },
        )
        MarkedChip(filter, onFilterChange)
        // The kinds in the chip order (D-108); none selected is the default view, every tea (D-114).
        CatalogKind.ALL.forEach { kind ->
            key(kind.key) {
                Box(
                    Modifier.onPlaced { chip ->
                        val start = side + chip.positionInParent().x.roundToInt()
                        kindChips[kind] = start until start + chip.size.width
                    },
                ) {
                    // One kind at a time; a tap on the selected one clears it, never back to *Grüntee* (D-114).
                    CatalogChip(stringResource(kind.chipLabel), filter.kind == kind) {
                        onFilterChange { it.toggleKind(kind) }
                    }
                }
            }
        }
        if (filter.isGreen) {
            CatalogChip(stringResource(R.string.catalog_filter_pure), filter.flavour == FlavourFilter.PURE) {
                onFilterChange {
                    it.copy(flavour = if (it.flavour == FlavourFilter.PURE) FlavourFilter.ALL else FlavourFilter.PURE)
                }
            }
        }
        switches.forEach { option ->
            CatalogChip(option.label, option.selected) { onFilterChange(option.toggle) }
        }
    }
}

/**
 * A kind change from off the chip row (*Alle zeigen*) brings the row at [chipsIndex] back at once: the old list's
 * index would open the new kind mid-list, its chip out of sight (motion review v0.5.0 N1). From a chip the row is on
 * screen and nothing moves; no animated travel through content that was just replaced.
 */
@Composable
internal fun ChipRowOnKindChange(listState: LazyListState, kind: CatalogKind?, chipsIndex: Int) {
    val shownKey = kind?.key ?: NO_KIND
    var listedKind by rememberSaveable { mutableStateOf(shownKey) }
    val index by rememberUpdatedState(chipsIndex)
    LaunchedEffect(shownKey) {
        if (shownKey == listedKind) return@LaunchedEffect
        listedKind = shownKey
        if (listState.layoutInfo.visibleItemsInfo.none { it.key == CHIPS_KEY }) listState.scrollToItem(index)
    }
}

/**
 * Scrolls the row just enough to show the [chip] (its span in the content, px) with the side margin [side] around it;
 * nothing when it is in view already. Only the row scrolls, never the list around it.
 */
private suspend fun ScrollState.reveal(chip: IntRange, side: Int, spec: AnimationSpec<Float>) {
    val target = when {
        chip.first - side < value -> chip.first - side
        chip.last + 1 + side > value + viewportSize -> chip.last + 1 + side - viewportSize
        else -> return
    }.coerceIn(0, maxValue)
    // A motion token (S3), `snap()` under reduce motion; a finger on the row cancels it through the scroll mutex.
    if (target != value) animateScrollTo(target, spec)
}

/** *Gemerkt* (CAT-18, D-097): the Merkliste's view, and back to the view before it. */
@Composable
private fun MarkedChip(filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    CatalogChip(stringResource(R.string.catalog_filter_marked), filter.marked) {
        onFilterChange { it.copy(marked = !it.marked) }
    }
}

/** An on/off filter of the chips and the sheet: its [label], whether it is on, and how a tap changes the filter. */
internal class SwitchOption(val label: String, val selected: Boolean, val toggle: FilterChange)

/** „Vorrätig", „Bio", „Neu" and „Im Besitz" (CAT-10). */
@Composable
internal fun switchOptions(filter: CatalogFilter): List<SwitchOption> = listOf(
    SwitchOption(stringResource(R.string.catalog_filter_available), filter.availableOnly) {
        it.copy(availableOnly = !it.availableOnly)
    },
    SwitchOption(stringResource(R.string.catalog_filter_organic), filter.organicOnly) {
        it.copy(organicOnly = !it.organicOnly)
    },
    SwitchOption(stringResource(R.string.catalog_filter_new), filter.newOnly) { it.copy(newOnly = !it.newOnly) },
    SwitchOption(stringResource(R.string.catalog_filter_owned), filter.ownedOnly) {
        it.copy(ownedOnly = !it.ownedOnly)
    },
)

/** A filter chip of the catalog, outlined at ≥ 3:1 against the background while off (WCAG 1.4.11, D-065). */
@Composable
internal fun CatalogChip(
    label: String,
    selected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    description: String? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leadingIcon,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

private const val CHIP_BORDER_ALPHA = 0.6f
private val ChipIconSize = 18.dp
