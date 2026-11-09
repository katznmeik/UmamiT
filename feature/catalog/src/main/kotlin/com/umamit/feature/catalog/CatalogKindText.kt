package com.umamit.feature.catalog

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.umamit.core.model.CatalogKind

/** The kind's chip label (SCR-19, D-103): a new kind adds its string here and an entry in [CatalogKind.ALL]. */
internal val CatalogKind.chipLabel: Int
    @StringRes get() = when (this) {
        CatalogKind.HERBAL -> R.string.catalog_filter_herbal
        else -> R.string.catalog_filter_green
    }

/** The list header's count of the kind's teas („274 Grüntees", „38 Kräutertees"). */
internal val CatalogKind.countPlural: Int
    @PluralsRes get() = when (this) {
        CatalogKind.HERBAL -> R.plurals.catalog_herbal_count
        else -> R.plurals.catalog_tea_count
    }
