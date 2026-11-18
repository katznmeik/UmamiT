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
