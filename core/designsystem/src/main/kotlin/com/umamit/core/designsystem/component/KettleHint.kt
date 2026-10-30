package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.KettleWater

/**
 * Kettle icon with „Wasserkocher auf 60 °C" (03 §3.8) and an optional detail
 * line such as „Aufguss 2 · 0:20"; for [KettleWater.COLD] and [KettleWater.ICE]
 * „Kaltes Wasser" or „Eiswürfel" instead (BRW-10). An optional [amount]
 * line, such as „4,0 g Tee · 250 ml Wasser" (BRW-11, D-097), sits between the
 * hint and the detail. TalkBack reads one sentence: the hint in words, then
 * [amountDescription] (or [amount]), then [detailDescription] (or [detail]).
 *
 * @param temperatureCelsius already a multiple of 5 (GEN-12).
 */
@Composable
fun KettleHint(
    temperatureCelsius: Int,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailDescription: String? = detail,
    water: KettleWater = KettleWater.HOT,
    amount: String? = null,
    amountDescription: String? = amount,
) {
    val text = when (water) {
        KettleWater.HOT -> stringResource(R.string.ds_kettle_hint, temperatureCelsius)
        KettleWater.COLD -> stringResource(R.string.ds_kettle_hint_cold)
        KettleWater.ICE -> stringResource(R.string.ds_kettle_hint_ice)
    }
    val spoken = listOfNotNull(
        if (water == KettleWater.HOT) {
            pluralStringResource(R.plurals.ds_kettle_hint_description, temperatureCelsius, temperatureCelsius)
        } else {
            text
        },
        amountDescription,
        detailDescription,
    ).joinToString(", ")
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UmamiIcons.Kettle),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (amount != null) {
                // As clear as the hint itself: the leaf to weigh matters as much as the water's heat (D-097).
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
