package com.umamit.feature.brew.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.feature.brew.R

/**
 * The top bar's ⋮ (SCR-04): *Tee ansehen* for every tea (D-100), then *Im Katalog ansehen* when [onOpenCatalog]
 * is given, for a tea with a catalog entry (D-098). It gives way to an alert like every setup menu
 * ([AlertGatedMenu], rule 8).
 */
@Composable
internal fun SetupMenu(alertKey: String?, onOpenTea: () -> Unit, onOpenCatalog: (() -> Unit)?) {
    AlertGatedMenu(
        alertKey = alertKey,
        anchor = { open ->
            IconButton(onClick = open) {
                Icon(
                    painterResource(UmamiIcons.MoreVert),
                    contentDescription = stringResource(R.string.brew_setup_more),
                )
            }
        },
    ) { close ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.brew_setup_menu_tea)) },
            onClick = {
                close()
                onOpenTea()
            },
        )
        onOpenCatalog?.let { open ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.brew_setup_menu_catalog)) },
                onClick = {
                    close()
                    open()
                },
            )
        }
    }
}

/**
 * A dropdown menu of the setup below its [anchor], which gets the function that opens it. The menu remembers the
 * alert it was opened in; another alert removes it in its first frame, without the close animation, and closes it
 * for good (rule 8). [items] get the function that closes it.
 */
@Composable
internal fun AlertGatedMenu(
    alertKey: String?,
    anchor: @Composable (open: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var openedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val covered = alertKey != null && alertKey != openedIn
    val shown = open && !covered
    LaunchedEffect(covered) { if (covered) open = false }
    Box(modifier) {
        anchor {
            openedIn = alertKey
            open = true
        }
        // Not composed while another alert plays: Material's menu would otherwise keep its popup for the exit
        // animation over the alert's first frames. A normal close still animates.
        if (alertKey == null || shown) {
            DropdownMenu(expanded = shown, onDismissRequest = { open = false }) {
                items { open = false }
            }
        }
    }
}
