package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.umamit.feature.settings.backup.ImportedBackup

/**
 * A finished backup import (BAK-02 AC6), as the root keeps it: [finish] closes everything above the shell and asks
 * the shell for Start at its root ([requestFor]), with „Sicherung eingespielt · …“ over it ([importedFor]) until the
 * snackbar is gone. No timer can run then (the import is refused during one), so nothing above the shell is an alert.
 */
internal class BackupImportStart(request: Long = 0L, imported: ImportedBackup? = null) {
    private var request by mutableLongStateOf(request)

    var imported by mutableStateOf(imported)

    fun finish(navController: NavHostController, done: ImportedBackup) {
        navController.popBackStack<ShellRoute>(inclusive = false)
        imported = done
        request++
    }

    /** A new count per import for the app's shell; the refresh shell above it is popped by [finish]. */
    fun requestFor(opening: ShellOpening): Long = if (opening == ShellOpening.Start) request else 0L

    fun importedFor(opening: ShellOpening): ImportedBackup? = imported.takeIf { opening == ShellOpening.Start }

    companion object {
        val Saver: Saver<BackupImportStart, List<Long>> = Saver(
            save = { listOfNotNull(it.request, it.imported?.sessions?.toLong(), it.imported?.teas?.toLong()) },
            restore = { saved ->
                val imported = if (saved.size == 3) ImportedBackup(saved[1].toInt(), saved[2].toInt()) else null
                BackupImportStart(saved[0], imported)
            },
        )
    }
}

@Composable
internal fun rememberBackupImportStart(): BackupImportStart =
    rememberSaveable(saver = BackupImportStart.Saver) { BackupImportStart() }
