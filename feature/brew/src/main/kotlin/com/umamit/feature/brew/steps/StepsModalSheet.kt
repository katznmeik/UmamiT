package com.umamit.feature.brew.steps

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.engine.TimerEvent

/**
 * The Schritte-Sheet (SCR-31) as Material 3's modal bottom sheet. It runs in
 * its own window, so TalkBack and keyboard focus stay inside it, and it brings
 * predictive back, drag and fling to dismiss and nested scrolling of the
 * step list. It opens fully expanded with Material's sheet motion (calm, no
 * overshoot, D-051); under reduce motion it opens expanded and closes without
 * the slide.
 *
 * *Fertig* emits [onDone] with the engine's `editSteps` request; the host
 * applies it and sets [visible] to false. *Im Rezept speichern* emits
 * [onSaveToRecipe] and shows a confirmation. A drag down, the scrim, back or
 * the handle call [onDismissRequest]: the host sets [visible] to false, and
 * nothing is applied. The sheet never calls the engine or the database itself.
 *
 * **Alerts first (CLAUDE.md rule 8):** the sheet must never cover the alert.
 * When the timer enters ALERTING, the host sets [alerting] to true (and
 * [visible] to false, dropping the edits): the sheet is removed in the same
 * frame, without any animation, and stays hidden while [alerting] is true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsSheet(
    visible: Boolean,
    state: StepsSheetState,
    onDone: (TimerEvent.EditSteps) -> Unit,
    onSaveToRecipe: (SaveToRecipeRequest) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alerting: Boolean = false,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberStepsSheetModalState(reduceMotion)
    // Whether the sheet's window was shown and has not finished its hide animation yet.
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduceMotion) {
        when {
            visible && shown -> sheetState.show()

            // reopened during the hide animation
            visible -> shown = true

            shown -> {
                if (!reduceMotion) sheetState.hide()
                shown = false
            }
        }
    }
    // Alerting and reduce motion decide in this composition, without waiting for an effect.
    val windowShown = !alerting && (visible || (shown && !reduceMotion))
    if (windowShown) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            StepsSheetContent(state = state, onDone = onDone, onSaveToRecipe = onSaveToRecipe)
        }
    }
}

/**
 * The sheet state: always fully expanded (no half-open stop). Under reduce
 * motion it starts expanded, so the sheet appears without the slide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberStepsSheetModalState(reduceMotion: Boolean): SheetState {
    val density = LocalDensity.current
    return rememberSaveable(
        reduceMotion,
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            confirmValueChange = { true },
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
