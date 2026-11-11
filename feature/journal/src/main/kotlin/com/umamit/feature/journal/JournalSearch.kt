package com.umamit.feature.journal

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** The journal's search field (SCR-15, JRN-09). */
const val JOURNAL_SEARCH_TAG = "journal_search"

/** The filter icon beside the search; it opens the filter sheet. */
const val JOURNAL_FILTER_BUTTON_TAG = "journal_filter_button"

/** The dot on the filter icon while a filter is set. */
const val JOURNAL_FILTER_DOT_TAG = "journal_filter_dot"

private val ChipIconSize = 18.dp
private val DotSize = 8.dp
private const val CHIP_BORDER_ALPHA = 0.6f

/**
 * „Tees, Notizen …" with the filter icon beside it (JRN-09). The field shows [JournalFilter.search] itself, so a
 * reset and a keystroke can never disagree. The icon carries a dot while a filter is set.
 */
@Composable
internal fun JournalSearchRow(
    filter: JournalFilter,
    onFilterChange: (JournalFilter) -> Unit,
    onOpenFilter: () -> Unit,
) {
    val text = filter.search
    val focus = LocalFocusManager.current
    val change = { value: String -> onFilterChange(filter.copy(search = value)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall, top = UmamiSpacing.XXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = change,
            modifier = Modifier
                .weight(1f)
                .testTag(JOURNAL_SEARCH_TAG),
            placeholder = { Text(stringResource(R.string.journal_search_hint)) },
            leadingIcon = { Icon(painterResource(UmamiIcons.Search), contentDescription = null) },
            trailingIcon = if (text.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { change("") }) {
                        Icon(painterResource(UmamiIcons.Close), stringResource(R.string.journal_search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        val description = stringResource(
            if (filter.hasFilters) R.string.journal_filter_open_active else R.string.journal_filter_open,
        )
        IconButton(
            onClick = onOpenFilter,
            modifier = Modifier
                .testTag(JOURNAL_FILTER_BUTTON_TAG)
                .semantics { contentDescription = description },
        ) {
            Box {
                Icon(painterResource(UmamiIcons.Tune), contentDescription = null)
                if (filter.hasFilters) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(DotSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag(JOURNAL_FILTER_DOT_TAG),
                    )
                }
            }
        }
    }
}

/** The active filters as chips with ✕ under the search (JRN-09); a tap removes one. Nothing while none is set. */
@Composable
internal fun JournalFilterChips(filter: JournalFilter, teaName: String, onFilterChange: (JournalFilter) -> Unit) {
    if (!filter.hasFilters) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filter.teaKey != null) {
            RemovableChip(teaName) { onFilterChange(filter.copy(teaKey = null)) }
        }
        if (filter.minRating != null) {
            RemovableChip(ratingLabel(filter.minRating)) { onFilterChange(filter.copy(minRating = null)) }
        }
        if (filter.withNotes) {
            RemovableChip(stringResource(R.string.journal_filter_notes)) {
                onFilterChange(filter.copy(withNotes = false))
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val description = stringResource(R.string.journal_filter_remove, label)
    InputChip(
        selected = false,
        onClick = onRemove,
        modifier = Modifier.semantics { contentDescription = description },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        trailingIcon = {
            Icon(painterResource(UmamiIcons.Close), contentDescription = null, modifier = Modifier.size(ChipIconSize))
        },
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = false,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

/** „ab 3 ★", „ab 4 ★", „ab 4,5 ★" for a [JournalFilter.minRating]. */
@Composable
internal fun ratingLabel(minRating: Double): String = stringResource(
    when {
        minRating >= RATING_HIGH -> R.string.journal_filter_rating_45
        minRating >= RATING_MID -> R.string.journal_filter_rating_4
        else -> R.string.journal_filter_rating_3
    },
)

private const val RATING_HIGH = 4.5
private const val RATING_MID = 4.0
