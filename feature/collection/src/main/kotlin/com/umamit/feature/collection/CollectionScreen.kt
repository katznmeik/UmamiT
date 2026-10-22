package com.umamit.feature.collection

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.theme.UmamiSpacing

/** Sammlung (SCR-08 / SCR-19), a calm placeholder until M2 and M3. */
@Composable
fun CollectionScreen(
    segment: CollectionSegment,
    onSegmentChange: (CollectionSegment) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
) {
    ScreenScaffold(
        title = stringResource(R.string.collection_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings) },
        scrollToTopRequest = scrollToTopRequest,
    ) {
        val segments = CollectionSegment.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
            segments.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == segment,
                    onClick = { onSegmentChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                ) {
                    Text(stringResource(entry.label))
                }
            }
        }
        when (segment) {
            CollectionSegment.MyTeas -> EmptyState(
                title = stringResource(R.string.collection_my_teas_empty_title),
                body = stringResource(R.string.collection_my_teas_empty_body),
            )

            CollectionSegment.Catalog -> EmptyState(
                title = stringResource(R.string.collection_catalog_empty_title),
                body = stringResource(R.string.collection_catalog_empty_body),
            )
        }
    }
}

private val CollectionSegment.label: Int
    get() = when (this) {
        CollectionSegment.MyTeas -> R.string.collection_segment_my_teas
        CollectionSegment.Catalog -> R.string.collection_segment_catalog
    }
