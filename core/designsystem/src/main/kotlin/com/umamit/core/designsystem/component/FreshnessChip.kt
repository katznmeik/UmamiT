package com.umamit.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.Freshness

/**
 * A tea's freshness label (03 §3.8, INV-06, D-104) beside its stock chip: *Frisch* in the matcha container,
 * *Bald genießen* in the cream of *Knapp*, *Über dem Höhepunkt* in the quiet chip grey. Always a word, never
 * colour alone. [compact] is the tea picker's small chip (SCR-03). TalkBack reads „Frische: Bald genießen".
 */
@Composable
fun FreshnessChip(freshness: Freshness, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container: Color, content: Color) = when (freshness) {
        Freshness.FRESH -> colors.primaryContainer to colors.onPrimaryContainer
        Freshness.DRINK_SOON -> colors.secondaryContainer to colors.onSecondaryContainer
        Freshness.PAST_PEAK -> colors.surfaceContainerHigh to colors.onSurface
    }
    val text = stringResource(freshness.label)
    val description = stringResource(R.string.ds_freshness_description, text)
    Surface(
        shape = CircleShape,
        color = container,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = content,
            // The same height as the stock chip and „Rezept prüfen" beside it.
            modifier = if (compact) {
                Modifier.padding(horizontal = CompactHorizontal, vertical = CompactVertical)
            } else {
                Modifier.padding(horizontal = UmamiSpacing.XSmall, vertical = UmamiSpacing.XXSmall)
            },
        )
    }
}

private val CompactHorizontal = 6.dp
private val CompactVertical = 2.dp

/** The label's word: „Frisch", „Bald genießen", „Über dem Höhepunkt". */
@get:StringRes
val Freshness.label: Int
    get() = when (this) {
        Freshness.FRESH -> R.string.ds_freshness_fresh
        Freshness.DRINK_SOON -> R.string.ds_freshness_drink_soon
        Freshness.PAST_PEAK -> R.string.ds_freshness_past_peak
    }
