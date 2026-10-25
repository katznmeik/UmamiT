package com.umamit.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Sammlung (SCR-08 / SCR-19), a calm placeholder until M2 and M3. *Meine Tees*
 * says where the teas are for now instead of an empty shelf: the seed teas
 * exist from the first start (#54 S2, GEN-09).
 */
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
        // The segments stay at the top (a centred column moved them with the placeholder's height).
        verticalArrangement = Arrangement.Top,
    ) {
        val segments = CollectionSegment.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        ) {
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
                title = stringResource(R.string.collection_my_teas_later_title),
                body = stringResource(R.string.collection_my_teas_later_body),
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
