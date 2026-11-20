package com.umamit.feature.settings.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.ui.showUndo
import com.umamit.feature.settings.R

/**
 * „Sicherung eingespielt · 142 Sessions, 23 Tees" · *Rückgängig machen* over Start after an import (03 SCR-26), for
 * about 8 s like every undo snackbar (D-093 Q21); [onDone] once it is gone, or after a tapped undo once its result
 * („Import rückgängig gemacht" or a refusal) has shown. While the undo runs, the cover „Import wird rückgängig
 * gemacht …" is over everything (D-133 Q5). [hostState] is the shell's one snackbar queue; without it the snackbar
 * has its own host.
 */
@Composable
fun ImportedBackupSnackbar(
    imported: ImportedBackup?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    hostState: SnackbarHostState? = null,
    viewModel: ImportUndoViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val undoing by viewModel.running.collectAsStateWithLifecycle()
    ImportedBackupSnackbar(
        imported = imported,
        onUndo = viewModel::undo,
        onDone = onDone,
        result = result,
        onResultEnd = viewModel::resultShown,
        undoing = undoing,
        hostState = hostState,
        modifier = modifier,
    )
}

/** The stateless snackbar; [undoing] shows the undo's cover. */
@Composable
fun ImportedBackupSnackbar(
    imported: ImportedBackup?,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    result: UndoResult? = null,
    onResultEnd: () -> Unit = {},
    undoing: Boolean = false,
    hostState: SnackbarHostState? = null,
) {
    val ownHost = remember { SnackbarHostState() }
    val host = hostState ?: ownHost
    val message = imported?.let { importedMessage(it) }
    val undoLabel = stringResource(R.string.backup_undo_confirm)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(imported) {
        if (imported == null || message == null) return@LaunchedEffect
        // A tapped undo keeps the snackbar's slot until its result has shown.
        if (host.showUndo(message, undoLabel, accessibility)) currentOnUndo() else currentOnDone()
    }
    val resources = LocalResources.current
    val currentOnResultEnd by rememberUpdatedState(onResultEnd)
    LaunchedEffect(result) {
        val ended = result ?: return@LaunchedEffect
        ended.messageRes?.let { host.showSnackbar(resources.getString(it)) }
        currentOnResultEnd()
        currentOnDone()
    }
    // One emitter: the own host (if any) with the undo's cover, a window of its own, beside it.
    Box(modifier) {
        if (hostState == null) SnackbarHost(hostState = ownHost)
        if (undoing) {
            BusyDialog(
                text = stringResource(R.string.backup_undoing),
                modifier = Modifier.fillMaxSize().testTag(DataBackupTags.BUSY),
            )
        }
    }
}
