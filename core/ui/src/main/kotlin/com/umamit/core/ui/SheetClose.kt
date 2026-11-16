package com.umamit.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.launch

/**
 * A sheet's own close (*Speichern*, *Abbrechen*, *Fertig*, …; A-13, NFR-PERF-12): the sheet slides out with Material's
 * hide, as after a drag or a scrim tap, and only then [onDismissRequest] removes it. Under reduce motion it is removed
 * at once. A second tap while it leaves does nothing ([SheetClose.leaving]).
 *
 * Rule 8 is unchanged: the host's `AlertGate` still removes the sheet in the alert's first frame, without a hide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetClose(sheetState: SheetState, onDismissRequest: () -> Unit): SheetClose {
    val reduceMotion = UmamiTheme.reduceMotion
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    return remember(sheetState, scope, reduceMotion) {
        SheetClose { close ->
            if (reduceMotion) {
                currentOnDismiss()
            } else {
                scope.launch { sheetState.hide() }.invokeOnCompletion { close.finish(currentOnDismiss) }
            }
        }
    }
}

/** Closes its sheet once ([invoke]); [leaving] once it is on its way out. */
class SheetClose internal constructor(private val start: (SheetClose) -> Unit) : () -> Unit {
    /** True from the first [invoke]: the sheet is on its way out. */
    var leaving by mutableStateOf(false)
        private set

    private var finished = false

    override fun invoke() {
        if (leaving) return
        leaving = true
        start(this)
    }

    internal fun finish(onDismissRequest: () -> Unit) {
        if (finished) return
        finished = true
        onDismissRequest()
    }
}

/**
 * A sheet a view model closes by setting its [state] to null (*Aufnehmen*, A-13): the last state stays [shown] while
 * the sheet slides out ([leaving]); the sheet calls [LeavingSheet.left] once it is hidden. With [removeAtOnce] (an
 * alert, rule 8) nothing is kept: the sheet is gone in that frame.
 */
@Composable
fun <T : Any> rememberLeavingSheet(state: T?, removeAtOnce: Boolean): LeavingSheet<T> {
    val holder = remember { LeavingSheetHolder<T>() }
    SideEffect {
        if (state != null) {
            holder.last = state
        } else if (removeAtOnce) {
            holder.last = null
        }
    }
    val shown = if (removeAtOnce) state else state ?: holder.last
    return LeavingSheet(shown = shown, leaving = state == null && shown != null, left = { holder.last = null })
}

/** What [rememberLeavingSheet] shows: [shown] (null: no sheet), [leaving] while it slides out, [left] once hidden. */
class LeavingSheet<T : Any> internal constructor(val shown: T?, val leaving: Boolean, val left: () -> Unit)

private class LeavingSheetHolder<T : Any> {
    var last by mutableStateOf<T?>(null)
}

/**
 * A sheet that is always fully expanded; under reduce motion it starts expanded, so it appears without the slide
 * (03 §3.6). [confirmValueChange] may refuse a state, as the add sheet does while it saves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(
    reduceMotion: Boolean,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState {
    val density = LocalDensity.current
    return remember(reduceMotion) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { POSITIONAL_THRESHOLD.toPx() } },
            velocityThreshold = { with(density) { VELOCITY_THRESHOLD.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
            confirmValueChange = confirmValueChange,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val POSITIONAL_THRESHOLD = 56.dp
private val VELOCITY_THRESHOLD = 125.dp
