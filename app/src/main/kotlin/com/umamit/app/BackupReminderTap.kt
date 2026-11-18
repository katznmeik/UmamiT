package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first

/**
 * The backup reminder's tap (BAK-06, 03 §2.4, D-130 Q11) in the shell: for each new non-zero [request] token it runs
 * [turnToStart] (everything the picker's flow left open closes, the shell turns to Start), waits until Start's stack
 * has its graph (a tab never shown has none yet) and opens SCR-26 there ([openDataBackup]).
 */
@Composable
internal fun BackupReminderTap(request: Long, start: NavHostController, turnToStart: () -> Unit) {
    var shown by rememberSaveable { mutableLongStateOf(request) }
    val currentTurnToStart by rememberUpdatedState(turnToStart)
    LaunchedEffect(request) {
        if (request != 0L && request != shown) {
            shown = request
            currentTurnToStart()
            start.currentBackStackEntryFlow.first()
            start.openDataBackup()
        }
    }
}

/**
 * SCR-26 on top of Start's stack, below it *Einstellungen* (so that a tab change closes it, `closeSettings`); already
 * open, it stays as it is, so a running export or a staged import is never dropped.
 */
private fun NavHostController.openDataBackup() {
    val current = currentDestination
    if (current?.hasRoute<DataBackupRoute>() == true) return
    if (current?.hasRoute<SettingsRoute>() != true) {
        popToRoot(Tab.Start)
        navigate(SettingsRoute)
    }
    navigate(DataBackupRoute)
}
