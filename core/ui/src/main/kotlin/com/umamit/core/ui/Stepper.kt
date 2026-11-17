package com.umamit.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.tabular

/**
 * A pill with a value between − and + buttons, each 48 dp, with German TalkBack labels (03 §6). Held, − and +
 * repeat and speed up ([RepeatingIconButton], #141); [onDecrease] and [onIncrease] apply one step and return whether
 * it changed anything (at a bound the hold stops). The value box is at least [valueMinWidth] wide and announces
 * changes politely; with [onValueClick] it is a button too (the duration opens „Dauer wählen"). Shared by the
 * Schritte-Sheet and the recipe editor (UI-A-08).
 */
@Suppress("LongParameterList") // a stepper's parts
@Composable
fun Stepper(
    value: String,
    valueDescription: String,
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Boolean,
    onIncrease: () -> Boolean,
    valueMinWidth: Dp,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = UmamiShape.Pill,
        border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingIconButton(
                icon = painterResource(UmamiIcons.Remove),
                contentDescription = decreaseDescription,
                enabled = canDecrease,
                onStep = onDecrease,
            )
            Box(
                modifier = Modifier
                    .heightIn(min = UmamiSpacing.MinTouchTarget)
                    .widthIn(min = valueMinWidth)
                    .then(
                        if (onValueClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(role = Role.Button, onClick = onValueClick)
                        },
                    )
                    .semantics {
                        contentDescription = valueDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.tabular(),
                    textAlign = TextAlign.Center,
                )
            }
            RepeatingIconButton(
                icon = painterResource(UmamiIcons.Add),
                contentDescription = increaseDescription,
                enabled = canIncrease,
                onStep = onIncrease,
            )
        }
    }
}
