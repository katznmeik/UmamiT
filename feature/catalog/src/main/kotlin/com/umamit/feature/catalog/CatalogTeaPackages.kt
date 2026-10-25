package com.umamit.feature.catalog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail

/**
 * PACKUNGEN (SCR-21, CAT-11; 08 §2.4 item 6): each size with the shop's price, an estimate
 * marked „≈" (a larger size), or „Preis beim Hinzufügen" (a smaller size, priced only when it is
 * added, CAT-12); with the savings the shop notes and a size out of stock marked.
 */
@Composable
internal fun Packages(tea: CatalogTeaDetail) {
    if (tea.packages.isEmpty()) return
    Section(stringResource(R.string.catalog_tea_section_packages)) {
        tea.packages.forEach {
            Fact(stringResource(R.string.catalog_package_grams, CatalogFormat.grams(it.grams)), it.price())
        }
        if (tea.packages.any { it.priceIsEstimate && it.priceCents != null }) {
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            Text(
                text = stringResource(R.string.catalog_package_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „12,40 €", „≈ 30,07 € · 3 % gespart", „Preis beim Hinzufügen", „7,90 € · nicht vorrätig". */
@Composable
private fun CatalogPackage.price(): String {
    val cents = priceCents
    val price = when {
        cents == null -> stringResource(R.string.catalog_package_price_on_add)
        priceIsEstimate -> stringResource(R.string.catalog_package_estimate, CatalogFormat.euros(cents))
        else -> stringResource(R.string.catalog_package_price, CatalogFormat.euros(cents))
    }
    return listOfNotNull(
        price,
        savingsPercent?.takeIf { it > 0 }?.let { stringResource(R.string.catalog_package_savings, it) },
        stringResource(R.string.catalog_tea_out_of_stock).takeIf { available == false },
    ).joinToString(stringResource(R.string.catalog_meta_separator))
}
