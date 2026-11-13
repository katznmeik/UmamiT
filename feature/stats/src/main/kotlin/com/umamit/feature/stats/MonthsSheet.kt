package com.umamit.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.MonthSpan
import kotlinx.coroutines.launch
import java.time.YearMonth

/** The test tag of the *Monate* sheet (INS-07, R7). */
const val STATS_MONTHS_SHEET_TAG = "stats_months_sheet"

/** The test tags of the two month values, „Dez. 2025". */
const val STATS_MONTH_FROM_TAG = "stats_month_from"
const val STATS_MONTH_TO_TAG = "stats_month_to"

private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
private const val HALF_TURN = 180f

/**
 * The *Monate* sheet (INS-07, D-115 Amendment 1, R7): *Von* and *Bis*, each a month stepped with ‹ › within [bounds]
 * (the first session's month … the current one), *Bis* never before *Von*. Every step is reported at once through
 * [onChange], so the figures behind the sheet follow; *Fertig* closes it. Never composed during an alert (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthsSheet(
    bounds: MonthSpan,
    selected: MonthSpan,
    onChange: (MonthSpan) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberMonthsSheetState(reduceMotion)
    val scope = rememberCoroutineScope()
    // The months being stepped: two quick taps both count, without waiting for the saved choice to come back.
    var draft by remember { mutableStateOf(selected) }
    val step = { span: MonthSpan ->
        draft = span
        onChange(span)
    }
    // Set by *Fertig*: the sheet is on its way out, so a second tap does nothing.
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            scope.launch { if (!reduceMotion) sheetState.hide() }.invokeOnCompletion {
                if (reduceMotion || !sheetState.isVisible) onDismissRequest() else leaving = false
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(STATS_MONTHS_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stats_months_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = leave) { Text(stringResource(R.string.stats_months_done)) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = UmamiSpacing.SideMargin)
                .padding(bottom = UmamiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
        ) {
            MonthPicker(
                label = stringResource(R.string.stats_months_from),
                month = draft.from,
                range = bounds.from..draft.to,
                tag = STATS_MONTH_FROM_TAG,
                onChange = { step(MonthSpan(it, draft.to)) },
            )
            MonthPicker(
                label = stringResource(R.string.stats_months_to),
                month = draft.to,
                range = draft.from..bounds.to,
                tag = STATS_MONTH_TO_TAG,
                onChange = { step(MonthSpan(draft.from, it)) },
            )
        }
    }
}

/** One picker: its [label] („Von"), the [month] between two arrows; the arrows stop at the ends of [range]. */
@Composable
private fun MonthPicker(
    label: String,
    month: YearMonth,
    range: ClosedRange<YearMonth>,
    tag: String,
    onChange: (YearMonth) -> Unit,
) {
    val earlier = stringResource(R.string.stats_months_earlier, label)
    val later = stringResource(R.string.stats_months_later, label)
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onChange(month.minusMonths(1)) },
                enabled = month > range.start,
                modifier = Modifier.semantics { contentDescription = earlier },
            ) {
                Icon(
                    painter = painterResource(UmamiIcons.ChevronRight),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = HALF_TURN },
                )
            }
            Text(
                text = month.fullLabel(),
                modifier = Modifier
                    .weight(1f)
                    .testTag(tag),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { onChange(month.plusMonths(1)) },
                enabled = month < range.endInclusive,
                modifier = Modifier.semantics { contentDescription = later },
            ) {
                Icon(painter = painterResource(UmamiIcons.ChevronRight), contentDescription = null)
            }
        }
    }
}

/** Always fully expanded; under reduce motion it starts expanded, so it appears without the slide (03 §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberMonthsSheetState(reduceMotion: Boolean): SheetState {
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
