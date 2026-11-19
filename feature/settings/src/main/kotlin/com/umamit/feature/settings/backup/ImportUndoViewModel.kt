package com.umamit.feature.settings.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the undo from Start's snackbar ended: [messageRes] is its text, null for none. */
data class UndoResult(@param:StringRes val messageRes: Int?)

/**
 * The snackbar's *Rückgängig machen* on Start (BAK-02 AC6, D-130 item 22): the undo of the import just done, refused
 * like the one on SCR-26 while a timer is active. [running] while it runs: the shell shows the cover „Import wird
 * rückgängig gemacht …" until the result's snackbar (D-133 Q5). [result] is its text, until [resultShown].
 */
@HiltViewModel
class ImportUndoViewModel @Inject constructor(private val backup: DataBackup) : ViewModel() {
    private val mutable = MutableStateFlow<UndoResult?>(null)

    val result: StateFlow<UndoResult?> = mutable.asStateFlow()

    private val mutableRunning = MutableStateFlow(false)

    /** The undo runs; true from the tap until its [result] is there. */
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    fun undo() {
        if (mutableRunning.value) return
        mutableRunning.value = true
        viewModelScope.launch {
            val message = when (val done = backup.undo()) {
                is RestoreResult.Done -> R.string.backup_undone
                is RestoreResult.Refused -> done.refusal.messageRes
                RestoreResult.NoSafetyCopy -> null
            }
            mutable.value = UndoResult(message)
            mutableRunning.value = false
        }
    }

    fun resultShown() {
        mutable.value = null
    }
}
