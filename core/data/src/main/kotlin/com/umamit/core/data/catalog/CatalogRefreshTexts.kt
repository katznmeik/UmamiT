package com.umamit.core.data.catalog

import android.content.res.Resources
import androidx.annotation.StringRes
import com.umamit.core.data.R

/**
 * „274 Tees · 12 neu · 5 geändert · 2 nicht mehr im Sortiment · 3 Rezepte prüfen" (CAT-07 AC3): the summary line of
 * the refresh sheet and of the result notification, read through [resources].
 */
fun CatalogRefreshOutcome.Success.summaryText(resources: Resources): String =
    summary().joinToString(resources.getString(R.string.data_catalog_summary_separator)) { part ->
        when (part) {
            is SummaryPart.Teas -> resources.getQuantityString(R.plurals.data_catalog_tea_count, part.count, part.count)

            is SummaryPart.New -> resources.getString(R.string.data_catalog_summary_new, part.count)

            is SummaryPart.Changed -> resources.getString(R.string.data_catalog_summary_changed, part.count)

            is SummaryPart.Discontinued -> resources.getString(R.string.data_catalog_summary_discontinued, part.count)

            is SummaryPart.RecipesToCheck ->
                resources.getQuantityString(R.plurals.data_catalog_summary_recipes, part.count, part.count)

            SummaryPart.NoChanges -> resources.getString(R.string.data_catalog_summary_unchanged)
        }
    }

/** Why a refresh that changed nothing failed, in the words of the sheet and of the notification (03 §5). */
@StringRes
fun CatalogRefreshOutcome.Failure.reasonRes(): Int = when (this) {
    is CatalogRefreshOutcome.Blocked -> R.string.data_catalog_result_blocked
    is CatalogRefreshOutcome.Offline -> R.string.data_catalog_result_offline
    is CatalogRefreshOutcome.ShopUnavailable -> R.string.data_catalog_result_unavailable
    is CatalogRefreshOutcome.ShopChanged -> R.string.data_catalog_result_shop_changed
    is CatalogRefreshOutcome.StorageFailed -> R.string.data_catalog_result_storage
}
