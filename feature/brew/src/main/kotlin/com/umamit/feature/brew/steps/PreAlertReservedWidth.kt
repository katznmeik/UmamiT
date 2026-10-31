package com.umamit.feature.brew.steps

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing

/** The chevron of a *Vorwarnung* selector's button. */
internal val PreAlertChevronSize = 20.dp

/** One dp more than the sum, so the rounding of the button's own layout never makes it the wider. */
private val RoundingSlack = 1.dp

/**
 * The width of a *Vorwarnung* selector's button with the widest of [labels] (SCR-07 polish 1, D-098): its padding,
 * the label, the gap and the chevron, measured from the text, so nothing invisible is composed.
 */
@Composable
internal fun preAlertReservedWidth(labels: List<StepPreAlert>): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val texts = labels.map { preAlertLabel(it) }
    val widest = texts.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
    val padding = ButtonDefaults.TextButtonContentPadding
    val content = with(LocalDensity.current) { widest.toDp() } + UmamiSpacing.XXSmall + PreAlertChevronSize +
        padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr)
    return maxOf(content + RoundingSlack, ButtonDefaults.MinWidth)
}
