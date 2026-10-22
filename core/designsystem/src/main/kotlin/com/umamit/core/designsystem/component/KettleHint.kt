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

/**
 * Kettle icon with „Wasserkocher auf 60 °C" (03 §3.8) and an optional detail
 * line such as „Aufguss 2 · 0:20". TalkBack reads one sentence: the temperature
 * in words, then [detailDescription] (or [detail]).
 *
 * @param temperatureCelsius already a multiple of 5 (GEN-12).
 */
@Composable
fun KettleHint(
    temperatureCelsius: Int,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailDescription: String? = detail,
) {
    val spoken = listOfNotNull(
        pluralStringResource(R.plurals.ds_kettle_hint_description, temperatureCelsius, temperatureCelsius),
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
                text = stringResource(R.string.ds_kettle_hint, temperatureCelsius),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
