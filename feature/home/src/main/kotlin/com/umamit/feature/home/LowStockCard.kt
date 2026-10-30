package com.umamit.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.stockGramsText
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.LowStockCard

internal const val HOME_LOW_STOCK_TAG = "home_low_stock"

/**
 * The low-stock card of Start's nudge slot (INV-10, SCR-02, D-097): „*Tanabata* wird knapp · 14 g übrig"
 * (or „… ist leer") in the body style with a small leaf in the tea's style colour, and the text buttons
 * *Ausblenden* and *Nachbestellen* (only with INV-09's link). A reminder, not an alarm: no accent fill, no
 * badge. It is a plain part of the Start list and never covers anything (rule 8).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LowStockNudge(
    card: LowStockCard,
    onReorder: (String) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HOME_LOW_STOCK_TAG),
        shape = UmamiShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                start = UmamiSpacing.Medium,
                end = UmamiSpacing.XSmall,
                top = UmamiSpacing.Small,
            ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(UmamiIcons.Leaf),
                    contentDescription = null,
                    tint = UmamiTheme.extendedColors.teaTypes.forStyle(card.styleKey),
                    modifier = Modifier
                        .padding(top = LEAF_TOP_PADDING)
                        .size(LEAF_SIZE),
                )
                Spacer(Modifier.width(UmamiSpacing.Small))
                Text(
                    text = message(card),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = UmamiSpacing.XSmall),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onHide) { Text(stringResource(R.string.home_low_stock_hide)) }
                card.reorderUrl?.let { url ->
                    TextButton(onClick = { onReorder(url) }) { Text(stringResource(R.string.home_low_stock_reorder)) }
                }
            }
        }
    }
}

/** „*Tanabata* wird knapp · 14 g übrig" or „*Tanabata* ist leer", the tea's name in italics. */
@Composable
private fun message(card: LowStockCard): AnnotatedString {
    val name = card.teaName
    val text = if (card.isEmpty) {
        stringResource(R.string.home_low_stock_empty, name)
    } else {
        stringResource(R.string.home_low_stock_low, name, stockGramsText(card.remainingGrams))
    }
    val start = text.indexOf(name)
    return buildAnnotatedString {
        if (start < 0) {
            append(text)
        } else {
            append(text.substring(0, start))
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(name) }
            append(text.substring(start + name.length))
        }
    }
}

private val LEAF_SIZE = 18.dp
private val LEAF_TOP_PADDING = 3.dp
