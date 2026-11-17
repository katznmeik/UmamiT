package com.umamit.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.reasonRes
import com.umamit.core.data.catalog.summaryText
import com.umamit.core.data.R as DataR

/**
 * The refresh's progress line, one source for SCR-22's sheet and SCR-25's *Katalog* (CAT-07 AC1, SET-23):
 * „Sitemap wird gelesen …" until the page count is known, then „Seiten: 2 von 4 · Produkte: 1". The words are
 * core:data's, the same as the progress notification's.
 */
@Composable
fun catalogRefreshProgressLine(progress: CatalogRefreshProgress): String = if (progress.pagesTotal == 0) {
    stringResource(DataR.string.data_catalog_progress_starting)
} else {
    stringResource(DataR.string.data_catalog_progress, progress.pagesDone, progress.pagesTotal, progress.products)
}

/**
 * The refresh's result line, one source for SCR-22's sheet and SCR-25 (CAT-07 AC3, SET-23): the summary of a
 * success („274 Tees · 12 neu · …"), „Laden abgebrochen", or the reason a failure kept the catalog as it was.
 */
@Composable
fun catalogRefreshResultLine(outcome: CatalogRefreshOutcome): String = when (outcome) {
    is CatalogRefreshOutcome.Success -> outcome.summaryText(LocalResources.current)
    CatalogRefreshOutcome.Cancelled -> stringResource(R.string.ui_catalog_refresh_cancelled)
    is CatalogRefreshOutcome.Failure -> stringResource(outcome.reasonRes())
}

/** The refresh's progress bar: indeterminate until the page count is known, then pages read of pages found. */
@Composable
fun CatalogRefreshBar(progress: CatalogRefreshProgress, modifier: Modifier = Modifier) {
    if (progress.pagesTotal == 0) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress.pagesDone.toFloat() / progress.pagesTotal },
            modifier = modifier.fillMaxWidth(),
        )
    }
}
