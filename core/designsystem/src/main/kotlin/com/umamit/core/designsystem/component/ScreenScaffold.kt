package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons

/**
 * A top-level screen (03 §2): top bar with [title], a vertically scrolling body
 * and edge-to-edge insets. The bottom inset is left to the navigation bar that
 * hosts the screen.
 *
 * @param scrollToTopRequest a counter; every increase scrolls the body to the top
 *   (re-selecting the tab, 03 §2.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollToTopRequest: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    // The request is a counter; only an increase seen by this screen scrolls, so it never replays on return.
    var handled by rememberSaveable { mutableIntStateOf(scrollToTopRequest) }
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
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

/** The ⋮ menu of the other tabs, whose only entry for now is the settings (03 §2.2). */
@Composable
fun OverflowSettingsAction(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(painterResource(UmamiIcons.MoreVert), contentDescription = stringResource(R.string.ds_more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ds_settings)) },
            onClick = {
                expanded = false
                onOpenSettings()
            },
        )
    }
}

/** Up arrow for pushed screens such as the settings. */
@Composable
fun BackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(painterResource(UmamiIcons.ArrowBack), contentDescription = stringResource(R.string.ds_back))
    }
}
