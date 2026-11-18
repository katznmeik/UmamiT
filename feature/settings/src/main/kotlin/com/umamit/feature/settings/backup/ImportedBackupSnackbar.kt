package com.umamit.feature.settings.backup

import androidx.annotation.StringRes
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.ui.showUndo
import com.umamit.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The snackbar's *Rückgängig machen* on Start (BAK-02 AC6, D-130 item 22): the undo of the import just done, refused
 * like the one on SCR-26 while a timer is active. [result] is its text, until [resultShown].
 */
@HiltViewModel
class ImportUndoViewModel @Inject constructor(private val backup: DataBackup) : ViewModel() {
    private val mutable = MutableStateFlow<Int?>(null)

    @get:StringRes
    val result: StateFlow<Int?> = mutable.asStateFlow()

    fun undo() {
        viewModelScope.launch {
            mutable.value = when (val done = backup.undo()) {
                is RestoreResult.Done -> R.string.backup_undone
                is RestoreResult.Refused -> done.refusal.messageRes
                RestoreResult.NoSafetyCopy -> null
            }
        }
    }

    fun resultShown() {
        mutable.value = null
    }
}

/**
 * „Sicherung eingespielt · 142 Sessions, 23 Tees" · *Rückgängig machen* over Start after an import (03 SCR-26), for
 * about 8 s like every undo snackbar (D-093 Q21); [onDone] once it is gone. The undo's result shows after it.
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
        onResultShown = viewModel::resultShown,
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
    @StringRes result: Int? = null,
    onResultShown: () -> Unit = {},
) {
    val hostState = remember { SnackbarHostState() }
    val message = imported?.let { importedMessage(it) }
    val undoLabel = stringResource(R.string.backup_undo_confirm)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(imported) {
        if (imported == null || message == null) return@LaunchedEffect
        val undone = hostState.showUndo(message, undoLabel, accessibility)
        currentOnDone()
        if (undone) currentOnUndo()
    }
    val resources = LocalResources.current
    val currentOnResultShown by rememberUpdatedState(onResultShown)
    LaunchedEffect(result) {
        val text = result?.let(resources::getString) ?: return@LaunchedEffect
        currentOnResultShown()
        hostState.showSnackbar(text)
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
