package com.umamit.feature.brew.steps

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.DiscardChangesDialog
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
 * the handle close the sheet without applying anything: without unsaved edits
 * they call [onDismissRequest] at once (the host sets [visible] to false); with
 * unsaved edits ([StepsSheetState.isDirty]) the sheet stays and asks
 * „Änderungen verwerfen?" (GEN-08): *Weiter bearbeiten* keeps editing,
 * *Verwerfen* calls [onDismissRequest] and resets the edits once the sheet has
 * left. The sheet never calls the engine or the database itself.
 *
 * **Alerts first (CLAUDE.md rule 8):** the sheet must never cover the alert.
 * When the timer enters ALERTING, the host sets [alerting] to true (and
 * [visible] to false, dropping the edits): the sheet and any open discard
 * question are removed in the same frame, without any animation and without
 * asking, and stay hidden while [alerting] is true.
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
    val guard = rememberSaveable(saver = DiscardGuard.Saver) { DiscardGuard() }
    val currentVisible by rememberUpdatedState(visible)
    val currentState by rememberUpdatedState(state)
    // Material asks before every user close (drag, fling, scrim, back); the host's close always passes.
    val mayHide: () -> Boolean = { guard.mayClose(currentVisible, currentState.isDirty) }
    val sheetState = rememberStepsSheetModalState(reduceMotion, mayHide)
    // Whether the sheet's window was shown and has not finished its hide animation yet.
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduceMotion) {
        // Reopened before a discarded sheet had left: drop the edits now.
        if (visible) guard.settle(state)
        when {
            visible && shown -> sheetState.show()

            // reopened during the hide animation
            visible -> shown = true

            shown -> {
                // The host closed the sheet: no question stays open.
                guard.keepEditing()
                if (!reduceMotion) sheetState.hide()
                shown = false
            }
        }
        if (!visible) guard.settle(state)
    }
    // Alerting and reduce motion decide in this composition, without waiting for an effect.
    val windowShown = !alerting && (visible || (shown && !reduceMotion))
    if (windowShown) {
        StepsSheetWindow(
            sheetState = sheetState,
            state = state,
            guard = guard,
            askable = visible,
            mayHide = mayHide,
            onDone = onDone,
            onSaveToRecipe = onSaveToRecipe,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        )
    }
}

/** The sheet's own window: Material's modal sheet with the content and the unsaved-changes guard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepsSheetWindow(
    sheetState: SheetState,
    state: StepsSheetState,
    guard: DiscardGuard,
    askable: Boolean,
    mayHide: () -> Boolean,
    onDone: (TimerEvent.EditSteps) -> Unit,
    onSaveToRecipe: (SaveToRecipeRequest) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        // Back ends here even when the sheet vetoed its hide, so the guard is asked again.
        onDismissRequest = { if (mayHide()) onDismissRequest() },
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        StepsSheetContent(state = state, onDone = onDone, onSaveToRecipe = onSaveToRecipe)
        // With unsaved edits, back asks instead of starting Material's predictive close, which a veto
        // would leave half-shrunk. Registered in the sheet's window after Material's, so it goes first.
        // It decides on the press itself, in case the edits were undone in this very frame.
        BackHandler(enabled = state.isDirty && !guard.discarding) { if (mayHide()) onDismissRequest() }
        if (guard.asking && askable) {
            DiscardChangesDialog(
                onKeepEditing = guard::keepEditing,
                onDiscard = { guard.discard(onDismissRequest) },
            )
        }
    }
}

/**
 * The unsaved-changes guard of the sheet (GEN-08): whether „Änderungen
 * verwerfen?" is [asking], and whether *Verwerfen* was chosen ([discarding]),
 * so the sheet may leave and its edits reset once it has.
 */
@Stable
private class DiscardGuard(asking: Boolean = false) {
    var asking by mutableStateOf(asking)
        private set
    var discarding by mutableStateOf(false)
        private set

    /** Whether a user close may go ahead now; with unsaved edits it asks instead. */
    fun mayClose(visible: Boolean, dirty: Boolean): Boolean {
        val allowed = !visible || discarding || !dirty
        if (!allowed) asking = true
        return allowed
    }

    fun keepEditing() {
        asking = false
    }

    fun discard(onDismissRequest: () -> Unit) {
        asking = false
        discarding = true
        onDismissRequest()
    }

    /** Drops the discarded edits, once the sheet has left or is reopened. */
    fun settle(state: StepsSheetState) {
        if (discarding) {
            state.discard()
            discarding = false
        }
    }

    companion object {
        /** The question survives a configuration change with the edits. */
        val Saver: Saver<DiscardGuard, Boolean> = Saver(save = { it.asking }, restore = { DiscardGuard(it) })
    }
}

/**
 * The sheet state: always fully expanded (no half-open stop). Under reduce
 * motion it starts expanded, so the sheet appears without the slide. [mayHide]
 * vetoes a user close while there are unsaved edits (GEN-08).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberStepsSheetModalState(reduceMotion: Boolean, mayHide: () -> Boolean): SheetState {
    val density = LocalDensity.current
    val currentMayHide by rememberUpdatedState(mayHide)
    val confirmValueChange: (SheetValue) -> Boolean = { it != SheetValue.Hidden || currentMayHide() }
    return rememberSaveable(
        reduceMotion,
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            confirmValueChange = confirmValueChange,
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
            confirmValueChange = confirmValueChange,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
