package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.theme.UmamiSpacing

private val BottomClearance = 72.dp

/**
 * The vertical scroll every tab's content is (SCR-18): [scrollState] belongs to the shell, so a tab keeps its place
 * across a swipe and a re-selection of the Statistik tab scrolls it to the top (D-049).
 */
@Composable
fun StatsTabColumn(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomClearance),
        content = content,
    )
}

/**
 * One section of a tab (SCR-18): its overline [title], then [content]. With [isEmpty] (nothing in the range) the
 * body reads „In diesem Zeitraum nichts" quietly instead (INS-08, D-115 Q15).
 */
@Composable
fun StatsSection(
    title: String,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = UmamiSpacing.Medium)) {
        SectionHeader(title = title)
        if (isEmpty) {
            Text(
                text = stringResource(R.string.stats_section_empty),
                modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            content()
        }
    }
}
