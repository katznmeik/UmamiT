package com.umamit.feature.catalog

import androidx.annotation.StringRes
import com.umamit.core.model.CatalogKind

/** The kind's chip label (SCR-19, D-103): a new kind adds its string here and an entry in [CatalogKind.ALL]. */
internal val CatalogKind.chipLabel: Int
    @StringRes get() = when (this) {
        CatalogKind.BLACK -> R.string.catalog_filter_black
        CatalogKind.WHITE -> R.string.catalog_filter_white
        CatalogKind.OOLONG -> R.string.catalog_filter_oolong
        CatalogKind.DARK -> R.string.catalog_filter_dark
        CatalogKind.HERBAL -> R.string.catalog_filter_herbal
        CatalogKind.ROOIBOS -> R.string.catalog_filter_rooibos
        CatalogKind.FRUIT -> R.string.catalog_filter_fruit
        CatalogKind.CHAI -> R.string.catalog_filter_chai
        else -> R.string.catalog_filter_green
    }

/** The kind's view's hint while the first refresh after the update that brings it is due (SCR-19). */
internal val CatalogKind.pendingHint: Int
    @StringRes get() = when (this) {
        CatalogKind.BLACK -> R.string.catalog_black_pending
        CatalogKind.WHITE -> R.string.catalog_white_pending
        CatalogKind.OOLONG -> R.string.catalog_oolong_pending
        CatalogKind.DARK -> R.string.catalog_dark_pending
        CatalogKind.ROOIBOS -> R.string.catalog_rooibos_pending
        CatalogKind.FRUIT -> R.string.catalog_fruit_pending
        CatalogKind.CHAI -> R.string.catalog_chai_pending
        else -> R.string.catalog_herbal_pending
    }
