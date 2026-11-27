package com.umamit.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.R as DsR

/**
 * A ⋮ whose menu holds one item, *Hilfe* (SCR-36 „Entry points", D-137 Q10): the top-bar action of a screen that has
 * no other menu, e.g. *Daten & Sicherung* or a new recipe. The menu gives way to an alert it was not opened in
 * ([alertKey], rule 8) like every menu. [onOpenHelp] opens the screen's Anleitung.
 */
@Composable
fun HelpOverflowAction(onOpenHelp: () -> Unit, alertKey: String?, modifier: Modifier = Modifier) {
    val menu = rememberAlertGate(alertKey)
    Box(modifier) {
        IconButton(onClick = menu::open) {
            Icon(painterResource(UmamiIcons.MoreVert), contentDescription = stringResource(DsR.string.ds_more_options))
        }
        AlertGatedDropdownMenu(menu) {
            DropdownMenuItem(
                text = { Text(stringResource(DsR.string.ds_help)) },
                onClick = {
                    menu.close()
                    onOpenHelp()
                },
            )
        }
    }
}
