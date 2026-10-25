package com.umamit.feature.catalog

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.SummaryPart
import com.umamit.core.data.catalog.summary
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.launch

/** The refresh sheet (SCR-22). */
const val CATALOG_REFRESH_SHEET_TAG = "catalog_refresh_sheet"

/** A successful refresh's summary line (CAT-07 AC3). */
const val CATALOG_REFRESH_SUMMARY_TAG = "catalog_refresh_summary"

/**
 * *Katalog aktualisieren* (SCR-22) as Material 3's modal bottom sheet: while [refresh] runs,
 * the pages and products done and *Abbrechen* ([onCancel], CAT-07 AC1); then the result: the
 * count of green teas and what changed („12 neu · 5 geändert · 2 nicht mehr im Sortiment ·
 * 3 Rezepte prüfen", CAT-07 AC3), or why nothing changed — with *Details kopieren* for the
 * developer (CAT-07 AC5). Closing it ([onDismissRequest]) never stops a running refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRefreshSheet(refresh: CatalogRefreshState, onCancel: () -> Unit, onDismissRequest: () -> Unit) {
    val sheetState = rememberCatalogSheetState(UmamiTheme.reduceMotion)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(CATALOG_REFRESH_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            when (refresh) {
                is CatalogRefreshState.Running -> Running(refresh.progress, onCancel)
                is CatalogRefreshState.Finished -> Result(refresh.outcome, onDismissRequest)
                CatalogRefreshState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ColumnScope.Running(progress: CatalogRefreshProgress, onCancel: () -> Unit) {
    Title(stringResource(R.string.catalog_refresh_running_title))
    if (progress.pagesTotal == 0) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Body(stringResource(R.string.catalog_refresh_starting))
    } else {
        LinearProgressIndicator(
            progress = { progress.pagesDone.toFloat() / progress.pagesTotal },
            modifier = Modifier.fillMaxWidth(),
        )
        Body(
            stringResource(
                R.string.catalog_refresh_progress,
                progress.pagesDone,
                progress.pagesTotal,
                progress.products,
            ),
        )
        // The photos come after the pages (CAT-08); none while SET-22 is off.
        if (progress.photos > 0) Body(stringResource(R.string.catalog_refresh_photos, progress.photos))
        // The guides come after the photos (CAT-09).
        if (progress.guides > 0) Body(stringResource(R.string.catalog_refresh_guides, progress.guides))
    }
    Body(stringResource(R.string.catalog_refresh_running_hint))
    Spacer(Modifier.height(UmamiSpacing.XSmall))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.catalog_refresh_cancel))
    }
}

@Composable
private fun ColumnScope.Result(outcome: CatalogRefreshOutcome, onClose: () -> Unit) {
    when (outcome) {
        is CatalogRefreshOutcome.Success -> {
            Title(stringResource(R.string.catalog_refresh_success_title))
            val separator = stringResource(R.string.catalog_refresh_summary_separator)
            Body(
                outcome.summary().map { summaryPart(it) }.joinToString(separator),
                Modifier.testTag(CATALOG_REFRESH_SUMMARY_TAG),
            )
            Close(stringResource(R.string.catalog_refresh_done), onClose)
        }

        CatalogRefreshOutcome.Cancelled -> {
            Title(stringResource(R.string.catalog_refresh_cancelled_title))
            Body(stringResource(R.string.catalog_refresh_kept))
            Close(stringResource(R.string.catalog_refresh_close), onClose)
        }

        is CatalogRefreshOutcome.Failure -> {
            Title(stringResource(R.string.catalog_refresh_failed_title))
            Body(stringResource(outcome.message()))
            // The shop-changed copy (03 §5) says it itself.
            if (outcome !is CatalogRefreshOutcome.ShopChanged) Body(stringResource(R.string.catalog_refresh_kept))
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            CopyDetails(outcome.details)
            Close(stringResource(R.string.catalog_refresh_close), onClose)
        }
    }
}

/** One part of the summary line (CAT-07 AC3): „274 Grüntees", „12 neu", „3 Rezepte prüfen", … */
@Composable
private fun summaryPart(part: SummaryPart): String = when (part) {
    is SummaryPart.Teas -> pluralStringResource(R.plurals.catalog_tea_count, part.count, part.count)

    is SummaryPart.New -> stringResource(R.string.catalog_refresh_summary_new, part.count)

    is SummaryPart.Changed -> stringResource(R.string.catalog_refresh_summary_changed, part.count)

    is SummaryPart.Discontinued -> stringResource(R.string.catalog_refresh_summary_discontinued, part.count)

    is SummaryPart.RecipesToCheck -> pluralStringResource(
        R.plurals.catalog_refresh_summary_recipes,
        part.count,
        part.count,
    )

    SummaryPart.NoChanges -> stringResource(R.string.catalog_refresh_summary_unchanged)
}

private fun CatalogRefreshOutcome.Failure.message(): Int = when (this) {
    is CatalogRefreshOutcome.Blocked -> R.string.catalog_refresh_blocked
    is CatalogRefreshOutcome.Offline -> R.string.catalog_refresh_offline
    is CatalogRefreshOutcome.ShopUnavailable -> R.string.catalog_refresh_unavailable
    is CatalogRefreshOutcome.ShopChanged -> R.string.catalog_refresh_shop_changed
    is CatalogRefreshOutcome.StorageFailed -> R.string.catalog_refresh_storage
}

/** *Details kopieren*: the technical details on the clipboard; Android confirms the copy itself (13+). */
@Composable
private fun CopyDetails(details: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.catalog_refresh_details_label)
    OutlinedButton(
        onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, details))) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.catalog_refresh_copy_details))
    }
}

@Composable
private fun Close(text: String, onClose: () -> Unit) {
    PillButton(text = text, onClick = onClose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The catalog's sheets (SCR-22, SCR-23): always fully expanded; under reduce motion it starts
 * expanded, so it appears without the slide (03 §3.6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberCatalogSheetState(reduceMotion: Boolean): SheetState {
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

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
