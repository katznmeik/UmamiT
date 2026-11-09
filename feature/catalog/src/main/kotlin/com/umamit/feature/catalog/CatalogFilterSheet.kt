package com.umamit.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.FlavourFilter
import com.umamit.core.model.TasteScale
import com.umamit.core.model.TasteScaleOption
import com.umamit.core.model.UmamiLevel

/** The filter sheet (SCR-19). */
const val CATALOG_FILTER_SHEET_TAG = "catalog_filter_sheet"

/**
 * Every filter and the sort (CAT-10) in Material 3's modal sheet: sort, *Sorte* and *Herkunft*
 * (several each, from the catalog's own values in [listing]), aroma, umami and the chips'
 * switches, and the shop's taste notes and scales of the view (D-108 point 8). In the *Kräutertee*
 * view aroma („Pur", Q14) and umami (Q19) don't apply and are left out. *Zurücksetzen* returns to the default
 * view (D-073) of the kind shown and keeps the search.
 * The caller never composes it during an alert (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogFilterSheet(
    filter: CatalogFilter,
    listing: CatalogListing,
    onFilterChange: (FilterChange) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberCatalogSheetState(UmamiTheme.reduceMotion),
        modifier = Modifier.testTag(CATALOG_FILTER_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
        ) {
            Text(
                text = stringResource(R.string.catalog_filter_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            SortGroup(filter, onFilterChange)
            ChoiceGroup(stringResource(R.string.catalog_filter_types), listing.types, filter.types) { value ->
                onFilterChange { it.copy(types = it.types.toggle(value)) }
            }
            ChoiceGroup(stringResource(R.string.catalog_filter_origins), listing.origins, filter.origins) { value ->
                onFilterChange { it.copy(origins = it.origins.toggle(value)) }
            }
            if (filter.isGreen) {
                FlavourGroup(filter, onFilterChange)
                UmamiGroup(filter, onFilterChange)
            }
            TasteNoteGroup(filter, listing, onFilterChange)
            listing.tasteScales.forEach { scale -> TasteScaleGroup(scale, filter, onFilterChange) }
            val switches = switchOptions(filter)
            Group(stringResource(R.string.catalog_filter_more)) {
                switches.forEach { option ->
                    CatalogChip(option.label, option.selected) { onFilterChange(option.toggle) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFilterChange { it.resetFilters() } }) {
                    Text(stringResource(R.string.catalog_filter_reset))
                }
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.catalog_filter_done)) }
            }
        }
    }
}

@Composable
private fun SortGroup(filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    val options = listOf(
        CatalogSort.TYPE to stringResource(R.string.catalog_sort_type),
        CatalogSort.NAME to stringResource(R.string.catalog_sort_name),
        CatalogSort.PRICE to stringResource(R.string.catalog_sort_price),
    )
    Group(stringResource(R.string.catalog_filter_sort)) {
        options.forEach { (sort, label) ->
            CatalogChip(label, filter.sort == sort) { onFilterChange { it.copy(sort = sort) } }
        }
    }
}

@Composable
private fun FlavourGroup(filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    val options = listOf(
        FlavourFilter.ALL to stringResource(R.string.catalog_flavour_all),
        FlavourFilter.PURE to stringResource(R.string.catalog_flavour_pure),
        FlavourFilter.FLAVOURED to stringResource(R.string.catalog_flavour_flavoured),
    )
    Group(stringResource(R.string.catalog_filter_flavour)) {
        options.forEach { (flavour, label) ->
            CatalogChip(label, filter.flavour == flavour) { onFilterChange { it.copy(flavour = flavour) } }
        }
    }
}

@Composable
private fun UmamiGroup(filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    val options = listOf(
        null to stringResource(R.string.catalog_umami_any),
        UmamiLevel.MEDIUM to stringResource(R.string.catalog_umami_medium),
        UmamiLevel.HIGH to stringResource(R.string.catalog_umami_high),
        UmamiLevel.VERY_HIGH to stringResource(R.string.catalog_umami_very_high),
    )
    Group(stringResource(R.string.catalog_filter_umami)) {
        options.forEach { (level, label) ->
            CatalogChip(label, filter.minUmami == level) { onFilterChange { it.copy(minUmami = level) } }
        }
    }
}

/** The shop's taste notes of the view (D-108 point 8), most frequent first; a tea must name every one chosen. */
@Composable
private fun TasteNoteGroup(filter: CatalogFilter, listing: CatalogListing, onFilterChange: (FilterChange) -> Unit) {
    if (listing.tasteNotes.isNotEmpty()) {
        Group(stringResource(R.string.catalog_filter_taste_notes)) {
            listing.tasteNotes.forEach { note ->
                CatalogChip(note.label, note.key in filter.notes) { onFilterChange { it.toggleNote(note.key) } }
            }
        }
    }
}

/**
 * One of the shop's taste scales (D-108 point 8) like Umami: *Egal* or „ab N" leaves of [TasteScale.MAX_LEAVES].
 * TalkBack reads each chip with the scale's name („Süße ab 3"), since „ab 3" alone names no scale.
 */
@Composable
private fun TasteScaleGroup(scale: TasteScaleOption, filter: CatalogFilter, onFilterChange: (FilterChange) -> Unit) {
    val chosen = filter.minScales[scale.key]
    Group(scale.label) {
        CatalogChip(
            stringResource(R.string.catalog_scale_any),
            chosen == null,
            description = stringResource(R.string.catalog_scale_any_description, scale.label),
        ) { onFilterChange { it.withMinScale(scale.key, null) } }
        for (leaves in 1..TasteScale.MAX_LEAVES) {
            CatalogChip(
                stringResource(R.string.catalog_scale_min, leaves),
                chosen == leaves,
                description = stringResource(R.string.catalog_scale_min_description, scale.label, leaves),
            ) { onFilterChange { it.withMinScale(scale.key, leaves) } }
        }
    }
}

/** A multiple choice from the catalog's own [values]; [selected] empty = every one. */
@Composable
private fun ChoiceGroup(title: String, values: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (values.isNotEmpty()) {
        Group(title) {
            values.forEach { value -> CatalogChip(value, value in selected) { onToggle(value) } }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = UmamiSpacing.Medium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
        ) { content() }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

/** *Zurücksetzen*: the default view (D-073) of the kind shown, the search kept; taste notes and scales cleared too. */
internal fun CatalogFilter.resetFilters(): CatalogFilter = CatalogFilter.DEFAULT.copy(search = search, kind = kind)
