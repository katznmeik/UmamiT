package com.umamit.feature.journal

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.launch

/** The journal's filter sheet (SCR-15, JRN-09). */
const val JOURNAL_FILTER_SHEET_TAG = "journal_filter_sheet"

private const val CHIP_BORDER_ALPHA = 0.6f
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp

/**
 * *Filter* (JRN-09, D-112): *Tee* (the teas that have sessions, [teas]; one at a time), *Bewertung* (*ab 3 ★* ·
 * *ab 4 ★* · *ab 4,5 ★*; one at a time), *Mit Notizen*, and *Zurücksetzen* at the end, which clears the filters and
 * keeps the search. The title row with *Fertig* stays in place above the scrolling groups (D-110). A chip chosen
 * again is unset. The caller never composes it during an alert (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalFilterSheet(
    filter: JournalFilter,
    teas: List<JournalTeaOption>,
    onFilterChange: (JournalFilter) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberJournalSheetState(reduceMotion)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    // Set by *Fertig*: the sheet is on its way out, so a second tap does nothing.
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            scope.launch { if (!reduceMotion) sheetState.hide() }.invokeOnCompletion {
                // Also fires on cancellation: dismiss only once hidden (M3 pattern), else stay usable.
                if (reduceMotion || !sheetState.isVisible) onDismissRequest() else leaving = false
            }
        }
    }
    // The hairline under the title row shows that groups scroll under it.
    val dividerAlpha by animateFloatAsState(
        targetValue = if (scroll.canScrollBackward) 1f else 0f,
        animationSpec = UmamiTheme.motion.fade(),
        label = "journalFilterSheetDivider",
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(JOURNAL_FILTER_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.journal_filter_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = leave) { Text(stringResource(R.string.journal_filter_done)) }
        }
        HorizontalDivider(
            modifier = Modifier.graphicsLayer {
                alpha = dividerAlpha
            },
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
        ) {
            if (teas.isNotEmpty()) {
                Group(stringResource(R.string.journal_filter_tea)) {
                    teas.forEach { tea ->
                        SheetChip(tea.name, filter.teaKey == tea.key) {
                            onFilterChange(filter.copy(teaKey = if (filter.teaKey == tea.key) null else tea.key))
                        }
                    }
                }
            }
            RatingGroup(filter, onFilterChange)
            Group(stringResource(R.string.journal_filter_notes)) {
                SheetChip(stringResource(R.string.journal_filter_notes), filter.withNotes) {
                    onFilterChange(filter.copy(withNotes = !filter.withNotes))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onFilterChange(filter.withoutFilters()) }) {
                    Text(stringResource(R.string.journal_filter_reset))
                }
            }
        }
    }
}

@Composable
private fun RatingGroup(filter: JournalFilter, onFilterChange: (JournalFilter) -> Unit) {
    Group(stringResource(R.string.journal_filter_rating)) {
        JournalFilter.RATING_STEPS.forEach { step ->
            val description = stringResource(
                when (step) {
                    JournalFilter.RATING_STEPS[0] -> R.string.journal_filter_rating_3_description
                    JournalFilter.RATING_STEPS[1] -> R.string.journal_filter_rating_4_description
                    else -> R.string.journal_filter_rating_45_description
                },
            )
            SheetChip(ratingLabel(step), filter.minRating == step, description) {
                onFilterChange(filter.copy(minRating = if (filter.minRating == step) null else step))
            }
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

/** A filter chip, outlined at >= 3:1 against the background while off (WCAG 1.4.11, D-065). */
@Composable
private fun SheetChip(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

/** Always fully expanded; under reduce motion it starts expanded, so it appears without the slide (03 §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberJournalSheetState(reduceMotion: Boolean): SheetState {
    val density = LocalDensity.current
    return remember(reduceMotion) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
}
