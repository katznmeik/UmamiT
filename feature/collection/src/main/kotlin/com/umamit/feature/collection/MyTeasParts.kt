package com.umamit.feature.collection

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.umamit.core.common.stockGramsText
import com.umamit.core.designsystem.component.CheckRecipeBadge
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TeaCategory

/** The search field of *Meine Tees* (SCR-08). */
const val MY_TEAS_SEARCH_TAG = "my_teas_search"

/** The sort chip of *Meine Tees* (TEA-11). */
const val MY_TEAS_SORT_TAG = "my_teas_sort"

private const val CHIP_BORDER_ALPHA = 0.6f
private const val DROPDOWN_ROTATION = 90f
private val ChipIconSize = 18.dp

/**
 * „Deine Tees durchsuchen …" (TEA-09): over name, secondary name, vendor, region, cultivar and notes. The field
 * keeps its own text, so typing never waits for the list, and follows the filter when it is reset.
 */
@Composable
internal fun MyTeasSearchField(filter: CollectionFilter, onFilterChange: (FilterChange) -> Unit) {
    var text by remember { mutableStateOf(filter.search) }
    LaunchedEffect(filter.search) { if (filter.search != text) text = filter.search }
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
            .testTag(MY_TEAS_SEARCH_TAG),
        placeholder = { Text(stringResource(R.string.my_teas_search_hint)) },
        leadingIcon = { Icon(painterResource(UmamiIcons.Search), contentDescription = null) },
        trailingIcon = if (text.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { change("") }) {
                    Icon(painterResource(UmamiIcons.Close), stringResource(R.string.my_teas_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

/**
 * The sort and the filter chips (TEA-10, TEA-11): the sort first, then *Favoriten*, *Kategorie* and *Sorte*
 * (each only while the collection has more than one value, or one is chosen), *Vorrat* (*Knapp* / *Leer*),
 * *Aromatisiert* and *Archiviert*. A chip with values opens a menu to tick them; their menus give way to an
 * alert (rule 8).
 */
@Composable
internal fun MyTeasChips(state: MyTeasUiState, actions: MyTeasActions, alerting: Boolean) {
    val filter = state.filter
    val change = actions.onFilterChange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortChip(state.sort, actions.onSortChange, alerting)
        ToggleChip(stringResource(R.string.my_teas_filter_favorites), filter.favoritesOnly) {
            change { it.copy(favoritesOnly = !it.favoritesOnly) }
        }
        if (state.categories.size > 1 || filter.categories.isNotEmpty()) {
            val labels = (state.categories + filter.categories).distinct().associateWith { stringResource(it.label) }
            MenuChip(
                name = stringResource(R.string.my_teas_filter_category),
                options = labels.toList(),
                chosen = filter.categories,
                alerting = alerting,
            ) { category -> change { it.copy(categories = it.categories.toggle(category)) } }
        }
        if (state.sorten.size > 1 || filter.sorten.isNotEmpty()) {
            MenuChip(
                name = stringResource(R.string.my_teas_filter_sorte),
                options = (state.sorten + filter.sorten).distinct().map { it to it },
                chosen = filter.sorten,
                alerting = alerting,
            ) { sorte -> change { it.copy(sorten = it.sorten.toggle(sorte)) } }
        }
        MenuChip(
            name = stringResource(R.string.my_teas_filter_stock),
            options = listOf(
                StockLevel.LOW to stringResource(R.string.my_teas_stock_low),
                StockLevel.EMPTY to stringResource(R.string.my_teas_stock_empty),
            ),
            chosen = filter.stock,
            alerting = alerting,
        ) { level -> change { it.copy(stock = it.stock.toggle(level)) } }
        ToggleChip(stringResource(R.string.my_teas_filter_flavored), filter.flavoredOnly) {
            change { it.copy(flavoredOnly = !it.flavoredOnly) }
        }
        ToggleChip(stringResource(R.string.my_teas_filter_archived), filter.archived) {
            change { it.copy(archived = !it.archived) }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** The sort (TEA-11): the chip names the order; its menu offers the four. */
@Composable
private fun SortChip(sort: CollectionSort, onSortChange: (CollectionSort) -> Unit, alerting: Boolean) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(sort.label)
    val description = stringResource(R.string.my_teas_sort_description, label)
    Box {
        MyTeasChip(
            label = label,
            selected = false,
            onClick = { if (!alerting) open = true },
            dropdown = true,
            modifier = Modifier
                .testTag(MY_TEAS_SORT_TAG)
                .semantics { contentDescription = description },
        )
        DropdownMenu(expanded = open && !alerting, onDismissRequest = { open = false }) {
            CollectionSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                    leadingIcon = { CheckMark(option == sort) },
                )
            }
        }
    }
}

/**
 * A chip with values to tick (*Kategorie*, *Sorte*, *Vorrat*): it names the one chosen, „Sorte (2)" for more,
 * else [name]; its menu stays open while values are ticked.
 */
@Composable
private fun <T> MenuChip(
    name: String,
    options: List<Pair<T, String>>,
    chosen: Set<T>,
    alerting: Boolean,
    onToggle: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = when (chosen.size) {
        0 -> name
        1 -> options.firstOrNull { it.first in chosen }?.second ?: name
        else -> stringResource(R.string.my_teas_filter_count, name, chosen.size)
    }
    Box {
        MyTeasChip(label = label, selected = chosen.isNotEmpty(), onClick = {
            if (!alerting) open = true
        }, dropdown = true)
        DropdownMenu(expanded = open && !alerting, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onToggle(value) },
                    leadingIcon = { CheckMark(value in chosen) },
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) =
    MyTeasChip(label = label, selected = selected, onClick = onClick)

/** A filter chip, outlined at ≥ 3:1 against the background while off (WCAG 1.4.11, D-065), as the catalog's. */
@Composable
private fun MyTeasChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropdown: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        trailingIcon = if (dropdown) {
            {
                Icon(
                    painterResource(UmamiIcons.ChevronRight),
                    contentDescription = null,
                    modifier = Modifier
                        .size(ChipIconSize)
                        .rotate(DROPDOWN_ROTATION),
                )
            }
        } else {
            null
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

@Composable
private fun CheckMark(checked: Boolean) {
    if (checked) {
        Icon(painterResource(UmamiIcons.Check), contentDescription = null)
    } else {
        Box(Modifier.size(24.dp))
    }
}

/**
 * A tea's row (TEA-08): cover, serif name, secondary name, Sorte, the stock chip and „Rezept prüfen", the
 * favourite's star; an archived tea ends with *Wiederherstellen*. TalkBack reads the row as one item, the stock
 * as „38 g übrig" / „Knapp, 8 g übrig" / „Leer", the star as „Favorit".
 */
@Composable
internal fun MyTeaItem(
    row: MyTeaRow,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    thumbnail: Painter?,
    modifier: Modifier = Modifier,
) {
    val hasBadges = row.stockLevel != null || row.checkRecipe
    TeaListItem(
        title = row.name,
        subtitle = row.secondaryName,
        meta = row.sorte ?: stringResource(row.category.label),
        modifier = modifier.testTag(myTeaRowTag(row.id)),
        onClick = onClick,
        thumbnail = thumbnail,
        placeholderColor = placeholderColor(row.sorte),
        badges = if (hasBadges) {
            {
                StockChip(row)
                if (row.checkRecipe) CheckRecipeBadge()
            }
        } else {
            null
        },
        trailing = when {
            row.isArchived -> {
                {
                    val description = stringResource(R.string.my_teas_restore_description, row.name)
                    TextButton(
                        onClick = onRestore,
                        modifier = Modifier.semantics {
                            contentDescription = description
                        },
                    ) {
                        Text(stringResource(R.string.my_teas_restore))
                    }
                }
            }

            row.isFavorite -> {
                {
                    Icon(
                        painter = painterResource(UmamiIcons.StarFilled),
                        contentDescription = stringResource(R.string.my_teas_favorite),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            else -> null
        },
    )
}

/** „38 g", *Knapp* or *Leer* (TEA-08, INV-05); nothing for a tea without stock events. */
@Composable
private fun StockChip(row: MyTeaRow) {
    val level = row.stockLevel ?: return
    val grams = stockGramsText(row.remainingGrams ?: 0.0)
    val colors = MaterialTheme.colorScheme
    val (text, description) = when (level) {
        StockLevel.AVAILABLE ->
            stringResource(R.string.my_teas_stock_grams, grams) to stringResource(R.string.my_teas_stock_left, grams)

        StockLevel.LOW ->
            stringResource(R.string.my_teas_stock_low) to stringResource(R.string.my_teas_stock_low_description, grams)

        StockLevel.EMPTY ->
            stringResource(R.string.my_teas_stock_empty) to stringResource(R.string.my_teas_stock_empty_description)
    }
    val container = when (level) {
        StockLevel.AVAILABLE -> colors.surfaceContainerHigh
        StockLevel.LOW -> colors.secondaryContainer
        StockLevel.EMPTY -> colors.errorContainer
    }
    val content = when (level) {
        StockLevel.AVAILABLE -> colors.onSurface
        StockLevel.LOW -> colors.onSecondaryContainer
        StockLevel.EMPTY -> colors.onErrorContainer
    }
    Surface(
        shape = CircleShape,
        color = container,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            // The same height as „Rezept prüfen" beside it (CheckRecipeBadge).
            modifier = Modifier.padding(horizontal = UmamiSpacing.XSmall, vertical = UmamiSpacing.XXSmall),
        )
    }
}

/** The leaf placeholder's colour by the tea's *Sorte* (03 §3.5), for a tea without a photo. */
@Composable
private fun placeholderColor(sorte: String?): Color {
    val colors = UmamiTheme.extendedColors.teaTypes
    val type = sorte.orEmpty().lowercase()
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

@get:StringRes
internal val TeaCategory.label: Int
    get() = when (this) {
        TeaCategory.GREEN -> R.string.my_teas_category_green
        TeaCategory.WHITE -> R.string.my_teas_category_white
        TeaCategory.YELLOW -> R.string.my_teas_category_yellow
        TeaCategory.OOLONG -> R.string.my_teas_category_oolong
        TeaCategory.BLACK -> R.string.my_teas_category_black
        TeaCategory.DARK -> R.string.my_teas_category_dark
        TeaCategory.HERBAL -> R.string.my_teas_category_herbal
        TeaCategory.OTHER -> R.string.my_teas_category_other
    }

@get:StringRes
private val CollectionSort.label: Int
    get() = when (this) {
        CollectionSort.LAST_BREWED -> R.string.my_teas_sort_last_brewed
        CollectionSort.NAME -> R.string.my_teas_sort_name
        CollectionSort.RECENTLY_ADDED -> R.string.my_teas_sort_recently_added
        CollectionSort.STOCK -> R.string.my_teas_sort_stock
    }
