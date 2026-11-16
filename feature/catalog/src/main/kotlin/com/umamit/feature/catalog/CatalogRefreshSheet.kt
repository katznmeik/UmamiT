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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.reasonRes
import com.umamit.core.data.catalog.summaryText
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.rememberExpandedSheetState
import com.umamit.core.ui.rememberSheetClose
import kotlinx.coroutines.launch
import com.umamit.core.data.R as DataR

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
    val sheetState = rememberExpandedSheetState(UmamiTheme.reduceMotion)
    // *Fertig* and *Schließen* slide the sheet out like a drag, then close it (A-13).
    val close = rememberSheetClose(sheetState, onDismissRequest)
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
                is CatalogRefreshState.Finished -> Result(refresh.outcome, close)
                CatalogRefreshState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ColumnScope.Running(progress: CatalogRefreshProgress, onCancel: () -> Unit) {
    Title(stringResource(DataR.string.data_catalog_progress_title))
    if (progress.pagesTotal == 0) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Body(stringResource(DataR.string.data_catalog_progress_starting))
    } else {
        LinearProgressIndicator(
            progress = { progress.pagesDone.toFloat() / progress.pagesTotal },
            modifier = Modifier.fillMaxWidth(),
        )
        Body(
            stringResource(
                DataR.string.data_catalog_progress,
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
        Text(stringResource(DataR.string.data_catalog_cancel))
    }
}

@Composable
private fun ColumnScope.Result(outcome: CatalogRefreshOutcome, onClose: () -> Unit) {
    when (outcome) {
        is CatalogRefreshOutcome.Success -> {
            Title(stringResource(DataR.string.data_catalog_result_success_title))
            Body(outcome.summaryText(LocalResources.current), Modifier.testTag(CATALOG_REFRESH_SUMMARY_TAG))
            Close(stringResource(R.string.catalog_refresh_done), onClose)
        }

        CatalogRefreshOutcome.Cancelled -> {
            Title(stringResource(R.string.catalog_refresh_cancelled_title))
            Body(stringResource(DataR.string.data_catalog_result_kept))
            Close(stringResource(R.string.catalog_refresh_close), onClose)
        }

        is CatalogRefreshOutcome.Failure -> {
            Title(stringResource(DataR.string.data_catalog_result_failed_title))
            Body(stringResource(outcome.reasonRes()))
            // The shop-changed copy (03 §5) says it itself.
            if (outcome !is CatalogRefreshOutcome.ShopChanged) {
                Body(
                    stringResource(DataR.string.data_catalog_result_kept),
                )
            }
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            CopyDetails(outcome.details)
            Close(stringResource(R.string.catalog_refresh_close), onClose)
        }
    }
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
