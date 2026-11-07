package com.umamit.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/**
 * Whether the alert playing now ([alertKey], null for none) covers something opened during [openedIn] (the
 * `alertKey` of the moment it was opened): only the alert it was opened in may play under it (rule 8, D-058).
 * View models use it for the questions they hold; screens use [rememberAlertGate].
 */
fun alertCovers(alertKey: String?, openedIn: String?): Boolean = alertKey != null && alertKey != openedIn

/**
 * The open state of one menu, dialog or sheet that gives way to an alert (rule 8, D-058). It remembers the alert it
 * was opened in; when another alert starts it is no longer [shown] in that frame (so no exit animation can cover the
 * alert) and it closes for good: it does not come back after the alert. It survives a configuration change (GEN-07).
 * Made by [rememberAlertGate]; [AlertGated] and [AlertGatedDropdownMenu] compose by it.
 */
@Stable
class AlertGate internal constructor(
    private val openState: MutableState<Boolean>,
    private val openedIn: MutableState<String?>,
    private val alertKey: State<String?>,
) {
    /** Whether it was opened and not closed since; it may still be [covered]. */
    val isOpen: Boolean get() = openState.value

    /** Whether an alert plays that it was not opened in. */
    val covered: Boolean get() = alertCovers(alertKey.value, openedIn.value)

    /** Whether it is on the screen now. */
    val shown: Boolean get() = isOpen && !covered

    /**
     * Whether a Material popup with its own exit animation (a [DropdownMenu]) may stay composed: always while no
     * alert plays, so a normal close still animates; during an alert only while [shown], so it leaves at once.
     */
    val keepsPopup: Boolean get() = alertKey.value == null || shown

    /** Opens it in the alert playing now (or in none). */
    fun open() {
        openedIn.value = alertKey.value
        openState.value = true
    }

    fun close() {
        openState.value = false
    }

    /** An `onExpandedChange` (Material's exposed dropdown): opens or closes it. */
    fun expand(expanded: Boolean) = if (expanded) open() else close()
}

/** An [AlertGate] for the alert playing now, [alertKey] (null: none). */
@Composable
fun rememberAlertGate(alertKey: String?): AlertGate {
    val openState = rememberSaveable { mutableStateOf(false) }
    val openedIn = rememberSaveable { mutableStateOf<String?>(null) }
    val key = rememberUpdatedState(alertKey)
    val gate = remember(openState, openedIn, key) { AlertGate(openState, openedIn, key) }
    // A new alert closes it for good; it is already gone in the frame the alert started.
    val covered = gate.covered
    LaunchedEffect(covered) { if (covered) gate.close() }
    return gate
}

/** Composes [content] (a dialog, a sheet) only while [gate] is [shown][AlertGate.shown]. */
@Composable
fun AlertGated(gate: AlertGate, content: @Composable () -> Unit) {
    if (gate.shown) content()
}

/**
 * A [DropdownMenu] open while [gate] is [shown][AlertGate.shown]; a tap outside closes it. It is not composed while
 * an alert plays that it was not opened in: Material keeps a closed menu's popup for its exit animation, which would
 * otherwise show over the alert's first frames. A normal close still animates.
 */
@Composable
fun AlertGatedDropdownMenu(
    gate: AlertGate,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (gate.keepsPopup) {
        DropdownMenu(expanded = gate.shown, onDismissRequest = gate::close, modifier = modifier, content = content)
    }
}

/**
 * The menu of Material's exposed dropdown (`ExposedDropdownMenuBox(expanded = gate.shown, onExpandedChange =
 * gate::expand)`), gated as [AlertGatedDropdownMenu].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.AlertGatedExposedDropdownMenu(
    gate: AlertGate,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (gate.keepsPopup) ExposedDropdownMenu(expanded = gate.shown, onDismissRequest = gate::close, content = content)
}
