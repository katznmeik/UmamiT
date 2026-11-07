package com.umamit.feature.brew.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.ui.AlertGate
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.brew.R

/**
 * The top bar's ⋮ (SCR-04): *Tee ansehen* for every tea (D-100), then *Im Katalog ansehen* when [onOpenCatalog]
 * is given, for a tea with a catalog entry (D-098). It gives way to an alert like every setup menu
 * ([AlertGate], rule 8).
 */
@Composable
internal fun SetupMenu(alertKey: String?, onOpenTea: () -> Unit, onOpenCatalog: (() -> Unit)?) {
    val menu = rememberAlertGate(alertKey)
    Box {
        IconButton(onClick = menu::open) {
            Icon(
                painterResource(UmamiIcons.MoreVert),
                contentDescription = stringResource(R.string.brew_setup_more),
            )
        }
        AlertGatedDropdownMenu(menu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.brew_setup_menu_tea)) },
                onClick = {
                    menu.close()
                    onOpenTea()
                },
            )
            onOpenCatalog?.let { open ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.brew_setup_menu_catalog)) },
                    onClick = {
                        menu.close()
                        open()
                    },
                )
            }
        }
    }
}
