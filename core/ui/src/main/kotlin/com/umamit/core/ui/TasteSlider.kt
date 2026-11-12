package com.umamit.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SlimSlider
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TastingLimits
import kotlin.math.roundToInt

/** Test tag of the slider of [axis]. */
fun tasteSliderTag(axis: TasteAxis): String = "taste_slider_${axis.name.lowercase()}"

private val LABEL_WIDTH = 104.dp
private val VALUE_WIDTH = 32.dp

/** The German name of [this] axis (JRN-03). */
@Composable
fun TasteAxis.tasteLabel(): String = stringResource(
    when (this) {
        TasteAxis.UMAMI -> R.string.ui_taste_umami
        TasteAxis.SWEETNESS -> R.string.ui_taste_sweetness
        TasteAxis.BITTERNESS -> R.string.ui_taste_bitterness
        TasteAxis.ASTRINGENCY -> R.string.ui_taste_astringency
        TasteAxis.BODY -> R.string.ui_taste_body
        TasteAxis.AROMA -> R.string.ui_taste_aroma
    },
)

/**
 * One taste input (03 §3.8, JRN-03): 0–5 in whole steps with its label and value; „–" while unset (unset is not 0).
 * Touching the slider sets a value; [onChange] gets it on release, not while dragging (a release saves). Stateless
 * except for the drag: [value] is shown as given. TalkBack reads the label and „3 von 5" / „nicht gesetzt".
 */
@Composable
fun TasteSlider(axis: TasteAxis, value: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val currentOnChange = rememberUpdatedState(onChange)
    var dragged by remember(value) { mutableStateOf(value) }
    val label = axis.tasteLabel()
    val shown = dragged
    val valueColor = if (shown == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val state = if (shown == null) {
        stringResource(R.string.ui_taste_not_set)
    } else {
        stringResource(R.string.ui_taste_slider_value, shown.toString())
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(min = LABEL_WIDTH)
                .clearAndSetSemantics {},
        )
        SlimSlider(
            value = (shown ?: TastingLimits.MIN_TASTE).toFloat(),
            onValueChange = {
                val new = it.roundToInt()
                if (new != dragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                dragged = new
            },
            onValueChangeFinished = { currentOnChange.value(dragged ?: TastingLimits.MIN_TASTE) },
            valueRange = TastingLimits.MIN_TASTE.toFloat()..TastingLimits.MAX_TASTE.toFloat(),
            steps = TastingLimits.MAX_TASTE - TastingLimits.MIN_TASTE - 1,
            unset = shown == null,
            modifier = Modifier
                .weight(1f)
                .testTag(tasteSliderTag(axis))
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
        )
        Text(
            text = shown?.toString() ?: stringResource(R.string.ui_taste_unset),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = VALUE_WIDTH)
                .clearAndSetSemantics {},
        )
    }
}
