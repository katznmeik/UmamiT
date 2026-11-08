package com.umamit.feature.collection.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.umamit.core.domain.collection.TeaCost
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.collection.R

/** VORRAT's cost lines (INV-08): price per 100 g, the recipe estimate, the average. */
const val TEA_DETAIL_COST_TAG = "tea_detail_cost"

/**
 * The cost of a priced tea (INV-08, D-104): „12,40 € / 100 g", „ca. 0,24 € pro Tasse (Rezept)" with a default recipe,
 * and from the first priced session „Ø 0,27 € pro Tasse aus 5 Sitzungen" (TalkBack: „Durchschnittlich …").
 */
@Composable
internal fun CostLines(cost: TeaCost) {
    Column(Modifier.testTag(TEA_DETAIL_COST_TAG)) {
        CostLine(stringResource(R.string.collection_tea_cost_price, GermanFormat.euros(cost.pricePer100gCents)))
        cost.recipeCupCents?.let {
            CostLine(stringResource(R.string.collection_tea_cost_recipe, GermanFormat.euros(it)))
        }
        cost.average?.let { average ->
            val euros = GermanFormat.euros(average.cupCents)
            val count = average.sessionCount
            val spoken = pluralStringResource(R.plurals.collection_tea_cost_average_spoken, count, euros, count)
            CostLine(
                text = pluralStringResource(R.plurals.collection_tea_cost_average, count, euros, count),
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun CostLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
