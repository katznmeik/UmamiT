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
 * Sammlung (SCR-08 / SCR-19): the segmented control [ Meine Tees | Katalog ] at the top and the
 * segment below it. *Meine Tees* is a calm placeholder until M3 that says where the teas are for
 * now instead of an empty shelf: the seed teas exist from the first start (#54 S2, GEN-09).
 * *Katalog* is [catalogContent] (`feature:catalog`, wired by the app, since a feature never
 * depends on another, 06 §3), which fills the rest of the screen and scrolls itself; it gets
 * the [scrollToTopRequest] counter (03 §2.2, D-049).
 */
@Composable
fun CollectionScreen(
    segment: CollectionSegment,
    onSegmentChange: (CollectionSegment) -> Unit,
    onOpenSettings: () -> Unit,
    catalogContent: @Composable (modifier: Modifier, scrollToTopRequest: Int) -> Unit,
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
        // The catalog is a lazy list that scrolls itself below the segments.
        scrollable = segment == CollectionSegment.MyTeas,
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

            CollectionSegment.Catalog -> catalogContent(Modifier.weight(1f), scrollToTopRequest)
        }
    }
}

private val CollectionSegment.label: Int
    get() = when (this) {
        CollectionSegment.MyTeas -> R.string.collection_segment_my_teas
        CollectionSegment.Catalog -> R.string.collection_segment_catalog
    }
