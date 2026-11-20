package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons

/**
 * A top-level screen (03 §2): top bar with [title], a vertically scrolling body
 * and edge-to-edge insets. The bottom inset applies only where the host has not
 * consumed it (the navigation bar and the active brew bar do).
 *
 * @param scrollToTopRequest a counter; every increase scrolls the body to the top
 *   (re-selecting the tab, 03 §2.2).
 * @param verticalArrangement the body's arrangement: centred for an empty state, [Arrangement.Top] for a list.
 * @param scrollable whether the body scrolls; false for a body that scrolls itself (a lazy list), which
 *   then handles [scrollToTopRequest] too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollToTopRequest: Int = 0,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    // The request is a counter; only an increase while this screen is composed scrolls. Not saveable on
    // purpose: a screen composed again (return from a pushed screen, tab switch, rotation) adopts the
    // current count, so a request is never replayed and a reset counter never swallows the next ones (D-049).
    var handled by remember { mutableIntStateOf(scrollToTopRequest) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > handled) {
            handled = scrollToTopRequest
            scrollState.animateScrollTo(0)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon, actions = actions)
        },
        // Bottom too: the bar layout has consumed it already (phones are unchanged), but the rail
        // layout has not, so the last item of a list stays clear of the gesture handle.
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** The gear on Start (03 §2.2). */
@Composable
fun SettingsGearAction(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onOpenSettings, modifier = modifier) {
        Icon(painterResource(UmamiIcons.Settings), contentDescription = stringResource(R.string.ds_settings))
    }
}

/** An entry of a screen's own in the ⋮ menu, above *Einstellungen* (e.g. *Katalog aktualisieren*). */
class OverflowItem(val label: String, val onClick: () -> Unit)

/**
 * The ⋮ menu of the other tabs: the screen's own [items], then the settings (03 §2.2). It gives way to an alert
 * (03 „Overlays give way", D-058): the host keeps its open state in an alert gate (`rememberAlertGate` in `core:ui`,
 * which this module cannot depend on, 06 §3) and passes it in.
 *
 * @param shown whether the menu is open now (the gate's `shown`).
 * @param keepsPopup whether the menu's popup may stay composed (the gate's `keepsPopup`): false while an alert plays
 *   that the menu was not opened in, so it leaves in that frame without Material's exit animation.
 * @param onOpen the ⋮ tap (the gate's `open`, which remembers the alert playing now).
 * @param onClose a tap outside or on an entry (the gate's `close`).
 */
@Composable
fun OverflowSettingsAction(
    onOpenSettings: () -> Unit,
    shown: Boolean,
    keepsPopup: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    items: List<OverflowItem> = emptyList(),
) {
    // Own anchor: the menu opens below the dots with its end edge on the button's, whatever layout the caller uses.
    Box(modifier = modifier) {
        IconButton(onClick = onOpen) {
            Icon(painterResource(UmamiIcons.MoreVert), contentDescription = stringResource(R.string.ds_more_options))
        }
        if (keepsPopup) {
            DropdownMenu(expanded = shown, onDismissRequest = onClose) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            onClose()
                            item.onClick()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ds_settings)) },
                    onClick = {
                        onClose()
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

/** Up arrow for pushed screens such as the settings. */
@Composable
fun BackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(painterResource(UmamiIcons.ArrowBack), contentDescription = stringResource(R.string.ds_back))
    }
}
