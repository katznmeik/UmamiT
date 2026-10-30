package com.umamit.feature.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.FlavourFilter

/** A change of the list's search, filters or sort (CAT-10): applied to the current [CatalogFilter]. */
typealias FilterChange = (CatalogFilter) -> CatalogFilter

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
 * and „Im Besitz".
 */
@Composable
internal fun CatalogFilterChips(
    filter: CatalogFilter,
    onFilterChange: (FilterChange) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val switches = switchOptions(filter)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
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
        CatalogChip(stringResource(R.string.catalog_filter_herbal), filter.herbal) {
            onFilterChange { it.withHerbal(!it.herbal) }
        }
        if (!filter.herbal) {
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
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
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
