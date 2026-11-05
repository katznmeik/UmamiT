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
import com.umamit.core.designsystem.component.OverflowItem
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Sammlung (SCR-08 / SCR-19): the segmented control [ Meine Tees | Katalog ] at the top and the
 * segment below it, each filling the rest of the screen and scrolling itself with the
 * [scrollToTopRequest] counter (03 §2.2, D-049). *Meine Tees* is [myTeasContent] (the app passes
 * [MyTeasRoute], D-100). *Katalog* is [catalogContent] (`feature:catalog`, wired by the app, since a
 * feature never depends on another, 06 §3). On *Katalog* the ⋮ menu offers
 * *Katalog aktualisieren* ([onRefreshCatalog], SCR-19, CAT-07) and *Link einfügen*
 * ([onPasteLink], a tea from a shop link, CAT-17), and while the catalog shows a view whose *Sorte* groups fold
 * ([onFoldCatalog] not null, D-098) *Alle einklappen* (`true`) and *Alle ausklappen* (`false`).
 */
@Composable
fun CollectionScreen(
    segment: CollectionSegment,
    onSegmentChange: (CollectionSegment) -> Unit,
    onOpenSettings: () -> Unit,
    catalogContent: @Composable (modifier: Modifier, scrollToTopRequest: Int) -> Unit,
    modifier: Modifier = Modifier,
    myTeasContent: @Composable (modifier: Modifier, scrollToTopRequest: Int) -> Unit = { _, _ -> },
    scrollToTopRequest: Int = 0,
    onRefreshCatalog: () -> Unit = {},
    onPasteLink: () -> Unit = {},
    onFoldCatalog: ((fold: Boolean) -> Unit)? = null,
) {
    val refreshLabel = stringResource(R.string.collection_catalog_refresh)
    val pasteLabel = stringResource(R.string.collection_catalog_paste_link)
    val foldLabel = stringResource(R.string.collection_catalog_fold_all)
    val unfoldLabel = stringResource(R.string.collection_catalog_unfold_all)
    val menuItems = if (segment == CollectionSegment.Catalog) {
        buildList {
            add(OverflowItem(refreshLabel, onRefreshCatalog))
            add(OverflowItem(pasteLabel, onPasteLink))
            if (onFoldCatalog != null) {
                add(OverflowItem(foldLabel) { onFoldCatalog(true) })
                add(OverflowItem(unfoldLabel) { onFoldCatalog(false) })
            }
        }
    } else {
        emptyList()
    }
    ScreenScaffold(
        title = stringResource(R.string.collection_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings, items = menuItems) },
        scrollToTopRequest = scrollToTopRequest,
        // The segments stay at the top.
        verticalArrangement = Arrangement.Top,
        // Both segments are lazy lists that scroll themselves below the segments.
        scrollable = false,
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
            CollectionSegment.MyTeas -> myTeasContent(Modifier.weight(1f), scrollToTopRequest)
            CollectionSegment.Catalog -> catalogContent(Modifier.weight(1f), scrollToTopRequest)
        }
    }
}

private val CollectionSegment.label: Int
    get() = when (this) {
        CollectionSegment.MyTeas -> R.string.collection_segment_my_teas
        CollectionSegment.Catalog -> R.string.collection_segment_catalog
    }
