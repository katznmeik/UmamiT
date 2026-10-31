package com.umamit.feature.brew.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.feature.brew.R

/**
 * The top bar's ⋮ with its one item, *Im Katalog ansehen* (SCR-04, D-098). It remembers the alert it was opened
 * in; another alert removes it in its first frame, without the close animation, and closes it for good (rule 8).
 */
@Composable
internal fun SetupCatalogMenu(alertKey: String?, onOpen: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var openedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val covered = alertKey != null && alertKey != openedIn
    val shown = open && !covered
    LaunchedEffect(covered) { if (covered) open = false }
    Box {
        IconButton(
            onClick = {
                openedIn = alertKey
                open = true
            },
        ) {
            Icon(painterResource(UmamiIcons.MoreVert), contentDescription = stringResource(R.string.brew_setup_more))
        }
        // Not composed while another alert plays: Material's menu would otherwise keep its popup for the exit
        // animation over the alert's first frames. A normal close still animates.
        if (alertKey == null || shown) {
            DropdownMenu(expanded = shown, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.brew_setup_menu_catalog)) },
                    onClick = {
                        open = false
                        onOpen()
                    },
                )
            }
        }
    }
}
