package com.umamit.feature.collection.detail

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.TeaArchive
import com.umamit.feature.collection.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * A tea just archived on its detail (TEA-04, D-100), for the snackbar „*Tanabata* archiviert" · *Rückgängig* on
 * the screen below the closed detail. [token] tells two archives of the same tea apart.
 */
data class ArchivedTea(val teaId: String, val teaName: String, val token: Long = System.nanoTime()) {
    companion object {
        /** Keeps the notice across a configuration change and process death. */
        val Saver: Saver<ArchivedTea?, Any> = listSaver(
            save = { notice -> notice?.let { listOf(it.teaId, it.teaName, it.token.toString()) }.orEmpty() },
            restore = { list -> if (list.size == 3) ArchivedTea(list[0], list[1], list[2].toLong()) else null },
        )
    }
}

/** *Rückgängig* of the archive snackbar: [TeaArchive.restore]. */
@HiltViewModel
class ArchiveUndoViewModel @Inject constructor(private val archive: TeaArchive) : ViewModel() {
    fun restore(teaId: String) {
        viewModelScope.launch { archive.restore(teaId) }
    }
}

/**
 * „*Tanabata* archiviert" · *Rückgängig* (03 §5) for [archived], on the screen below the closed detail: about 8 s
 * (as the picker's, D-093 Q21), longer when the accessibility settings ask for more time. *Rückgängig* restores the
 * tea; either way [onDone] clears the notice.
 */
@Composable
fun ArchivedTeaSnackbar(
    archived: ArchivedTea?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchiveUndoViewModel = hiltViewModel(),
) {
    ArchivedTeaSnackbar(archived, onUndo = viewModel::restore, onDone = onDone, modifier = modifier)
}

/** The stateless snackbar: [onUndo] gets the tea's id. */
@Composable
fun ArchivedTeaSnackbar(
    archived: ArchivedTea?,
    onUndo: (teaId: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostState = remember { SnackbarHostState() }
    val message = archived?.let { stringResource(R.string.collection_tea_archived, it.teaName) }
    val undo = stringResource(R.string.collection_tea_undo)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(archived) {
        if (archived == null || message == null) return@LaunchedEffect
        val timeoutMs = accessibility?.calculateRecommendedTimeoutMillis(
            UNDO_TIMEOUT_MS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_TIMEOUT_MS
        val result = withTimeoutOrNull(timeoutMs) {
            hostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) currentOnUndo(archived.teaId)
        currentOnDone()
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}

/** About 8 s for *Rückgängig* (D-093 Q21). */
private const val UNDO_TIMEOUT_MS = 8_000L
