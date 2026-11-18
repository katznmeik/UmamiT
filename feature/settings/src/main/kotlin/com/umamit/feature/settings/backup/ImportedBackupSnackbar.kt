package com.umamit.feature.settings.backup

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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.ui.showUndo
import com.umamit.feature.settings.R

/**
 * „Sicherung eingespielt · 142 Sessions, 23 Tees" · *Rückgängig machen* over Start after an import (03 SCR-26), for
 * about 8 s like every undo snackbar (D-093 Q21); [onDone] once it is gone, or after a tapped undo once its result
 * („Import rückgängig gemacht" or a refusal) has shown.
 */
@Composable
fun ImportedBackupSnackbar(
    imported: ImportedBackup?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportUndoViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    ImportedBackupSnackbar(
        imported = imported,
        onUndo = viewModel::undo,
        onDone = onDone,
        result = result,
        onResultEnd = viewModel::resultShown,
        modifier = modifier,
    )
}

/** The stateless snackbar. */
@Composable
fun ImportedBackupSnackbar(
    imported: ImportedBackup?,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    result: UndoResult? = null,
    onResultEnd: () -> Unit = {},
) {
    val hostState = remember { SnackbarHostState() }
    val message = imported?.let { importedMessage(it) }
    val undoLabel = stringResource(R.string.backup_undo_confirm)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(imported) {
        if (imported == null || message == null) return@LaunchedEffect
        // A tapped undo keeps the snackbar's host until its result has shown.
        if (hostState.showUndo(message, undoLabel, accessibility)) currentOnUndo() else currentOnDone()
    }
    val resources = LocalResources.current
    val currentOnResultEnd by rememberUpdatedState(onResultEnd)
    LaunchedEffect(result) {
        val ended = result ?: return@LaunchedEffect
        ended.messageRes?.let { hostState.showSnackbar(resources.getString(it)) }
        currentOnResultEnd()
        currentOnDone()
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
